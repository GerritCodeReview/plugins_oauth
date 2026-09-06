// Copyright (C) 2016 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.cas;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonArray;
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
@OAuthServiceProviderConfig(name = CasOAuthService.PROVIDER_NAME)
public class CasOAuthService extends StandardResourceOAuthService {
  public static final String PROVIDER_NAME = "cas";
  private static final String PROTECTED_RESOURCE_URL = "%s/oauth2.0/profile";
  private static final String USE_JSON_EXTRACTOR = "use-json-extractor";

  private final String rootUrl;
  private final boolean fixLegacyUserId;
  private final String extIdScheme;

  @Inject
  CasOAuthService(OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("Generic CAS OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    fixLegacyUserId = cfg.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false);
    boolean useJsonExtractor = cfg.getBoolean(USE_JSON_EXTRACTOR, false);
    // CAS may omit token_type in the token response; tolerate it so the empty
    // string is stored instead of failing.
    client =
        clientFactory.createClient(
            PROVIDER_NAME, new CasApi(rootUrl, useJsonExtractor), null, true);
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected String resourceUrl() {
    return String.format(PROTECTED_RESOURCE_URL, rootUrl);
  }

  @Override
  protected OAuthUserInfo parseUserInfo(String body) throws IOException {
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (!userJson.isJsonObject()) {
      throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
    }
    JsonObject jsonObject = userJson.getAsJsonObject();

    JsonElement id = jsonObject.get("id");
    if (isNull(id)) {
      throw new IOException(String.format("CAS response missing id: %s", body));
    }

    JsonElement attrListJson = jsonObject.get("attributes");
    if (attrListJson == null) {
      throw new IOException(String.format("CAS response missing attributes: %s", body));
    }

    String email = null, name = null, login = null;
    if (attrListJson.isJsonArray()) {
      // It is possible for CAS to be configured to not return any attributes (email, name,
      // login),
      // in which case,
      // CAS returns an empty JSON object "attributes":{}, rather than "null" or an empty JSON
      // array
      // "attributes": []

      JsonArray attrJson = attrListJson.getAsJsonArray();
      for (JsonElement elem : attrJson) {
        if (elem == null || !elem.isJsonObject()) {
          throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", elem));
        }
        JsonObject obj = elem.getAsJsonObject();

        String property = getStringElement(obj, "email");
        if (property != null) {
          email = property;
        }
        property = getStringElement(obj, "name");
        if (property != null) {
          name = property;
        }
        property = getStringElement(obj, "login");
        if (property != null) {
          login = property;
        }
      }
    }

    return new OAuthUserInfo(
        extIdScheme + ":" + id.getAsString(),
        login,
        email,
        name,
        fixLegacyUserId ? id.getAsString() : null);
  }

  private String getStringElement(JsonObject o, String name) {
    JsonElement elem = o.get(name);
    if (isNull(elem)) {
      return null;
    }

    return elem.getAsString();
  }
}
