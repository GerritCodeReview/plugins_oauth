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

package com.googlesource.gerrit.plugins.oauth.keycloak;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.common.base.Splitter;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.util.Optional;

@Singleton
@OAuthServiceProviderConfig(name = KeycloakOAuthService.PROVIDER_NAME)
public class KeycloakOAuthLoginProvider implements OAuthLoginProvider {

  private final KeycloakOAuthService service;
  private final boolean enableResourceOwnerPasswordCredentials;
  private final ExternalIds externalIds;
  private final ExternalIdKeyFactory externalIdKeyFactory;
  private final String extIdScheme;

  @Inject
  KeycloakOAuthLoginProvider(
      KeycloakOAuthService service,
      OAuthPluginConfigFactory cfgFactory,
      ExternalIds externalIds,
      ExternalIdKeyFactory externalIdKeyFactory) {
    this.service = service;
    PluginConfig cfg = cfgFactory.create(KeycloakOAuthService.PROVIDER_NAME);
    this.enableResourceOwnerPasswordCredentials =
        cfg.getBoolean("enable-resource-owner-password-credentials", false);
    this.externalIds = externalIds;
    this.externalIdKeyFactory = externalIdKeyFactory;
    this.extIdScheme =
        OAuthServiceProviderExternalIdScheme.create(KeycloakOAuthService.PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo login(String username, String secret) throws IOException {
    if (secret == null) {
      throw new IOException("Authentication error");
    }
    OAuthUserInfo userInfo;
    if (isJwt(secret)) {
      userInfo = service.getUserInfoFromBearerToken(secret);
    } else if (enableResourceOwnerPasswordCredentials) {
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
              .filter(e -> e.key().isScheme(extIdScheme))
              .findAny()
              .orElseThrow(() -> new IOException("Authentication error"));
      OAuth2AccessToken accessToken = service.getAccessToken(extId.email(), secret);
      userInfo = service.getUserInfoFromBearerToken(accessToken.getAccessToken());
    } else {
      throw new IOException("Authentication error");
    }
    // A username does not have to be provided, but if it is, it should match
    // the username provided by the IDP to prevent confusion. The username is
    // not taken into account in the later authentication, only the provided
    // external ID is.
    if (username != null && !username.equals(userInfo.getUserName())) {
      throw new IOException("Authentication error: username does not match");
    }
    return userInfo;
  }

  private boolean isJwt(String s) {
    return Splitter.on('.').splitToList(s).size() == 3;
  }
}
