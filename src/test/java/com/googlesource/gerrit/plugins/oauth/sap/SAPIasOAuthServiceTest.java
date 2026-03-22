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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.core.pkce.PKCE;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.CombiningValidator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SAPIasOAuthServiceTest {

  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private OAuth20ServiceFactory mockServiceFactory;
  @Mock private OAuth20Service mockScribeOAuthService;
  @Mock private CombiningValidator<Token> mockTokenValidator;
  @Mock private AuthorizationUrlBuilder mockUrlBuilder;

  private static final String TEST_SAP_ROOT_URL = "https://accounts.sap.com";

  @Before
  public void setUp() {
    when(mockConfigFactory.create(SAPIasOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(TEST_SAP_ROOT_URL);

    when(mockServiceFactory.create(
            anyString(),
            any(com.github.scribejava.core.builder.api.DefaultApi20.class),
            anyString()))
        .thenReturn(mockScribeOAuthService);
  }

  @Test
  public void getAuthorizationInfo_withPkce_shouldReturnVerifierFromLocalBuilder() {
    when(mockPluginConfig.getBoolean(InitOAuth.ENABLE_PKCE, false)).thenReturn(true);

    AuthorizationUrlBuilder mockUrlBuilder = mock(AuthorizationUrlBuilder.class);
    when(mockScribeOAuthService.createAuthorizationUrlBuilder()).thenReturn(mockUrlBuilder);

    PKCE pkce = new PKCE();
    pkce.setCodeVerifier("sap-secret-verifier");

    when(mockUrlBuilder.getPkce()).thenReturn(pkce);
    when(mockUrlBuilder.build()).thenReturn("https://sap.com/auth?code_challenge=xyz");

    SAPIasOAuthService service =
        new SAPIasOAuthService(mockConfigFactory, mockServiceFactory, mockTokenValidator);
    OAuthAuthorizationInfo info = service.getAuthorizationInfo();

    assertThat(info.getPkceVerifier()).isEqualTo("sap-secret-verifier");
  }

  @Test
  public void getAccessToken_withPkce_shouldUsePassedVerifierIgnoringInternalState()
      throws Exception {
    when(mockPluginConfig.getBoolean(InitOAuth.ENABLE_PKCE, false)).thenReturn(true);
    SAPIasOAuthService service =
        new SAPIasOAuthService(mockConfigFactory, mockServiceFactory, mockTokenValidator);

    OAuthVerifier verifier = new OAuthVerifier("auth-code");
    String verifierFromSession = "session-stored-verifier";

    com.github.scribejava.core.model.OAuth2AccessToken mockToken =
        mock(com.github.scribejava.core.model.OAuth2AccessToken.class);

    when(mockToken.getAccessToken()).thenReturn("token");
    when(mockToken.getTokenType()).thenReturn("Bearer");
    when(mockToken.getRawResponse()).thenReturn("dummy-raw");

    when(mockScribeOAuthService.getAccessToken(any(AccessTokenRequestParams.class)))
        .thenReturn(mockToken);

    service.getAccessToken(verifier, verifierFromSession);

    ArgumentCaptor<AccessTokenRequestParams> captor =
        ArgumentCaptor.forClass(AccessTokenRequestParams.class);
    verify(mockScribeOAuthService).getAccessToken(captor.capture());

    assertThat(captor.getValue().getPkceCodeVerifier()).isEqualTo(verifierFromSession);
  }
}
