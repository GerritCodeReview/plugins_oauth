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

package com.googlesource.gerrit.plugins.oauth.dex;

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
import com.googlesource.gerrit.plugins.oauth.StandardIdTokenOAuthService;
import java.io.IOException;
import java.net.URI;

@Singleton
@OAuthServiceProviderConfig(name = DexOAuthService.PROVIDER_NAME)
public class DexOAuthService extends StandardIdTokenOAuthService {
  public static final String PROVIDER_NAME = "dex";
  private final String domain;
  private final String extIdScheme;

  @Inject
  DexOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super(cfgFactory.create(PROVIDER_NAME).getString(InitOAuth.SERVICE_NAME, "Dex OAuth2"));
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    domain = cfg.getString(InitOAuth.DOMAIN, null);
    client =
        clientFactory.createClient(
            PROVIDER_NAME, new DexApi(rootUrl), "openid profile email offline_access");
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected OAuthUserInfo parseClaims(JsonObject claimObject) throws IOException {
    // Dex does not support basic profile currently (2017-09), extracting info
    // from access token claim
    JsonElement emailElement = claimObject.get("email");
    JsonElement nameElement = claimObject.get("name");
    if (isNull(emailElement)) {
      throw new IOException("Response doesn't contain email field");
    }
    if (nameElement == null || nameElement.isJsonNull()) {
      throw new IOException("Response doesn't contain name field");
    }
    String email = emailElement.getAsString();
    String name = nameElement.getAsString();
    String username = email;
    if (domain != null && domain.length() > 0) {
      username = email.replace("@" + domain, "");
    }

    return new OAuthUserInfo(extIdScheme + ":" + email, username, email, name, null);
  }
}
