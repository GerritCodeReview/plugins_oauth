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

package com.googlesource.gerrit.plugins.oauth.bitbucket;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

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
@OAuthServiceProviderConfig(name = BitbucketOAuthService.PROVIDER_NAME)
public class BitbucketOAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "bitbucket";
  private static final String PROTECTED_RESOURCE_URL = "https://bitbucket.org/api/1.0/user/";
  private final boolean fixLegacyUserId;
  private final String extIdScheme;

  @Inject
  BitbucketOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("Bitbucket OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    fixLegacyUserId = cfg.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false);
    client = clientFactory.createClient(PROVIDER_NAME, new BitbucketApi());
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected String resourceUrl() {
    return PROTECTED_RESOURCE_URL;
  }

  @Override
  protected OAuthUserInfo parseUserInfo(String body) throws IOException {
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonObject userObject = jsonObject.getAsJsonObject("user");
      if (isNull(userObject)) {
        throw new IOException("Response doesn't contain 'user' field");
      }
      JsonElement usernameElement = userObject.get("username");
      String username = usernameElement.getAsString();

      JsonElement displayName = jsonObject.get("display_name");
      return new OAuthUserInfo(
          extIdScheme + ":" + username,
          username,
          null,
          displayName == null || displayName.isJsonNull() ? null : displayName.getAsString(),
          fixLegacyUserId ? username : null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }
}
