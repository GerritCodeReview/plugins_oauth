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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@OAuthServiceProviderConfig(name = KeycloakOAuthService.PROVIDER_NAME)
public class KeycloakOAuthLoginProvider implements OAuthLoginProvider {
  private static final Logger log = LoggerFactory.getLogger(KeycloakOAuthLoginProvider.class);

  private final KeycloakOAuthService service;
  // Resource Owner Password Credentials (ROPC) support.
  //
  // The ROPC grant is explicitly discouraged by the OAuth 2.0 specification and MUST NOT be
  // used when other flows are available. Keycloak has ongoing discussion around deprecating or
  // removing ROPC support entirely. Enable only when direct password exchange is strictly
  // required and no alternative flow is feasible.
  //
  // See: https://tools.ietf.org/html/rfc6749#section-10.7
  // See: https://github.com/keycloak/keycloak/discussions/9598
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
    this.enableResourceOwnerPasswordCredentials =
        cfgFactory
            .create(KeycloakOAuthService.PROVIDER_NAME)
            .getBoolean("enable-resource-owner-password-credentials", false);
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
      // Validate the access token via the Keycloak userinfo endpoint. This performs
      // full server-side validation rather than relying on local JWT parsing.
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
              .filter(e -> e.key().isScheme(this.extIdScheme))
              .findAny()
              .orElseThrow(() -> new IOException("Authentication error"));
      OAuth2AccessToken accessToken = service.getAccessToken(extId.email(), secret);
      userInfo = service.getUserInfoFromBearerToken(accessToken.getAccessToken());
    } else {
      throw new IOException("Authentication error");
    }

    // Username is optional, but if provided it must match the identity returned by Keycloak
    // to prevent account confusion. The actual authentication decision is based on the
    // external ID returned by the userinfo endpoint, not the provided username.
    if (username != null && !username.equals(userInfo.getUserName())) {
      throw new IOException("Authentication error: username does not match");
    }
    return userInfo;
  }

  /** Returns true if {@code s} looks like a JWT (three base64url segments separated by dots). */
  private boolean isJwt(String s) {
    return Splitter.on('.').splitToList(s).size() == 3;
  }
}
