// Copyright (C) 2023 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.auth0;

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
@OAuthServiceProviderConfig(name = Auth0OAuthService.PROVIDER_NAME)
public class Auth0OAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "auth0";
  private static final String PROTECTED_RESOURCE_URL = "%s/userinfo";
  private final String rootUrl;
  private final String extIdScheme;

  @Inject
  Auth0OAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super(cfgFactory.create(PROVIDER_NAME).getString(InitOAuth.SERVICE_NAME, "Auth0"));
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    client =
        clientFactory.createClient(PROVIDER_NAME, new Auth0Api(rootUrl), "openid profile email");
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected String resourceUrl() {
    return String.format(PROTECTED_RESOURCE_URL, rootUrl);
  }

  @Override
  protected OAuthUserInfo parseUserInfo(String body) throws IOException {
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    JsonObject jsonObject = userJson.getAsJsonObject();
    if (isNull(jsonObject)) {
      throw new IOException("Response doesn't contain 'user' field" + jsonObject);
    }
    JsonElement id = jsonObject.get("sub");
    JsonElement username = jsonObject.get("preferred_username");
    JsonElement email = jsonObject.get("email");
    JsonElement name = jsonObject.get("name");
    return new OAuthUserInfo(
        extIdScheme + ":" + id.getAsString(),
        asString(username),
        asString(email),
        asString(name),
        id.getAsString());
  }
}
