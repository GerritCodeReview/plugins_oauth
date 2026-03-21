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
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.jwtPayloadJson;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import com.googlesource.gerrit.plugins.oauth.OAuthUserInfoWithGroups;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
  private final String userInfoEndpoint;

  private final KeycloakGroupCache keycloakGroupCache;

  @Inject
  KeycloakOAuthService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory oauth20ServiceFactory, KeycloakGroupCache groupCache) {
    keycloakGroupCache = groupCache;

    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);

    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    String realm = cfg.getString(InitOAuth.REALM);
    serviceName = cfg.getString(InitOAuth.SERVICE_NAME, "Keycloak OAuth2");
    usePreferredUsername = cfg.getBoolean(InitOAuth.USE_PREFERRED_USERNAME, true);

    KeycloakApi keycloakApi = new KeycloakApi(rootUrl, realm);
    service = oauth20ServiceFactory.create(PROVIDER_NAME, keycloakApi, "openid");
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
    userInfoEndpoint = keycloakApi.getUserInfoEndpoint();
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    JsonElement tokenJson = JSON.newGson().fromJson(token.getRaw(), JsonElement.class);
    return getUserInfo(tokenJson);
  }

  public OAuthUserInfo getUserInfo(OAuth2AccessToken token) throws IOException {
    JsonElement tokenJson = JSON.newGson().fromJson(token.getRawResponse(), JsonElement.class);
    return getUserInfo(tokenJson);
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

  public OAuth2AccessToken getAccessToken(String email, String password) {
    try {
	    return service.getAccessTokenPasswordGrant(email, password);
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

  public String getExternalIdScheme() {
    return extIdScheme;
  }

  public OAuthUserInfo getUserInfoFromBearerToken(String bearerToken) throws IOException {
    OAuthRequest request = new OAuthRequest(Verb.GET, userInfoEndpoint);
    service.signRequest(new OAuth2AccessToken(bearerToken), request);
    Response response;
    try {
      response = service.execute(request);
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException("Failed to validate token via Keycloak userinfo endpoint", e);
    }
    if (response.getCode() != 200) {
      throw new IOException("Token validation failed: HTTP " + response.getCode());
    }
    JsonObject userInfoObject = JSON.newGson().fromJson(response.getBody(), JsonObject.class);
    return parseUserClaims(userInfoObject);
  }

  private OAuthUserInfo getUserInfo(JsonElement tokenJson) throws IOException {
    JsonObject tokenObject = tokenJson.getAsJsonObject();
    JsonElement id_token = tokenObject.get("id_token");
    String jwt = jwtPayloadJson(id_token.getAsString());
    JsonObject claimObject = JSON.newGson().fromJson(jwt, JsonElement.class).getAsJsonObject();
    return parseUserClaims(claimObject);
  }

  private OAuthUserInfo parseUserClaims(JsonObject claimObject) throws IOException {
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
    String username = usePreferredUsername ? usernameAsString : null;
    String externalId = extIdScheme + ":" + usernameAsString;
    String email = emailElement.getAsString();
    String name = nameElement.getAsString();

    Set<String> groups = new HashSet<>();
    JsonElement groupsElement = claimObject.get("groups");
    if (groupsElement != null && groupsElement.isJsonArray()) {
      groupsElement.getAsJsonArray().forEach(element -> groups.add(element.getAsString()));
    } else {
      log.warn("No groups claim found in response for user {}", usernameAsString);
    }
    keycloakGroupCache.put(externalId, groups);
    if (log.isDebugEnabled()) {
      log.debug("User {} has groups {}", usernameAsString, groups);
    }

    return new OAuthUserInfoWithGroups(externalId, username, email, name, null, groups);
  }

}
