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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import java.lang.reflect.Field;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CognitoOAuthServiceTest {

  // Mocks for constructor dependencies of CognitoOAuthService
  @Mock private PluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private Provider<String> mockUrlProvider;

  // This is the ScribeJava service we want to mock
  @Mock private OAuth20Service mockScribeOAuthService;

  // Constants for configuration values
  private static final String TEST_PLUGIN_NAME = "gerrit-oauth-provider-cognito";
  private static final String TEST_CANONICAL_WEB_URL = "http://gerrit.example.com";
  private static final String TEST_COGNITO_ROOT_URL =
      "https://cognito-idp.us-east-1.amazonaws.com/USER_POOL_ID";
  private static final String TEST_CLIENT_ID = "test-client-id-123";
  private static final String TEST_CLIENT_SECRET = "test-client-secret-abc";
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
    when(mockConfigFactory.getFromGerritConfig(
            TEST_PLUGIN_NAME + CognitoOAuthService.CONFIG_SUFFIX))
        .thenReturn(mockPluginConfig);

    // Mock the CanonicalWebUrl provider
    when(mockUrlProvider.get()).thenReturn(TEST_CANONICAL_WEB_URL);

    // Configure the mockPluginConfig with necessary values for CognitoOAuthService
    // constructor
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(TEST_COGNITO_ROOT_URL);
    when(mockPluginConfig.getString(InitOAuth.CLIENT_ID)).thenReturn(TEST_CLIENT_ID);
    when(mockPluginConfig.getString(InitOAuth.CLIENT_SECRET)).thenReturn(TEST_CLIENT_SECRET);
    when(mockPluginConfig.getString(InitOAuth.SERVICE_NAME, DEFAULT_SERVICE_NAME))
        .thenReturn(DEFAULT_SERVICE_NAME);
  }

  /**
   * Helper method to create an instance of CognitoOAuthService with a mocked internal
   * OAuth20Service. This allows testing the logic of CognitoOAuthService in isolation.
   *
   * @param linkExistingGerritAccounts The value for the 'link-to-existing-gerrit-account' config.
   * @return An instance of CognitoOAuthService with the ScribeJava service mocked.
   * @throws Exception If reflection fails.
   */
  private CognitoOAuthService createServiceAndInjectMock(boolean linkExistingGerritAccounts)
      throws Exception {
    // Configure the specific 'link-to-existing-gerrit-account' for this instance
    when(mockPluginConfig.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false))
        .thenReturn(linkExistingGerritAccounts);

    CognitoOAuthService serviceInstance =
        new CognitoOAuthService(mockConfigFactory, TEST_PLUGIN_NAME, mockUrlProvider);

    // Replace the internal OAuth20Service with our mock using reflection.
    Field serviceField = CognitoOAuthService.class.getDeclaredField("service");
    serviceField.setAccessible(true); // Allow modification of the private final field
    serviceField.set(serviceInstance, mockScribeOAuthService); // Inject our mock
    return serviceInstance;
  }

  /**
   * Helper method to mock the HTTP response from Cognito's user info endpoint.
   *
   * @param userId The 'sub' (subject) ID from Cognito.
   * @param username The 'preferred_username' from Cognito. Can be null.
   * @param email The 'email' from Cognito. Can be null.
   * @param name The 'name' from Cognito. Can be null.
   * @throws Exception If mocking fails.
   */
  private void mockCognitoUserInfoResponse(
      String userId, String username, String email, String name) throws Exception {
    // Construct the JSON response string. Handles nulls correctly for JSON.
    String cognitoJsonResponse =
        String.format(
            "{\"sub\":\"%s\",\"preferred_username\":%s,\"email\":%s,\"name\":%s}",
            userId, // 'sub' should always be a non-null string
            username == null ? "null" : "\"" + username + "\"",
            email == null ? "null" : "\"" + email + "\"",
            name == null ? "null" : "\"" + name + "\"");

    Response mockHttpResponse = mock(Response.class);
    when(mockHttpResponse.getCode()).thenReturn(HttpServletResponse.SC_OK);
    // Simulate successful HTTP 200 OK
    when(mockHttpResponse.getBody()).thenReturn(cognitoJsonResponse);

    // Configure the mockScribeOAuthService to return this mockHttpResponse
    when(mockScribeOAuthService.execute(any(OAuthRequest.class))).thenReturn(mockHttpResponse);
  }

  /**
   * Test Case 1: linkExistingGerrit=true, username is VALID. Expects claimedIdentity to be
   * "gerrit:{username}".
   */
  @Test
  public void getUserInfo_linkTrue_validUsername_shouldSetClaimedIdentity() throws Exception {
    // --- ARRANGE ---
    CognitoOAuthService service = createServiceAndInjectMock(true);
    // linkExistingGerrit = true
    mockCognitoUserInfoResponse(COGNITO_USER_ID, COGNITO_USERNAME, COGNITO_EMAIL, COGNITO_NAME);
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
    CognitoOAuthService service = createServiceAndInjectMock(false);
    // linkExistingGerrit = false
    mockCognitoUserInfoResponse(COGNITO_USER_ID, COGNITO_USERNAME, COGNITO_EMAIL, COGNITO_NAME);
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
