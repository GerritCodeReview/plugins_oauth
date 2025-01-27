// Copyright (C) 2025 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Base64;

@Singleton
class SAPIasOAuthLoginProvider implements OAuthLoginProvider {
  private static final String USER_NAME_ATTRIBUTE = "sub";

  private final SAPIasOAuthService service;

  @Inject
  SAPIasOAuthLoginProvider(SAPIasOAuthService service) {
    this.service = service;
  }

  @Override
  public OAuthUserInfo login(String username, String token) throws IOException {
    if (username == null || token == null || !isAccessToken(token)) {
      throw new IOException("Authentication error");
    }
    OAuth2AccessToken accessToken = new OAuth2AccessToken(token);
    return service.getUserInfo(accessToken);
  }

  private boolean isAccessToken(String accessToken) {
    try {
      JsonObject jsonWebToken = toJsonWebToken(accessToken);
      return getAttribute(jsonWebToken, USER_NAME_ATTRIBUTE) != null;
    } catch (IOException e) {
      return false;
    }
  }

  private static String getAttribute(JsonObject json, String name) {
    JsonPrimitive prim = getAsJsonPrimitive(json, name);
    return prim != null && prim.isString() ? prim.getAsString() : null;
  }

  private static JsonPrimitive getAsJsonPrimitive(JsonObject json, String name) {
    JsonElement attr = json.get(name);
    if (attr == null || !attr.isJsonPrimitive()) {
      return null;
    }
    return attr.getAsJsonPrimitive();
  }

  public static JsonObject getAsJsonObject(String s) {
    JsonElement json = JsonParser.parseString(s);
    if (!json.isJsonObject()) {
      return new JsonObject();
    }
    return json.getAsJsonObject();
  }

  private JsonObject toJsonWebToken(String accessToken) throws IOException {
    String[] segments = getSegments(accessToken);
    return getAsJsonObject(decodeBase64(segments[1]));
  }

  private String decodeBase64(String s) {
    return new String(Base64.getDecoder().decode(s), UTF_8);
  }

  private String[] getSegments(String accessToken) throws IOException {
    String[] segments = accessToken.split("\\.");
    if (segments.length != 3) {
      throw new IOException("Invalid token: must be of the form 'header.token.signature'");
    }
    return segments;
  }
}
