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

package com.googlesource.gerrit.plugins.oauth.facebook;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import com.googlesource.gerrit.plugins.oauth.StandardResourceOAuthService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Singleton
@OAuthServiceProviderConfig(name = FacebookOAuthService.PROVIDER_NAME)
public class FacebookOAuthService extends StandardResourceOAuthService {
  private static final String PROTECTED_RESOURCE_URL = "https://graph.facebook.com/me";
  public static final String PROVIDER_NAME = "facebook";
  private static final String SCOPE = "email";
  private static final String FIELDS_QUERY = "fields";
  private static final String FIELDS = "email,name";
  private final String extIdScheme;

  @Inject
  FacebookOAuthService(OAuth20ServiceFactory clientFactory) {
    super("Facebook OAuth2");
    client = clientFactory.createClient(PROVIDER_NAME, new Facebook2Api(), SCOPE);
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  protected String resourceUrl() {
    // Encode the fields value exactly as ScribeJava's query parameter did (comma -> %2C).
    return PROTECTED_RESOURCE_URL
        + "?"
        + FIELDS_QUERY
        + "="
        + URLEncoder.encode(FIELDS, StandardCharsets.UTF_8);
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
      // Heads up!
      // Lets keep `login` equal to `email`, since `username` field is
      // deprecated for Facebook API versions v2.0 and higher
      JsonElement login = jsonObject.get("email");

      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          asString(login),
          asString(email),
          asString(name),
          null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }
}
