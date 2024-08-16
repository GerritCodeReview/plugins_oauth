// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.oauth;

import static com.google.gerrit.json.OutputFormat.JSON;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.CharMatcher;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DexOAuthService implements OAuthServiceProvider {
  private static final Logger log = LoggerFactory.getLogger(DexOAuthService.class);

  static final String CONFIG_SUFFIX = "-dex-oauth";
  private static final String DEX_PROVIDER_PREFIX = "dex-oauth:";
  private final String jwksUrl;
  private final int JwksCacheTimeoutHours;
  private final int JwksCacheRefillRateMinutes;
  private final int JwksCacheSize;
  private final OAuth20Service service;
  private final String rootUrl;
  private final String domain;
  private final String serviceName;
  private final boolean useDexEndpointPrefix;
  private final boolean linkExistingGerrit;

  @Inject
  DexOAuthService(
      PluginConfigFactory cfgFactory,
      @PluginName String pluginName,
      @CanonicalWebUrl Provider<String> urlProvider) {
    PluginConfig cfg = cfgFactory.getFromGerritConfig(pluginName + CONFIG_SUFFIX);
    String canonicalWebUrl = CharMatcher.is('/').trimTrailingFrom(urlProvider.get()) + "/";

    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    jwksUrl = cfg.getString(InitOAuth.JWKS_URL, "");
    if (!jwksUrl.isEmpty()) {
      if (!URI.create(jwksUrl).isAbsolute()) {
        throw new ProvisionException("JWKS URL must be absolute URL");
      }
    }
    domain = cfg.getString(InitOAuth.DOMAIN, null);
    serviceName = cfg.getString(InitOAuth.SERVICE_NAME, "Dex OAuth2");
    linkExistingGerrit = cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false);
    useDexEndpointPrefix = cfg.getBoolean(InitOAuth.USE_DEX_ENDPOINT_PREFIX, true);
    JwksCacheTimeoutHours = cfg.getInt(InitOAuth.JWKS_CACHE_TIMEOUT_HOURS, 1);
    JwksCacheSize = cfg.getInt(InitOAuth.JWKS_CACHE_SIZE, 10);
    JwksCacheRefillRateMinutes = cfg.getInt(InitOAuth.JWKS_CACHE_REFILL_RATE_MINUTES, 1);

    service =
        new ServiceBuilder(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .defaultScope("openid profile email offline_access")
            .callback(canonicalWebUrl + "oauth")
            .build(new DexApi(rootUrl, useDexEndpointPrefix));
  }

  private DecodedJWT parseJwt(String input) throws JWTVerificationException {
    try {
      if (jwksUrl.isEmpty()) {
        return JWT.decode(input);
      }
      URI jwksUri = URI.create(jwksUrl);
      JwkProvider provider =
          new JwkProviderBuilder(jwksUri.toURL())
              .cached(JwksCacheSize, JwksCacheTimeoutHours, TimeUnit.HOURS)
              .rateLimited(JwksCacheSize, JwksCacheRefillRateMinutes, TimeUnit.MINUTES)
              .build();
      Algorithm algorithm = getAlgorithm(provider);
      return JWT.require(algorithm).build().verify(input);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private Algorithm getAlgorithm(JwkProvider provider) {
    RSAKeyProvider keyProvider =
        new RSAKeyProvider() {
          @Override
          public RSAPublicKey getPublicKeyById(String kid) {
            try {
              return (RSAPublicKey) provider.get(kid).getPublicKey();
            } catch (Exception e) {
              throw new RuntimeException("Failed to retrieve public key", e);
            }
          }

          @Override
          public RSAPrivateKey getPrivateKey() {
            return null;
          }

          @Override
          public String getPrivateKeyId() {
            return null;
          }
        };
    Algorithm algorithm = Algorithm.RSA256(keyProvider);
    return algorithm;
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    try {
      JsonElement tokenJson = JSON.newGson().fromJson(token.getRaw(), JsonElement.class);
      JsonObject tokenObject = tokenJson.getAsJsonObject();
      if (!tokenObject.has("id_token")) {
        throw new IOException("No entry id_token in json");
      }

      JsonElement id_token = tokenObject.get("id_token");
      System.out.println("TOKEN: " + token.getToken());
      System.out.println("TOKEN ID: " + id_token.getAsString());

      DecodedJWT jwt = parseJwt(id_token.getAsString());
      String email = jwt.getClaim("email").asString();
      String name = jwt.getClaim("name").asString();
      String username = email;
      if (domain != null && !domain.isEmpty()) {
        username = email.replace("@" + domain, "");
      }
      return new OAuthUserInfo(
          DEX_PROVIDER_PREFIX + email,
          username,
          email,
          name,
          linkExistingGerrit ? "gerrit:" + username : null /*claimedIdentity*/);
    } catch (JWTVerificationException | JsonSyntaxException | IllegalStateException e) {
      throw new IOException("Unable to parse JWT or JWT has expired");
    }
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier rv) {
    try {
      OAuth2AccessToken accessToken = service.getAccessToken(rv.getValue());
      return new OAuthToken(
          accessToken.getAccessToken(), accessToken.getTokenType(), accessToken.getRawResponse());
    } catch (InterruptedException | ExecutionException | IOException e) {
      String msg = "Cannot retrieve access token";
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public String getAuthorizationUrl() {
    return service.getAuthorizationUrl();
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }

  @Override
  public String getName() {
    return serviceName;
  }
}
