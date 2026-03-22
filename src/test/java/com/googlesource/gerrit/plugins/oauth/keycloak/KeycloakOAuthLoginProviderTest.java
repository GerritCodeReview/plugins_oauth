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
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import java.io.IOException;
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

  @Mock private KeycloakOAuthService mockService;

  private KeycloakOAuthLoginProvider provider() {
    return new KeycloakOAuthLoginProvider(mockService);
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
  public void login_withNonJwtSecret_throwsIOException() throws Exception {
    provider().login(TEST_USERNAME, NOT_A_JWT);
  }

  @Test(expected = IOException.class)
  public void login_withNullSecret_throwsIOException() throws Exception {
    provider().login(TEST_USERNAME, null);
  }
}
