// Copyright (C) 2015 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.google;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.jwtPayloadJson;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.AbstractOAuthService;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Singleton
@OAuthServiceProviderConfig(name = GoogleOAuthService.PROVIDER_NAME)
public class GoogleOAuthService extends AbstractOAuthService {
  public static final String PROVIDER_NAME = "google";
  private static final String PROTECTED_RESOURCE_URL =
      "https://www.googleapis.com/oauth2/v2/userinfo";
  private static final String SCOPE = "email profile";
  private final List<String> domains;
  private final boolean useEmailAsUsername;
  private final boolean fixLegacyUserId;
  private final String extIdScheme;

  @Inject
  GoogleOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("Google OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    if (cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_OPENID_ACCOUNT, false)) {
      log.warn(
          String.format(
              "The support for: %s is disconinued", InitOAuth.LINK_TO_EXISTING_OPENID_ACCOUNT));
    }
    fixLegacyUserId = cfg.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false);
    this.domains = Arrays.asList(cfg.getStringList(InitOAuth.DOMAIN));
    this.useEmailAsUsername = cfg.getBoolean(InitOAuth.USE_EMAIL_AS_USERNAME, false);
    this.client = clientFactory.createClient(PROVIDER_NAME, new Google2Api(), SCOPE);

    if (log.isDebugEnabled()) {
      log.debug("OAuth2: scope={}", SCOPE);
      log.debug("OAuth2: domains={}", domains);
      log.debug("OAuth2: useEmailAsUsername={}", useEmailAsUsername);
    }
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    String body = client.get(URI.create(PROTECTED_RESOURCE_URL), token);
    if (log.isDebugEnabled()) {
      log.debug("User info response: {}", body);
    }
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonElement id = jsonObject.get("id");
      if (isNull(id)) {
        throw new IOException("Response doesn't contain id field");
      }
      JsonElement email = jsonObject.get("email");
      JsonElement name = jsonObject.get("name");
      String login = null;

      if (!domains.isEmpty()) {
        boolean domainMatched = false;
        JsonObject jwtToken = retrieveJWTToken(token);
        String hdClaim = retrieveHostedDomain(jwtToken);
        for (String domain : domains) {
          if (domain.equalsIgnoreCase(hdClaim)) {
            domainMatched = true;
            break;
          }
        }
        if (!domainMatched) {
          // TODO(davido): improve error reporting in OAuth extension point
          log.error("Error: hosted domain validation failed: {}", Strings.nullToEmpty(hdClaim));
          return null;
        }
      }
      if (useEmailAsUsername && !email.isJsonNull()) {
        login = email.getAsString().split("@")[0];
      }
      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          login,
          asString(email),
          asString(name),
          fixLegacyUserId ? id.getAsString() : null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }

  private JsonObject retrieveJWTToken(OAuthToken token) throws IOException {
    JsonElement idToken = JSON.newGson().fromJson(token.getRaw(), JsonElement.class);
    if (idToken != null && idToken.isJsonObject()) {
      JsonObject idTokenObj = idToken.getAsJsonObject();
      JsonElement idTokenElement = idTokenObj.get("id_token");
      if (idTokenElement != null && !idTokenElement.isJsonNull()) {
        String payload = jwtPayloadJson(idTokenElement.getAsString());
        if (!Strings.isNullOrEmpty(payload)) {
          JsonElement tokenJsonElement = JSON.newGson().fromJson(payload, JsonElement.class);
          if (tokenJsonElement.isJsonObject()) {
            return tokenJsonElement.getAsJsonObject();
          }
        }
      }
    }
    return null;
  }

  private String retrieveHostedDomain(JsonObject jwtToken) {
    if (jwtToken == null) {
      log.debug("OAuth2: JWT token is null");
      return null;
    }
    JsonElement hdClaim = jwtToken.get("hd");
    if (!isNull(hdClaim)) {
      String hd = hdClaim.getAsString();
      log.debug("OAuth2: hd={}", hd);
      return hd;
    }
    log.debug("OAuth2: JWT doesn't contain hd element");
    return null;
  }

  @Override
  public OAuthAuthorizationInfo getAuthorizationInfo() {
    OAuthAuthorizationInfo info = client.getAuthorizationInfo();
    StringBuilder urlBuilder = new StringBuilder(info.getAuthorizationUrl());
    if (domains.size() == 1) {
      urlBuilder.append("&hd=");
      urlBuilder.append(URLEncoder.encode(domains.get(0), StandardCharsets.UTF_8));
    } else if (domains.size() > 1) {
      urlBuilder.append("&hd=*");
    }
    if (log.isDebugEnabled()) {
      log.debug("OAuth2: authorization URL={}", urlBuilder);
    }
    return new OAuthAuthorizationInfo(urlBuilder.toString(), info.getPkceVerifier());
  }
}
