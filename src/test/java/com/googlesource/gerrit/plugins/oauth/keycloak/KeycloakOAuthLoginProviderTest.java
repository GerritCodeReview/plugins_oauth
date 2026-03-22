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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import java.io.IOException;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KeycloakOAuthLoginProviderTest {

  private static final String VALID_JWT = "header.payload.signature";
  private static final String NOT_A_JWT = "notajwt";
  private static final String TEST_USERNAME = "testuser";
  private static final String TEST_EMAIL = "testuser@example.com";
  private static final String TEST_NAME = "Test User";
  private static final String TEST_EXT_ID = "keycloak-oauth:testuser";
  private static final String ACCESS_TOKEN = "access.token.value";

  @Mock private KeycloakOAuthService mockService;
  @Mock private OAuthPluginConfigFactory mockCfgFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private ExternalIds mockExternalIds;
  @Mock private ExternalIdKeyFactory mockExternalIdKeyFactory;

  private KeycloakOAuthLoginProvider providerWithRopc(boolean enabled) {
    when(mockCfgFactory.create(KeycloakOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);
    when(mockPluginConfig.getBoolean("enable-resource-owner-password-credentials", false))
        .thenReturn(enabled);
    return new KeycloakOAuthLoginProvider(
        mockService, mockCfgFactory, mockExternalIds, mockExternalIdKeyFactory);
  }

  private KeycloakOAuthLoginProvider provider() {
    return providerWithRopc(false);
  }

  @Test
  public void login_withValidAccessToken_succeeds() throws Exception {
    OAuthUserInfo userInfo =
        new OAuthUserInfo(TEST_EXT_ID, TEST_USERNAME, TEST_EMAIL, TEST_NAME, null);
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(userInfo);

    OAuthUserInfo result = provider().login(TEST_USERNAME, VALID_JWT);

    assertThat(result.getExternalId()).isEqualTo(TEST_EXT_ID);
    assertThat(result.getUserName()).isEqualTo(TEST_USERNAME);
    assertThat(result.getEmailAddress()).isEqualTo(TEST_EMAIL);
    assertThat(result.getDisplayName()).isEqualTo(TEST_NAME);
  }

  @Test
  public void login_withValidAccessToken_noUsername_succeeds() throws Exception {
    OAuthUserInfo userInfo =
        new OAuthUserInfo(TEST_EXT_ID, TEST_USERNAME, TEST_EMAIL, TEST_NAME, null);
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(userInfo);

    OAuthUserInfo result = provider().login(null, VALID_JWT);

    assertThat(result.getExternalId()).isEqualTo(TEST_EXT_ID);
  }

  @Test(expected = IOException.class)
  public void login_withExpiredAccessToken_throwsIOException() throws Exception {
    when(mockService.getUserInfoFromBearerToken(VALID_JWT))
        .thenThrow(new IOException("Token validation failed: HTTP 401"));

    provider().login(TEST_USERNAME, VALID_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withMismatchedUsername_throwsIOException() throws Exception {
    OAuthUserInfo userInfo =
        new OAuthUserInfo(TEST_EXT_ID, "otheruser", TEST_EMAIL, TEST_NAME, null);
    when(mockService.getUserInfoFromBearerToken(VALID_JWT)).thenReturn(userInfo);

    provider().login(TEST_USERNAME, VALID_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withNonJwtSecret_ropcDisabled_throwsIOException() throws Exception {
    provider().login(TEST_USERNAME, NOT_A_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withNullSecret_throwsIOException() throws Exception {
    provider().login(TEST_USERNAME, null);
  }

  @Test
  public void login_withPassword_ropcEnabled_succeeds() throws Exception {
    Account.Id accountId = Account.id(1001);
    ExternalId.Key usernameKey = mock(ExternalId.Key.class);
    ExternalId usernameExtId = mock(ExternalId.class);
    ExternalId keycloakExtId = mock(ExternalId.class);
    ExternalId.Key keycloakKey = mock(ExternalId.Key.class);
    OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
    OAuthUserInfo userInfo =
        new OAuthUserInfo(TEST_EXT_ID, TEST_USERNAME, TEST_EMAIL, TEST_NAME, null);

    when(mockExternalIdKeyFactory.create("username", TEST_USERNAME)).thenReturn(usernameKey);
    when(mockExternalIds.get(usernameKey)).thenReturn(Optional.of(usernameExtId));
    when(usernameExtId.accountId()).thenReturn(accountId);
    when(mockExternalIds.byAccount(accountId))
        .thenReturn(ImmutableSet.of(keycloakExtId));
    when(keycloakExtId.key()).thenReturn(keycloakKey);
    when(keycloakKey.isScheme("keycloak-oauth")).thenReturn(true);
    when(keycloakExtId.email()).thenReturn(TEST_EMAIL);
    when(mockService.getAccessToken(TEST_EMAIL, NOT_A_JWT)).thenReturn(accessToken);
    when(accessToken.getAccessToken()).thenReturn(ACCESS_TOKEN);
    when(mockService.getUserInfoFromBearerToken(ACCESS_TOKEN)).thenReturn(userInfo);

    OAuthUserInfo result = providerWithRopc(true).login(TEST_USERNAME, NOT_A_JWT);

    assertThat(result.getUserName()).isEqualTo(TEST_USERNAME);
  }

  @Test(expected = IOException.class)
  public void login_withPassword_ropcEnabled_userNotFound_throwsIOException() throws Exception {
    ExternalId.Key usernameKey = mock(ExternalId.Key.class);
    when(mockExternalIdKeyFactory.create("username", TEST_USERNAME)).thenReturn(usernameKey);
    when(mockExternalIds.get(usernameKey)).thenReturn(Optional.empty());

    providerWithRopc(true).login(TEST_USERNAME, NOT_A_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withPassword_ropcEnabled_noKeycloakExtId_throwsIOException() throws Exception {
    Account.Id accountId = Account.id(1001);
    ExternalId.Key usernameKey = mock(ExternalId.Key.class);
    ExternalId usernameExtId = mock(ExternalId.class);
    ExternalId otherExtId = mock(ExternalId.class);
    ExternalId.Key otherKey = mock(ExternalId.Key.class);

    when(mockExternalIdKeyFactory.create("username", TEST_USERNAME)).thenReturn(usernameKey);
    when(mockExternalIds.get(usernameKey)).thenReturn(Optional.of(usernameExtId));
    when(usernameExtId.accountId()).thenReturn(accountId);
    when(mockExternalIds.byAccount(accountId)).thenReturn(ImmutableSet.of(otherExtId));
    when(otherExtId.key()).thenReturn(otherKey);
    when(otherKey.isScheme("keycloak-oauth")).thenReturn(false);

    providerWithRopc(true).login(TEST_USERNAME, NOT_A_JWT);
  }
}
