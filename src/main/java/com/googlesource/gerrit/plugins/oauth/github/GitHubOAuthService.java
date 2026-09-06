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

package com.googlesource.gerrit.plugins.oauth.github;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.google.common.base.CharMatcher;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import com.googlesource.gerrit.plugins.oauth.StandardResourceOAuthService;
import java.io.IOException;

@Singleton
@OAuthServiceProviderConfig(name = GitHubOAuthService.PROVIDER_NAME)
public class GitHubOAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "github";
  private static final String GITHUB_API_ENDPOINT_URL = "https://api.github.com/";
  private static final String GHE_API_ENDPOINT_URL = "%sapi/v3/";
  static final String GITHUB_ROOT_URL = "https://github.com/";
  static final String SCOPE = "user:email";
  private final String rootUrl;
  private final boolean fixLegacyUserId;
  private final String extIdScheme;

  @Inject
  GitHubOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("GitHub OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    fixLegacyUserId = cfg.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false);
    rootUrl =
        CharMatcher.is('/').trimTrailingFrom(cfg.getString(InitOAuth.ROOT_URL, GITHUB_ROOT_URL))
            + "/";
    client = clientFactory.createClient(PROVIDER_NAME, new GitHub2Api(rootUrl), SCOPE);
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  private String getApiUrl() {
    return GITHUB_ROOT_URL.equals(rootUrl)
        ? GITHUB_API_ENDPOINT_URL
        : String.format(GHE_API_ENDPOINT_URL, rootUrl);
  }

  @Override
  protected String resourceUrl() {
    return getApiUrl() + "user";
  }

  @Override
  protected OAuthUserInfo parseUserInfo(String body) throws IOException {
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonElement id = jsonObject.get("id");
      if (isNull(id)) {
        throw new IOException("Response doesn't contain id field");
      }
      JsonElement email = jsonObject.get("email");
      JsonElement name = jsonObject.get("name");
      JsonElement login = jsonObject.get("login");
      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          asString(login),
          asString(email),
          asString(name),
          fixLegacyUserId ? id.getAsString() : null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }
}
