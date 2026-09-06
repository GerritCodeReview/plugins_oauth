// Copyright (C) 2026 The Android Open Source Project
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
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.jwtPayloadJson;

import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;

/**
 * Base for OpenID Connect providers that read the user information from the {@code id_token}
 * carried in the token response, rather than from a separately fetched resource.
 *
 * <p>Subclasses supply only the mapping of the decoded claims ({@link #parseClaims(JsonObject)}),
 * the service name, and construct the {@link OAuthClient}. Extracting and decoding the {@code
 * id_token} JWT is handled here; token exchange, the authorization redirect and the version live in
 * {@link AbstractOAuthService}.
 */
public abstract class StandardIdTokenOAuthService extends AbstractOAuthService {

  protected StandardIdTokenOAuthService(String name) {
    super(name);
  }

  /** Maps the decoded {@code id_token} claims to Gerrit's user information. */
  protected abstract OAuthUserInfo parseClaims(JsonObject claims) throws IOException;

  @Override
  public final OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    JsonElement tokenJson = JSON.newGson().fromJson(token.getRaw(), JsonElement.class);
    JsonObject tokenObject = tokenJson.getAsJsonObject();
    JsonElement idToken = tokenObject.get("id_token");
    String jwt = jwtPayloadJson(idToken.getAsString());
    JsonElement claimJson = JSON.newGson().fromJson(jwt, JsonElement.class);
    JsonObject claimObject = claimJson.getAsJsonObject();
    if (log.isDebugEnabled()) {
      log.debug("Claim object: {}", claimObject);
    }
    return parseClaims(claimObject);
  }
}
