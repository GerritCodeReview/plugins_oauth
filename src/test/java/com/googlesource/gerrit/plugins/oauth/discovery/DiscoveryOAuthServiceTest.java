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

package com.googlesource.gerrit.plugins.oauth.discovery;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.core.pkce.PKCE;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.ProvisionException;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DiscoveryOAuthServiceTest {
  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private OAuth20Service mockScribeOAuthService;
  @Mock private OAuth20ServiceFactory mockServiceFactory;

  private static final String TEST_DISCOVERY_ROOT_URL = "https://id.example.com/realms/gerrit";
  private static final String TEST_ISSUER = "https://id.example.com/realms/gerrit";
  private static final String TEST_AUTHORIZATION_ENDPOINT =
      "https://id.example.com/realms/gerrit/protocol/openid-connect/auth";
  private static final String TEST_TOKEN_ENDPOINT =
      "https://id.example.com/realms/gerrit/protocol/openid-connect/token";
  private static final String TEST_USERINFO_ENDPOINT =
      "https://id.example.com/realms/gerrit/protocol/openid-connect/userinfo";

  private static final String DISCOVERY_PROVIDER_PREFIX_FOR_TEST = "discovery-oauth:";

  @Before
  public void setUp() {
    when(mockConfigFactory.create(DiscoveryOAuthService.PROVIDER_NAME))
        .thenReturn(mockPluginConfig);
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(TEST_DISCOVERY_ROOT_URL);

    when(mockServiceFactory.create(anyString(), any(DefaultApi20.class), anyString()))
        .thenReturn(mockScribeOAuthService);
  }

  private DiscoveryOpenIdConnect mockDiscoveryDocument(
      String issuer, String authorizationEndpoint, String tokenEndpoint, String userinfoEndpoint) {
    DiscoveryOpenIdConnect discovery = mock(DiscoveryOpenIdConnect.class);
    when(discovery.getIssuer()).thenReturn(issuer);
    when(discovery.getAuthorizationEndpoint()).thenReturn(authorizationEndpoint);
    when(discovery.getTokenEndpoint()).thenReturn(tokenEndpoint);
    when(discovery.getUserinfoEndpoint()).thenReturn(userinfoEndpoint);
    return discovery;
  }

  private DiscoveryOpenIdConnect validDiscoveryDocument() {
    return mockDiscoveryDocument(
        TEST_ISSUER, TEST_AUTHORIZATION_ENDPOINT, TEST_TOKEN_ENDPOINT, TEST_USERINFO_ENDPOINT);
  }

  private DiscoveryOAuthService createServiceWithDiscoveryDoc(DiscoveryOpenIdConnect discovery) {
    return new DiscoveryOAuthService(mockConfigFactory, mockServiceFactory) {
      @Override
      DiscoveryOpenIdConnect fetchDiscoveryDocument(String discoveryUrl) {
        return discovery;
      }
    };
  }

  private ProvisionException assertProvisionException(DiscoveryOpenIdConnect discovery) {
    try {
      createServiceWithDiscoveryDoc(discovery);
    } catch (ProvisionException e) {
      return e;
    }
    throw new AssertionError("expected ProvisionException");
  }

  private ProvisionException assertConstructorProvisionException() {
    try {
      new DiscoveryOAuthService(mockConfigFactory, mockServiceFactory);
    } catch (ProvisionException e) {
      return e;
    }
    throw new AssertionError("expected ProvisionException");
  }

  private void mockUserInfoResponse(String body) throws Exception {
    Response mockHttpResponse = mock(Response.class);
    when(mockHttpResponse.getCode()).thenReturn(HttpServletResponse.SC_OK);
    when(mockHttpResponse.getBody()).thenReturn(body);
    when(mockScribeOAuthService.execute(any(OAuthRequest.class))).thenReturn(mockHttpResponse);
  }

  @Test
  public void getAuthorizationInfo_withPkceEnabled_shouldReturnVerifier() throws Exception {
    when(mockPluginConfig.getBoolean("enable-pkce", false)).thenReturn(true);

    AuthorizationUrlBuilder mockUrlBuilder = mock(AuthorizationUrlBuilder.class);
    when(mockScribeOAuthService.createAuthorizationUrlBuilder()).thenReturn(mockUrlBuilder);

    PKCE pkce = new PKCE();
    pkce.setCodeVerifier("secret-verifier-123");

    when(mockUrlBuilder.getPkce()).thenReturn(pkce);
    when(mockUrlBuilder.build()).thenReturn("https://id.example.com/auth?code_challenge=xyz");

    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());
    OAuthAuthorizationInfo info = service.getAuthorizationInfo();

    assertThat(info.getAuthorizationUrl()).contains("code_challenge=xyz");
    assertThat(info.getPkceVerifier()).isEqualTo("secret-verifier-123");
  }

  @Test
  public void getAccessToken_withPkce_shouldPassVerifierToScribe() throws Exception {
    when(mockPluginConfig.getBoolean("enable-pkce", false)).thenReturn(true);
    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());

    OAuthVerifier verifier = new OAuthVerifier("auth-code");
    String secureVerifierFromSession = "session-secret-verifier";

    com.github.scribejava.core.model.OAuth2AccessToken mockToken =
        mock(com.github.scribejava.core.model.OAuth2AccessToken.class);

    when(mockToken.getAccessToken()).thenReturn("dummy-access-token");
    when(mockToken.getTokenType()).thenReturn("Bearer");
    when(mockToken.getRawResponse()).thenReturn("raw-json-response");

    when(mockScribeOAuthService.getAccessToken(any(AccessTokenRequestParams.class)))
        .thenReturn(mockToken);

    service.getAccessToken(verifier, secureVerifierFromSession);

    ArgumentCaptor<AccessTokenRequestParams> captor =
        ArgumentCaptor.forClass(AccessTokenRequestParams.class);
    verify(mockScribeOAuthService).getAccessToken(captor.capture());

    assertThat(captor.getValue().getPkceCodeVerifier()).isEqualTo(secureVerifierFromSession);
  }

  @Test
  public void getUserInfo_validStandardFields_shouldMapUserInfo() throws Exception {
    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());

    mockUserInfoResponse(
        "{\"sub\":\"12345\",\"preferred_username\":\"jane.doe\","
            + "\"email\":\"jane.doe@example.com\",\"name\":\"Jane Doe\"}");

    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    OAuthUserInfo userInfo = service.getUserInfo(inputToken);

    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId()).isEqualTo(DISCOVERY_PROVIDER_PREFIX_FOR_TEST + "12345");
    assertThat(userInfo.getUserName()).isEqualTo("jane.doe");
    assertThat(userInfo.getEmailAddress()).isEqualTo("jane.doe@example.com");
    assertThat(userInfo.getDisplayName()).isEqualTo("Jane Doe");
    assertThat(userInfo.getClaimedIdentity()).isNull();
  }

  @Test
  public void getUserInfo_fallbackFields_shouldMapUserInfo() throws Exception {
    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());

    mockUserInfoResponse(
        "{\"sub\":\"67890\",\"username\":\"john\","
            + "\"email\":\"john@example.com\",\"display_name\":\"John Doe\"}");

    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    OAuthUserInfo userInfo = service.getUserInfo(inputToken);

    assertThat(userInfo).isNotNull();
    assertThat(userInfo.getExternalId()).isEqualTo(DISCOVERY_PROVIDER_PREFIX_FOR_TEST + "67890");
    assertThat(userInfo.getUserName()).isEqualTo("john");
    assertThat(userInfo.getEmailAddress()).isEqualTo("john@example.com");
    assertThat(userInfo.getDisplayName()).isEqualTo("John Doe");
  }

  @Test
  public void getUserInfo_missingSub_shouldThrowIOException() throws Exception {
    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());

    mockUserInfoResponse(
        "{\"preferred_username\":\"jane.doe\",\"email\":\"jane.doe@example.com\"}");

    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    try {
      service.getUserInfo(inputToken);
    } catch (IOException e) {
      assertThat(e).hasMessageThat().contains("sub");
      return;
    }

    throw new AssertionError("expected IOException");
  }

  @Test
  public void getUserInfo_nonObjectJson_shouldThrowIOException() throws Exception {
    DiscoveryOAuthService service = createServiceWithDiscoveryDoc(validDiscoveryDocument());

    mockUserInfoResponse("[]");

    OAuthToken inputToken =
        new OAuthToken("dummyAccessToken", "dummySecretForTest", "dummyRawResponse");

    try {
      service.getUserInfo(inputToken);
    } catch (IOException e) {
      assertThat(e).hasMessageThat().contains("not a JSON Object");
      return;
    }

    throw new AssertionError("expected IOException");
  }

  @Test
  public void constructor_missingRootUrl_shouldThrowProvisionException() {
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn(null);

    ProvisionException e = assertConstructorProvisionException();

    assertThat(e).hasMessageThat().contains("Root URL must be configured");
  }

  @Test
  public void constructor_relativeRootUrl_shouldThrowProvisionException() {
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn("/relative/path");

    ProvisionException e = assertConstructorProvisionException();

    assertThat(e).hasMessageThat().contains("Root URL must be absolute URL");
  }

  @Test
  public void constructor_unsupportedRootUrlScheme_shouldThrowProvisionException() {
    when(mockPluginConfig.getString(InitOAuth.ROOT_URL)).thenReturn("ftp://id.example.com/realm");

    ProvisionException e = assertConstructorProvisionException();

    assertThat(e).hasMessageThat().contains("Root URL must use http or https");
  }

  @Test
  public void constructor_nullDiscoveryDocument_shouldThrowProvisionException() {
    ProvisionException e = assertProvisionException(null);

    assertThat(e).hasMessageThat().contains("Discovery document is empty");
  }

  @Test
  public void constructor_missingIssuer_shouldThrowProvisionException() {
    DiscoveryOpenIdConnect discovery =
        mockDiscoveryDocument(
            null, TEST_AUTHORIZATION_ENDPOINT, TEST_TOKEN_ENDPOINT, TEST_USERINFO_ENDPOINT);

    ProvisionException e = assertProvisionException(discovery);

    assertThat(e).hasMessageThat().contains("missing required field: issuer");
  }

  @Test
  public void constructor_malformedTokenEndpoint_shouldThrowProvisionException() {
    DiscoveryOpenIdConnect discovery =
        mockDiscoveryDocument(
            TEST_ISSUER, TEST_AUTHORIZATION_ENDPOINT, "http://[invalid", TEST_USERINFO_ENDPOINT);

    ProvisionException e = assertProvisionException(discovery);

    assertThat(e).hasMessageThat().contains("not a valid URL: token_endpoint");
  }

  @Test
  public void constructor_relativeUserinfoEndpoint_shouldThrowProvisionException() {
    DiscoveryOpenIdConnect discovery =
        mockDiscoveryDocument(
            TEST_ISSUER,
            TEST_AUTHORIZATION_ENDPOINT,
            TEST_TOKEN_ENDPOINT,
            "/protocol/openid-connect/userinfo");

    ProvisionException e = assertProvisionException(discovery);

    assertThat(e).hasMessageThat().contains("userinfo_endpoint");
    assertThat(e).hasMessageThat().contains("absolute URL");
  }

  @Test
  public void constructor_unsupportedUserinfoEndpointScheme_shouldThrowProvisionException() {
    DiscoveryOpenIdConnect discovery =
        mockDiscoveryDocument(
            TEST_ISSUER,
            TEST_AUTHORIZATION_ENDPOINT,
            TEST_TOKEN_ENDPOINT,
            "ftp://id.example.com/realms/gerrit/protocol/openid-connect/userinfo");

    ProvisionException e = assertProvisionException(discovery);

    assertThat(e).hasMessageThat().contains("must use http or https: userinfo_endpoint");
  }
}
