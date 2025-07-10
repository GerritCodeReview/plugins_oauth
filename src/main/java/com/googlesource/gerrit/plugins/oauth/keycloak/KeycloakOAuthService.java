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

package com.googlesource.gerrit.plugins.oauth.keycloak;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@OAuthServiceProviderConfig(name = KeycloakOAuthService.PROVIDER_NAME)
public class KeycloakOAuthService implements OAuthServiceProvider {

  private static final Logger log = LoggerFactory.getLogger(KeycloakOAuthService.class);
  public static final String PROVIDER_NAME = "keycloak";

  private final OAuth20Service service;
  private final String serviceName;
  private final boolean usePreferredUsername;
  private final String extIdScheme;
  private final boolean linkExistingGerrit;

  @Inject
  KeycloakOAuthService(
      OAuthPluginConfigFactory cfgFactory, @CanonicalWebUrl Provider<String> urlProvider) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    String canonicalWebUrl = CharMatcher.is('/').trimTrailingFrom(urlProvider.get()) + "/";

    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    String realm = cfg.getString(InitOAuth.REALM);
    serviceName = cfg.getString(InitOAuth.SERVICE_NAME, "Keycloak OAuth2");
    usePreferredUsername = cfg.getBoolean(InitOAuth.USE_PREFERRED_USERNAME, true);
    linkExistingGerrit = cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false);

    service =
        new ServiceBuilder(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .callback(canonicalWebUrl + "oauth")
            .defaultScope("openid")
            .build(new KeycloakApi(rootUrl, realm));
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  private String parseJwt(String input) throws UnsupportedEncodingException {
    String[] parts = input.split("\\.");
    Preconditions.checkState(parts.length == 3);
    Preconditions.checkNotNull(parts[1]);
    return new String(Base64.decodeBase64(parts[1]), StandardCharsets.UTF_8.name());
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    JsonElement tokenJson = JSON.newGson().fromJson(token.getRaw(), JsonElement.class);
    JsonObject tokenObject = tokenJson.getAsJsonObject();
    JsonElement id_token = tokenObject.get("id_token");
    String jwt;
    try {
      jwt = parseJwt(id_token.getAsString());
    } catch (UnsupportedEncodingException e) {
      throw new IOException(
          String.format(
              "%s support is required to interact with JWTs", StandardCharsets.UTF_8.name()),
          e);
    }

    JsonElement claimJson = JSON.newGson().fromJson(jwt, JsonElement.class);

    JsonObject claimObject = claimJson.getAsJsonObject();
    if (log.isDebugEnabled()) {
      log.debug("Claim object: {}", claimObject);
    }
    JsonElement usernameElement = claimObject.get("preferred_username");
    JsonElement emailElement = claimObject.get("email");
    JsonElement nameElement = claimObject.get("name");
    if (isNull(usernameElement)) {
      throw new IOException("Response doesn't contain preferred_username field");
    }
    if (isNull(emailElement)) {
      throw new IOException("Response doesn't contain email field");
    }
    if (isNull(nameElement)) {
      throw new IOException("Response doesn't contain name field");
    }
    String usernameAsString = usernameElement.getAsString();
    String username = null;
    if (usePreferredUsername) {
      username = usernameAsString;
    }
    String externalId = extIdScheme + ":" + usernameAsString;
    String email = emailElement.getAsString();
    String name = nameElement.getAsString();

    return new OAuthUserInfo(
        externalId /*externalId*/,
        username /*username*/,
        email /*email*/,
        name /*displayName*/,
        linkExistingGerrit ? "gerrit:" + usernameElement.getAsString() : null /*claimedIdentity*/);
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
