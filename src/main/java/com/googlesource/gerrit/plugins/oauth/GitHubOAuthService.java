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

package com.googlesource.gerrit.plugins.oauth;

import static com.google.gerrit.json.OutputFormat.JSON;

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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class GitHubOAuthService implements OAuthServiceProvider {
  private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);
  static final String CONFIG_SUFFIX = "-github-oauth";
  private static final String GITHUB_PROVIDER_PREFIX = "github-oauth:";
  private static final String PROTECTED_RESOURCE_URL = "%sapi/v3/user";
  static final String GITHUB_ROOT_URL = "https://github.com/";
  private final String rootUrl;

  private static final String SCOPE = "user:email";
  private final boolean fixLegacyUserId;
  private final OAuthService service;

  @Inject
  GitHubOAuthService(
      PluginConfigFactory cfgFactory,
      @PluginName String pluginName,
      @CanonicalWebUrl Provider<String> urlProvider) {
    PluginConfig cfg = cfgFactory.getFromGerritConfig(pluginName + CONFIG_SUFFIX);
    String canonicalWebUrl = CharMatcher.is('/').trimTrailingFrom(urlProvider.get()) + "/";
    fixLegacyUserId = cfg.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false);
    String _rootUrl = cfg.getString(InitOAuth.ROOT_URL, GITHUB_ROOT_URL);

    if (!_rootUrl.endsWith("/")) {
      rootUrl = _rootUrl + "/";
    } else {
      rootUrl = _rootUrl;
    }

    service =
        new ServiceBuilder()
            .provider(new GitHub2Api(rootUrl))
            .apiKey(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .callback(canonicalWebUrl + "oauth")
            .scope(SCOPE)
            .build();
  }

  private String getProtectedResourceUrl() {
    return GITHUB_ROOT_URL.equals(rootUrl)
        ? "https://api.github.com/user/"
        : String.format(PROTECTED_RESOURCE_URL, rootUrl);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuthRequest request = new OAuthRequest(Verb.GET, getProtectedResourceUrl());
    Token t = new Token(token.getToken(), token.getSecret(), token.getRaw());
    service.signRequest(t, request);
    Response response = request.send();
    if (response.getCode() != HttpServletResponse.SC_OK) {
      throw new IOException(
          String.format(
              "Status %s (%s) for request %s",
              response.getCode(), response.getBody(), request.getUrl()));
    }
    JsonElement userJson = JSON.newGson().fromJson(response.getBody(), JsonElement.class);
    if (log.isDebugEnabled()) {
      log.debug("User info response: {}", response.getBody());
    }
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonElement id = jsonObject.get("id");
      if (id == null || id.isJsonNull()) {
        throw new IOException("Response doesn't contain id field");
      }
      JsonElement email = jsonObject.get("email");
      JsonElement name = jsonObject.get("name");
      JsonElement login = jsonObject.get("login");
      return new OAuthUserInfo(
          GITHUB_PROVIDER_PREFIX + id.getAsString(),
          login == null || login.isJsonNull() ? null : login.getAsString(),
          email == null || email.isJsonNull() ? null : email.getAsString(),
          name == null || name.isJsonNull() ? null : name.getAsString(),
          fixLegacyUserId ? id.getAsString() : null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier rv) {
    Verifier vi = new Verifier(rv.getValue());
    Token to = service.getAccessToken(null, vi);
    OAuthToken result = new OAuthToken(to.getToken(), to.getSecret(), to.getRawResponse());
    return result;
  }

  @Override
  public String getAuthorizationUrl() {
    return service.getAuthorizationUrl(null);
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }

  @Override
  public String getName() {
    return "GitHub OAuth2";
  }
}
