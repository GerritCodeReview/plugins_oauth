// Copyright (C) 2024 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.cognito;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.utils.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.utils.JsonUtil.isNull;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.base.OAuthConfigKeys;
import com.googlesource.gerrit.plugins.oauth.base.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.base.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.base.OAuthServiceProviderExternalIdScheme;
import com.googlesource.gerrit.plugins.oauth.base.StandardResourceOAuthService;
import com.googlesource.gerrit.plugins.oauth.utils.OAuthUrls;
import java.io.IOException;
import java.net.URI;

@Singleton
@OAuthServiceProviderConfig(name = CognitoOAuthService.PROVIDER_NAME)
public class CognitoOAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "cognito";
  private static final String PROTECTED_RESOURCE_URL = "%s/oauth2/userInfo";
  private final String rootUrl;
  private final boolean linkExistingGerrit;
  private final String extIdScheme;

  @Inject
  CognitoOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super(cfgFactory.create(PROVIDER_NAME).getString(OAuthConfigKeys.SERVICE_NAME, "Cognito"));
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    rootUrl = OAuthUrls.trimTrailingSlashes(cfg.getString(OAuthConfigKeys.ROOT_URL));
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    linkExistingGerrit = cfg.getBoolean(OAuthConfigKeys.LINK_TO_EXISTING_GERRIT_ACCOUNT, false);
    boolean enablePkce = cfg.getBoolean(OAuthConfigKeys.ENABLE_PKCE, false);
    client =
        clientFactory.createClient(
            PROVIDER_NAME, new CognitoApi(rootUrl), "openid profile email", false, enablePkce);
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
    log.warn(
        "The Cognito OAuth provider is soft-deprecated; prefer the generic Discovery provider."
            + " See config-discovery.md for the migration recipe. The wrapper still works.");
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
        linkExistingGerrit ? "gerrit:" + username.getAsString() : null);
  }
}
