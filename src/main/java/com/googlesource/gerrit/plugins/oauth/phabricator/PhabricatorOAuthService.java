// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.phabricator;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
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
import com.googlesource.gerrit.plugins.oauth.StandardResourceOAuthService;
import java.io.IOException;
import java.net.URI;

@Singleton
@OAuthServiceProviderConfig(name = PhabricatorOAuthService.PROVIDER_NAME)
public class PhabricatorOAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "phabricator";
  private static final String PROTECTED_RESOURCE_URL = "%s/api/user.whoami";
  private final String rootUrl;
  private final String extIdScheme;

  @Inject
  PhabricatorOAuthService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("Phabricator OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    client = clientFactory.createClient(PROVIDER_NAME, new PhabricatorApi(rootUrl));
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected String resourceUrl() {
    return String.format(PROTECTED_RESOURCE_URL, rootUrl);
  }

  @Override
  protected OAuthUserInfo parseUserInfo(String body) throws IOException {
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonElement jsonResult = jsonObject.get("result");
      if (jsonResult == null) {
        throw new IOException("Response doesn't contain result field");
      }
      JsonObject resultObject = jsonResult.getAsJsonObject();
      JsonElement id = resultObject.get("phid");
      if (isNull(id)) {
        throw new IOException("Response doesn't contain id field");
      }
      JsonElement email = resultObject.get("primaryEmail");
      JsonElement name = resultObject.get("realName");
      JsonElement username = resultObject.get("userName");
      String login = null;

      if (!username.isJsonNull()) {
        login = username.getAsString();
      }
      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(), login, asString(email), asString(name), null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }
}
