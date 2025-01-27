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

package com.googlesource.gerrit.plugins.oauth.sap;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.common.base.Splitter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
@OAuthServiceProviderConfig(name = SAPIasOAuthService.PROVIDER_NAME)
public class SAPIasOAuthLoginProvider implements OAuthLoginProvider {
  private static final String USER_NAME_ATTRIBUTE = "sub";

  private final SAPIasOAuthService service;
  private final boolean enableResourceOwnerPasswordFlow;
  private final ExternalIds externalIds;
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private final String extIdScheme;

  @Inject
  SAPIasOAuthLoginProvider(
      SAPIasOAuthService service,
      OAuthPluginConfigFactory cfgFactory,
      ExternalIds externalIds,
      ExternalIdKeyFactory externalIdKeyFactory) {
    this.service = service;
    this.enableResourceOwnerPasswordFlow =
        cfgFactory
            .create(SAPIasOAuthService.PROVIDER_NAME)
            .getBoolean("enable-resource-owner-password-flow", false);
    this.externalIds = externalIds;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.extIdScheme =
        OAuthServiceProviderExternalIdScheme.create(SAPIasOAuthService.PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo login(String username, String secret) throws IOException {
    if (secret == null) {
      throw new IOException("Authentication error");
    }
    OAuth2AccessToken accessToken;
    if (isAccessToken(secret)) {
      accessToken = new OAuth2AccessToken(secret);
    } else if (enableResourceOwnerPasswordFlow) {
      if (username == null) {
        throw new IOException("Authentication error");
      }
      Optional<Account.Id> accountId =
          externalIds
              .get(externalIdKeyFactory.create(SCHEME_USERNAME, username))
              .map(ExternalId::accountId);
      if (accountId.isEmpty()) {
        throw new IOException("Authentication error");
      }
      ExternalId extId =
          externalIds.byAccount(accountId.get()).stream()
              .filter(e -> e.key().isScheme(this.extIdScheme))
              .findAny()
              .orElseThrow(() -> new IOException("Authentication error"));
      accessToken = service.getAccessToken(extId.email(), secret);
    } else {
      throw new IOException("Authentication error");
    }
    OAuthUserInfo userInfo = service.getUserInfo(accessToken);
    // A username does not have to be provided, but if it is, it should match
    // the username provided by the IDP to prevent confusion. The username is
    // not taken into account in the later authentication, only the provided
    // external ID is.
    if (username != null && username != userInfo.getUserName()) {
      throw new IOException("Authentication error: username does not match");
    }
    return userInfo;
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
    List<String> segments = getSegments(accessToken);
    return getAsJsonObject(decodeBase64(segments.get(1)));
  }

  private String decodeBase64(String s) {
    return new String(Base64.getDecoder().decode(s), UTF_8);
  }

  private List<String> getSegments(String accessToken) throws IOException {
    List<String> segments = Splitter.on('.').splitToList(accessToken);
    if (segments.size() != 3) {
      throw new IOException("Invalid token: must be of the form 'header.token.signature'");
    }
    return segments;
  }
}
