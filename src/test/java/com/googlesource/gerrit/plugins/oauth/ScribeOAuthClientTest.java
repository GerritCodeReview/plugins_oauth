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

package com.googlesource.gerrit.plugins.oauth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.github.scribejava.core.pkce.PKCE;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import java.net.URI;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScribeOAuthClientTest {
  @Mock private OAuth20Service service;

  private static OAuth2AccessToken accessToken(String token, String type, String raw) {
    // OAuth2AccessToken getters are final, so build a real instance instead of a mock.
    return new OAuth2AccessToken(token, type, null, null, null, raw);
  }

  @Test
  public void getAuthorizationInfo_pkceDisabled_returnsPlainUrl() {
    when(service.getAuthorizationUrl()).thenReturn("https://idp/auth");

    OAuthAuthorizationInfo info = new ScribeOAuthClient(service).getAuthorizationInfo();

    assertThat(info.getAuthorizationUrl()).isEqualTo("https://idp/auth");
    assertThat(info.getPkceVerifier()).isNull();
  }

  @Test
  public void getAuthorizationInfo_pkceEnabled_initsPkceAndReturnsVerifier() {
    AuthorizationUrlBuilder builder = mock(AuthorizationUrlBuilder.class);
    when(service.createAuthorizationUrlBuilder()).thenReturn(builder);
    PKCE pkce = new PKCE();
    pkce.setCodeVerifier("verifier-123");
    when(builder.getPkce()).thenReturn(pkce);
    when(builder.build()).thenReturn("https://idp/auth?code_challenge=abc");

    OAuthAuthorizationInfo info =
        new ScribeOAuthClient(service, false, true).getAuthorizationInfo();

    verify(builder).initPKCE();
    assertThat(info.getAuthorizationUrl()).isEqualTo("https://idp/auth?code_challenge=abc");
    assertThat(info.getPkceVerifier()).isEqualTo("verifier-123");
  }

  @Test
  public void exchangeCode_withCodeVerifier_passesPkceVerifier() throws Exception {
    when(service.getAccessToken(any(AccessTokenRequestParams.class)))
        .thenReturn(accessToken("at", "Bearer", "raw"));

    new ScribeOAuthClient(service).exchangeCode(new OAuthVerifier("code"), "code-verifier");

    ArgumentCaptor<AccessTokenRequestParams> captor =
        ArgumentCaptor.forClass(AccessTokenRequestParams.class);
    verify(service).getAccessToken(captor.capture());
    assertThat(captor.getValue().getPkceCodeVerifier()).isEqualTo("code-verifier");
  }

  @Test
  public void exchangeCode_defaultKeepsTokenType() throws Exception {
    when(service.getAccessToken(anyString())).thenReturn(accessToken("at", "Bearer", "raw"));

    OAuthToken token = new ScribeOAuthClient(service).exchangeCode(new OAuthVerifier("code"), null);

    assertThat(token.getToken()).isEqualTo("at");
    assertThat(token.getSecret()).isEqualTo("Bearer");
    assertThat(token.getRaw()).isEqualTo("raw");
  }

  @Test
  public void exchangeCode_tolerateMissingTokenType_storesEmptyString() throws Exception {
    when(service.getAccessToken(anyString())).thenReturn(accessToken("at", null, "raw"));

    OAuthToken token =
        new ScribeOAuthClient(service, true, false).exchangeCode(new OAuthVerifier("code"), null);

    assertThat(token.getSecret()).isEmpty();
  }

  @Test
  public void passwordGrant_mapsAccessToken() throws Exception {
    when(service.getAccessTokenPasswordGrant("user", "pass"))
        .thenReturn(accessToken("at", "Bearer", "raw"));

    OAuthToken token = new ScribeOAuthClient(service).passwordGrant("user", "pass");

    assertThat(token.getToken()).isEqualTo("at");
    assertThat(token.getSecret()).isEqualTo("Bearer");
    assertThat(token.getRaw()).isEqualTo("raw");
  }

  @Test
  public void passwordGrant_tolerateMissingTokenType_storesEmptyString() throws Exception {
    when(service.getAccessTokenPasswordGrant("user", "pass"))
        .thenReturn(accessToken("at", null, "raw"));

    OAuthToken token = new ScribeOAuthClient(service, true, false).passwordGrant("user", "pass");

    assertThat(token.getSecret()).isEmpty();
  }

  @Test
  public void get_returnsBodyOnSuccess() throws Exception {
    Response response = mock(Response.class);
    when(response.getCode()).thenReturn(HttpServletResponse.SC_OK);
    when(response.getBody()).thenReturn("body");
    when(service.execute(any(OAuthRequest.class))).thenReturn(response);

    String body =
        new ScribeOAuthClient(service)
            .get(URI.create("https://idp/userinfo"), new OAuthToken("t", "s", "r"));

    assertThat(body).isEqualTo("body");
  }

  @Test
  public void get_addsExtraHeaders() throws Exception {
    Response response = mock(Response.class);
    when(response.getCode()).thenReturn(HttpServletResponse.SC_OK);
    when(response.getBody()).thenReturn("body");
    when(service.execute(any(OAuthRequest.class))).thenReturn(response);

    new ScribeOAuthClient(service)
        .get(
            URI.create("https://idp/userinfo"),
            new OAuthToken("t", "s", "r"),
            ImmutableMap.of("Accept", "*/*"));

    ArgumentCaptor<OAuthRequest> captor = ArgumentCaptor.forClass(OAuthRequest.class);
    verify(service).execute(captor.capture());
    assertThat(captor.getValue().getHeaders()).containsEntry("Accept", "*/*");
  }
}
