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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthUserInfoWithGroups;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KeycloakOAuthLoginProviderTest {

  // Three dot-separated segments — looks like a JWT to isJwt()
  private static final String VALID_JWT = "header.payload.signature";
  // Plain password — not a JWT (no dots)
  private static final String PLAIN_PASSWORD = "mypassword";

  private static final String USERNAME = "jdoe";
  private static final String EMAIL = "jdoe@example.com";
  private static final String DISPLAY_NAME = "Jane Doe";
  private static final String EXTERNAL_ID = "keycloak-oauth:" + USERNAME;

  @Mock private KeycloakOAuthService mockService;
  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private ExternalIds mockExternalIds;
  @Mock private ExternalIdKeyFactory mockExternalIdKeyFactory;

  private OAuthUserInfo validUserInfo;

  @Before
  public void setUp() {
    when(mockConfigFactory.create(KeycloakOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);
    when(mockPluginConfig.getBoolean("enable-resource-owner-password-credentials", false))
        .thenReturn(false);
    validUserInfo =
        new OAuthUserInfoWithGroups(EXTERNAL_ID, USERNAME, EMAIL, DISPLAY_NAME, null, Set.of());
  }

  private KeycloakOAuthLoginProvider createProvider() {
    return new KeycloakOAuthLoginProvider(
        mockService, mockConfigFactory, mockExternalIds, mockExternalIdKeyFactory);
  }

  // --- Access token (JWT) path ---

  @Test
  public void login_withValidAccessToken_succeeds() throws Exception {
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(validUserInfo);

    OAuthUserInfo result = createProvider().login(USERNAME, VALID_JWT);

    assertThat(result.getUserName()).isEqualTo(USERNAME);
    assertThat(result.getEmailAddress()).isEqualTo(EMAIL);
    assertThat(result.getExternalId()).isEqualTo(EXTERNAL_ID);
    assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
  }

  @Test
  public void login_withValidAccessToken_withoutUsername_succeeds() throws Exception {
    // Username is optional when an access token is provided directly
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(validUserInfo);

    OAuthUserInfo result = createProvider().login(null, VALID_JWT);

    assertThat(result.getUserName()).isEqualTo(USERNAME);
  }

  @Test(expected = IOException.class)
  public void login_withExpiredAccessToken_throwsIOException() throws Exception {
    when(mockService.getUserInfoFromBearerToken(VALID_JWT))
        .thenThrow(new IOException("Token validation failed: HTTP 401"));

    createProvider().login(USERNAME, VALID_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withMismatchedUsername_throwsIOException() throws Exception {
    // Token belongs to "jdoe" but caller claims to be "other-user"
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(validUserInfo);

    createProvider().login("other-user", VALID_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withNonJwtSecret_ropcDisabled_throwsIOException() throws Exception {
    createProvider().login(USERNAME, PLAIN_PASSWORD);
  }

  @Test(expected = IOException.class)
  public void login_withNullSecret_throwsIOException() throws Exception {
    createProvider().login(USERNAME, null);
  }

  // --- ROPC (Resource Owner Password Credentials) path ---

  @Test(expected = IOException.class)
  public void login_ropcEnabled_userNotFound_throwsIOException() throws Exception {
    when(mockPluginConfig.getBoolean("enable-resource-owner-password-credentials", false))
        .thenReturn(true);
    when(mockExternalIds.get(any())).thenReturn(Optional.empty());

    createProvider().login(USERNAME, PLAIN_PASSWORD);
  }

  @Test(expected = IOException.class)
  public void login_ropcEnabled_noKeycloakExtId_throwsIOException() throws Exception {
    when(mockPluginConfig.getBoolean("enable-resource-owner-password-credentials", false))
        .thenReturn(true);

    Account.Id accountId = Account.id(1001);
    ExternalId usernameExtId = mock(ExternalId.class);
    when(usernameExtId.accountId()).thenReturn(accountId);
    when(mockExternalIds.get(any())).thenReturn(Optional.of(usernameExtId));
    // Account exists but has no Keycloak-scheme external ID
    when(mockExternalIds.byAccount(accountId)).thenReturn(ImmutableSet.of());

    createProvider().login(USERNAME, PLAIN_PASSWORD);
  }
}
