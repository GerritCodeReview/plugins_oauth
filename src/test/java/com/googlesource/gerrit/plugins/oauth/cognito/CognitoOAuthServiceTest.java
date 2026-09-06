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

package com.googlesource.gerrit.plugins.oauth.cognito;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthClient;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import java.net.URI;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CognitoOAuthServiceTest {

  // Mocks for constructor dependencies of CognitoOAuthService
  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;

  // The internal client boundary we want to stub
  @Mock private OAuth20ServiceFactory mockClientFactory;
  @Mock private OAuthClient mockClient;

  // Constants for configuration values
  private static final String TEST_COGNITO_ROOT_URL =
      "https://cognito-idp.us-east-1.amazonaws.com/USER_POOL_ID";
  private static final String DEFAULT_SERVICE_NAME = "Cognito";

  // User details from Cognito
  private static final String COGNITO_USER_ID = "abcdef-12345-uuid";
  private static final String COGNITO_USERNAME = "jane.doe"; // This is the expected username
  private static final String COGNITO_EMAIL = "jane.doe@example.com";
  private static final String COGNITO_NAME = "Jane Doe"; // This is the expected display name

  // Define the prefix locally in the test, mirroring CognitoOAuthService
  private static final String COGNITO_PROVIDER_PREFIX_FOR_TEST = "cognito-oauth:";

  @Before
  public void setUp() throws Exception {
    // Mock the PluginConfigFactory to return our mockPluginConfig
    when(mockConfigFactory.create(CognitoOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);

    // Configure the mockPluginConfig with necessary values for CognitoOAuthService
    // constructor
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(TEST_COGNITO_ROOT_URL);
    when(mockPluginConfig.getString(InitOAuth.SERVICE_NAME, DEFAULT_SERVICE_NAME))
        .thenReturn(DEFAULT_SERVICE_NAME);

    // Stub the client factory so the service talks to our mock client instead of
    // the network.
    when(mockClientFactory.createClient(
            eq(CognitoOAuthService.PROVIDER_NAME), any(), any()))
        .thenReturn(mockClient);
  }

  /**
   * Helper method to create an instance of CognitoOAuthService with a stubbed client. This allows
   * testing the logic of CognitoOAuthService in isolation.
   *
   * @param linkExistingGerritAccounts The value for the 'link-to-existing-gerrit-account' config.
   * @return An instance of CognitoOAuthService with the client stubbed.
   */
  private CognitoOAuthService createService(boolean linkExistingGerritAccounts) {
    // Configure the specific 'link-to-existing-gerrit-account' for this instance
    when(mockPluginConfig.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false))
        .thenReturn(linkExistingGerritAccounts);
    return new CognitoOAuthService(mockConfigFactory, mockClientFactory);
  }

  /**
   * Helper method to stub the user info body returned by Cognito's user info endpoint.
   *
   * @param userId The 'sub' (subject) ID from Cognito.
   * @param username The 'preferred_username' from Cognito. Can be null.
   * @param email The 'email' from Cognito. Can be null.
   * @param name The 'name' from Cognito. Can be null.
   */
  private void mockUserInfoResponse(String userId, String username, String email, String name)
      throws Exception {
    // Construct the JSON response string. Handles nulls correctly for JSON.
    String cognitoJsonResponse =
        String.format(
            "{\"sub\":\"%s\",\"preferred_username\":%s,\"email\":%s,\"name\":%s}",
            userId, // 'sub' should always be a non-null string
            username == null ? "null" : "\"" + username + "\"",
            email == null ? "null" : "\"" + email + "\"",
            name == null ? "null" : "\"" + name + "\"");

    when(mockClient.get(any(URI.class), any(OAuthToken.class))).thenReturn(cognitoJsonResponse);
  }

  /**
   * Test Case 1: linkExistingGerrit=true, username is VALID. Expects claimedIdentity to be
   * "gerrit:{username}".
   */
  @Test
  public void getUserInfo_linkTrue_validUsername_shouldSetClaimedIdentity() throws Exception {
    // --- ARRANGE ---
    CognitoOAuthService service = createService(true); // linkExistingGerrit = true
    mockUserInfoResponse(COGNITO_USER_ID, COGNITO_USERNAME, COGNITO_EMAIL, COGNITO_NAME);
    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    // --- ACT ---
    OAuthUserInfo userInfo = service.getUserInfo(inputToken);

    // --- ASSERT ---
    // Primary assertion for this test case
    assertThat(userInfo.getClaimedIdentity()).isEqualTo("gerrit:" + COGNITO_USERNAME);

    // Secondary assertions for completeness of OAuthUserInfo object
    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId())
        .isEqualTo(COGNITO_PROVIDER_PREFIX_FOR_TEST + COGNITO_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(COGNITO_USERNAME);
    assertThat(userInfo.getDisplayName()).isEqualTo(COGNITO_NAME);
    assertThat(userInfo.getEmailAddress()).isEqualTo(COGNITO_EMAIL);
  }

  /**
   * Test Case 2: linkExistingGerrit=false, username is VALID. Expects claimedIdentity to be null.
   */
  @Test
  public void getUserInfo_linkFalse_validUsername_shouldSetClaimedIdentityNull() throws Exception {
    // --- ARRANGE ---
    CognitoOAuthService service = createService(false); // linkExistingGerrit = false
    mockUserInfoResponse(COGNITO_USER_ID, COGNITO_USERNAME, COGNITO_EMAIL, COGNITO_NAME);
    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    // --- ACT ---
    OAuthUserInfo userInfo = service.getUserInfo(inputToken);

    // --- ASSERT ---
    // Primary assertion for this test case
    assertThat(userInfo.getClaimedIdentity()).isNull();

    // Secondary assertions
    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId())
        .isEqualTo(COGNITO_PROVIDER_PREFIX_FOR_TEST + COGNITO_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(COGNITO_USERNAME);
  }
}
