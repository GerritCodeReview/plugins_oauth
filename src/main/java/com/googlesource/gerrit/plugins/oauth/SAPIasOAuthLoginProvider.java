// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.slf4j.LoggerFactory.getLogger;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sap.cloud.security.client.DefaultHttpClientFactory;
import com.sap.cloud.security.config.ClientCredentials;
import com.sap.cloud.security.config.OAuth2ServiceConfiguration;
import com.sap.cloud.security.config.OAuth2ServiceConfigurationBuilder;
import com.sap.cloud.security.config.Service;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.CombiningValidator;
import com.sap.cloud.security.token.validation.ValidationResult;
import com.sap.cloud.security.token.validation.validators.JwtValidatorBuilder;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;

@Singleton
@OAuthServiceProviderConfig(name = SAPIasOAuthService.PROVIDER_NAME)
class SAPIasOAuthLoginProvider implements OAuthLoginProvider {
  private static final Logger log = getLogger(SAPIasOAuthLoginProvider.class);
  private static final String USER_NAME_ATTRIBUTE = "sub";
  private static final String ONDEMAND_DOMAIN = ".ondemand.com";
  private static final String CLOUD_DOMAIN = ".cloud.sap";

  private final SAPIasOAuthService service;
  private final boolean enableResourceOwnerPasswordFlow;
  private final ExternalIds externalIds;
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private final String extIdScheme;
  private final CombiningValidator<Token> tokenValidator;

  @Inject
  SAPIasOAuthLoginProvider(
      OAuthPluginConfigFactory cfgFactory,
      SAPIasOAuthService service,
      ExternalIds externalIds,
      ExternalIdKeyFactory externalIdKeyFactory) {
    PluginConfig cfg = cfgFactory.create(SAPIasOAuthService.PROVIDER_NAME);
    this.service = service;
    this.enableResourceOwnerPasswordFlow = cfg.getBoolean("enableResourceOwnerPasswordFlow", false);
    this.externalIds = externalIds;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.extIdScheme =
        OAuthServiceProviderExternalIdScheme.create(SAPIasOAuthService.PROVIDER_NAME);

    String[] rootUrlParts = cfg.getString(InitOAuth.ROOT_URL).split("\\.");
    String universeSubdomain = rootUrlParts[rootUrlParts.length - 3];
    OAuth2ServiceConfiguration serviceConfiguration =
        OAuth2ServiceConfigurationBuilder.forService(Service.IAS)
            .withUrl(cfg.getString(InitOAuth.ROOT_URL))
            .withClientId(cfg.getString(InitOAuth.CLIENT_ID))
            .withDomains(universeSubdomain + ONDEMAND_DOMAIN, universeSubdomain + CLOUD_DOMAIN)
            .build();
    tokenValidator =
        JwtValidatorBuilder.getInstance(serviceConfiguration)
            .withHttpClient(
                new DefaultHttpClientFactory()
                    .createClient(
                        new ClientCredentials(
                            cfg.getString(InitOAuth.CLIENT_ID),
                            cfg.getString(InitOAuth.CLIENT_SECRET))))
            .build();
  }

  @Override
  public OAuthUserInfo login(String username, String secret) throws IOException {
    if (secret == null) {
      throw new IOException("Authentication error");
    }
    OAuth2AccessToken accessToken;
    if (isAccessToken(secret)) {
      ValidationResult res = tokenValidator.validate(new SapIdToken(secret));
      if (res.isErroneous()) {
        log.warn("Token validation failed: {}", res.getErrorDescription());
        throw new IOException("Authentication error");
      }
      accessToken = new OAuth2AccessToken(secret);
    } else if (enableResourceOwnerPasswordFlow) {
      if (username == null) {
        throw new IOException("Authentication error");
      }
      Optional<Account.Id> accountId =
          externalIds
              .get(externalIdKeyFactory.create(SCHEME_USERNAME, username))
              .map(ExternalId::accountId);
      if (accountId.isEmpty()) {
        throw new IOException("Authentication error");
      }
      ExternalId extId =
          externalIds.byAccount(accountId.get()).stream()
              .filter(e -> e.key().isScheme(extIdScheme.substring(0, extIdScheme.length())))
              .findAny()
              .orElseThrow(() -> new IOException("Authentication error"));
      accessToken = service.getAccessToken(extId.email(), secret);
    } else {
      throw new IOException("Authentication error");
    }
    return service.getUserInfo(accessToken);
  }

  private boolean isAccessToken(String accessToken) {
    try {
      JsonObject jsonWebToken = toJsonWebToken(accessToken);
      return getAttribute(jsonWebToken, USER_NAME_ATTRIBUTE) != null;
    } catch (IOException e) {
      return false;
    }
  }

  private static String getAttribute(JsonObject json, String name) {
    JsonPrimitive prim = getAsJsonPrimitive(json, name);
    return prim != null && prim.isString() ? prim.getAsString() : null;
  }

  private static JsonPrimitive getAsJsonPrimitive(JsonObject json, String name) {
    JsonElement attr = json.get(name);
    if (attr == null || !attr.isJsonPrimitive()) {
      return null;
    }
    return attr.getAsJsonPrimitive();
  }

  public static JsonObject getAsJsonObject(String s) {
    JsonElement json = JsonParser.parseString(s);
    if (!json.isJsonObject()) {
      return new JsonObject();
    }
    return json.getAsJsonObject();
  }

  private JsonObject toJsonWebToken(String accessToken) throws IOException {
    String[] segments = getSegments(accessToken);
    return getAsJsonObject(decodeBase64(segments[1]));
  }

  private String decodeBase64(String s) {
    return new String(Base64.getDecoder().decode(s), UTF_8);
  }

  private String[] getSegments(String accessToken) throws IOException {
    String[] segments = accessToken.split("\\.");
    if (segments.length != 3) {
      throw new IOException("Invalid token: must be of the form 'header.token.signature'");
    }
    return segments;
  }
}
