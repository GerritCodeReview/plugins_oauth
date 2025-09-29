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

package com.googlesource.gerrit.plugins.oauth.gitlab;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.slf4j.LoggerFactory.getLogger;

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
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;

@Singleton
@OAuthServiceProviderConfig(name = GitLabOAuthService.PROVIDER_NAME)
public class GitLabOAuthService implements OAuthServiceProvider {
  private static final Logger log = getLogger(GitLabOAuthService.class);
  private static final String PROTECTED_RESOURCE_URL = "%s/api/v3/user";
  public static final String PROVIDER_NAME = "gitlab";
  private final OAuth20Service service;
  private final String rootUrl;
  private final String extIdScheme;

  @Inject
  GitLabOAuthService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory oauth20ServiceFactory) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    service = oauth20ServiceFactory.create(PROVIDER_NAME, new GitLabApi(rootUrl));

    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuthRequest request =
        new OAuthRequest(Verb.GET, String.format(PROTECTED_RESOURCE_URL, rootUrl));
    OAuth2AccessToken t = new OAuth2AccessToken(token.getToken(), token.getRaw());
    service.signRequest(t, request);

    try (Response response = service.execute(request)) {
      if (response.getCode() != SC_OK) {
        throw new IOException(
            String.format(
                "Status %s (%s) for request %s",
                response.getCode(), response.getBody(), request.getUrl()));
      }
      JsonElement userJson = JSON.newGson().fromJson(response.getBody(), JsonElement.class);
      if (log.isDebugEnabled()) {
        log.debug("User info response: {}", response.getBody());
      }
      JsonObject jsonObject = userJson.getAsJsonObject();
      if (isNull(jsonObject)) {
        throw new IOException("Response doesn't contain 'user' field" + jsonObject);
      }
      JsonElement id = jsonObject.get("id");
      JsonElement username = jsonObject.get("username");
      JsonElement email = jsonObject.get("email");
      JsonElement name = jsonObject.get("name");
      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          asString(username),
          asString(email),
          asString(name),
          null);
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException("Cannot retrieve user info resource", e);
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
    return "GitLab OAuth2";
  }
}
