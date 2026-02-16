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

package com.googlesource.gerrit.plugins.oauth.sap;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Provider;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.CombiningValidator;
import com.sap.cloud.security.token.validation.ValidationResult;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SAPOAuthServiceTest {

  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private Provider<String> mockUrlProvider;
  @Mock private OAuth20Service mockScribeOAuthService;
  @Mock private CombiningValidator<Token> mockTokenValidator;

  private static final String TEST_CANONICAL_WEB_URL = "http://gerrit.example.com";
  private static final String TEST_SAP_ROOT_URL = "https://accounts.sap.com";
  private static final String TEST_CLIENT_ID = "sap-client-id";
  private static final String TEST_CLIENT_SECRET = "sap-client-secret";
  private static final String DEFAULT_SERVICE_NAME = "SAP IAS";

  private static final String SAP_USER_ID = "sap-user-123";
  private static final String SAP_USERNAME = "john.sap";
  private static final String SAP_EMAIL = "john.sap@example.com";
  private static final String SAP_FIRST_NAME = "John";
  private static final String SAP_LAST_NAME = "SAP";
  private static final String SAP_DISPLAY_NAME = "John SAP";

  private static final String SAP_PROVIDER_PREFIX_FOR_TEST = "sapias-oauth:";

  private OAuth2AccessToken accessToken;

  @Before
  public void setUp() throws Exception {
    when(mockConfigFactory.create(SAPIasOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);
    when(mockUrlProvider.get()).thenReturn(TEST_CANONICAL_WEB_URL);
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(TEST_SAP_ROOT_URL);
    when(mockPluginConfig.getString(InitOAuth.CLIENT_ID)).thenReturn(TEST_CLIENT_ID);
    when(mockPluginConfig.getString(InitOAuth.CLIENT_SECRET)).thenReturn(TEST_CLIENT_SECRET);
    when(mockPluginConfig.getString(InitOAuth.SERVICE_NAME, DEFAULT_SERVICE_NAME))
        .thenReturn(DEFAULT_SERVICE_NAME);
    when(mockPluginConfig.getBoolean(InitOAuth.ENABLE_PKCE, false)).thenReturn(false);
    accessToken = mock(OAuth2AccessToken.class);
    when(accessToken.getRawResponse())
        .thenReturn(
            "{\"id_token\":\""
                + dummyJwtWithClaims(SAP_USER_ID, SAP_EMAIL, SAP_FIRST_NAME, SAP_LAST_NAME)
                + "\"}");
  }

  private SAPIasOAuthService createServiceAndInjectMock(boolean linkExistingGerritAccounts)
      throws Exception {
    when(mockPluginConfig.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false))
        .thenReturn(linkExistingGerritAccounts);

    SAPIasOAuthService serviceInstance =
        new SAPIasOAuthService(mockConfigFactory, mockUrlProvider, mockTokenValidator);

    Field serviceField = SAPIasOAuthService.class.getDeclaredField("service");
    serviceField.setAccessible(true);
    serviceField.set(serviceInstance, mockScribeOAuthService);
    return serviceInstance;
  }

  private void mockSapIdTokenValidation(boolean valid) {
    ValidationResult mockValidationResult = mock(ValidationResult.class);
    when(mockValidationResult.isValid()).thenReturn(valid);
    when(mockTokenValidator.validate(any(Token.class))).thenReturn(mockValidationResult);
  }

  @Test
  public void getUserInfo_linkExistingAccounts_shouldSetClaimedIdentity() throws Exception {
    SAPIasOAuthService service = createServiceAndInjectMock(true);

    mockSapIdTokenValidation(true);

    OAuthUserInfo userInfo = service.getUserInfo(accessToken);

    assertThat(userInfo.getClaimedIdentity()).isEqualTo("gerrit:" + SAP_USER_ID);
    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId()).isEqualTo(SAP_PROVIDER_PREFIX_FOR_TEST + SAP_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(SAP_USER_ID);
    assertThat(userInfo.getDisplayName()).isEqualTo(SAP_FIRST_NAME + " " + SAP_LAST_NAME);
    assertThat(userInfo.getEmailAddress()).isEqualTo(SAP_EMAIL);
  }

  @Test
  public void getUserInfo_doNotLinkExistingAccounts_shouldSetClaimedIdentityNull()
      throws Exception {
    SAPIasOAuthService service = createServiceAndInjectMock(false);

    mockSapIdTokenValidation(true);

    OAuthUserInfo userInfo = service.getUserInfo(accessToken);

    assertThat(userInfo.getClaimedIdentity()).isNull();
    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId()).isEqualTo(SAP_PROVIDER_PREFIX_FOR_TEST + SAP_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(SAP_USER_ID);
  }

  @Test
  public void fail_authentication() throws Exception {
    SAPIasOAuthService service = createServiceAndInjectMock(false);

    mockSapIdTokenValidation(false);
    IOException ex = assertThrows(IOException.class, () -> service.getUserInfo(accessToken));
    assertThat(ex).hasMessageThat().contains("Authentication error");
  }

  private static String base64Url(String json) {
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String dummyJwtWithClaims(String sub, String email, String first, String last) {
    String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
    String payload =
        base64Url(
            "{"
                + "\"sub\":\""
                + sub
                + "\","
                + "\"email\":\""
                + email
                + "\","
                + "\"first_name\":\""
                + first
                + "\","
                + "\"last_name\":\""
                + last
                + "\""
                + "}");
    String sig = "x";
    return header + "." + payload + "." + sig;
  }
}
