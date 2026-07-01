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

package com.googlesource.gerrit.plugins.oauth.github;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitHubOAuthServiceTest {
  private static final String USER_RESPONSE =
      "{\"id\":\"12345\",\"login\":\"octocat\",\"email\":null,\"name\":\"The Octocat\"}";
  private static final String USER_RESPONSE_WITHOUT_EMAIL =
      "{\"id\":\"12345\",\"login\":\"octocat\",\"name\":\"The Octocat\"}";
  private static final String USER_RESPONSE_WITH_PUBLIC_EMAIL =
      "{\"id\":\"12345\",\"login\":\"octocat\","
          + "\"email\":\"public@example.com\",\"name\":\"The Octocat\"}";
  private static final String EMAILS_RESPONSE =
      "["
          + "{\"email\":\"secondary@example.com\",\"primary\":false,\"verified\":true},"
          + "{\"email\":\"primary@example.com\",\"primary\":true,\"verified\":true}"
          + "]";

  @Mock private OAuthPluginConfigFactory configFactory;
  @Mock private PluginConfig pluginConfig;
  @Mock private OAuth20ServiceFactory serviceFactory;
  @Mock private OAuth20Service scribeService;

  @Before
  public void setUp() {
    when(configFactory.create(GitHubOAuthService.PROVIDER_NAME)).thenReturn(pluginConfig);
    when(pluginConfig.getString(InitOAuth.ROOT_URL, GitHubOAuthService.GITHUB_ROOT_URL))
        .thenReturn(GitHubOAuthService.GITHUB_ROOT_URL);
    when(pluginConfig.getBoolean(InitOAuth.FIX_LEGACY_USER_ID, false)).thenReturn(false);
    when(serviceFactory.create(
            eq(GitHubOAuthService.PROVIDER_NAME),
            any(GitHub2Api.class),
            eq(GitHubOAuthService.SCOPE)))
        .thenReturn(scribeService);
  }

  @Test
  public void getUserInfoFetchesPrivatePrimaryEmailWhenUserEmailIsNull() throws Exception {
    Response userResponse = response(USER_RESPONSE);
    Response emailsResponse = response(EMAILS_RESPONSE);
    when(scribeService.execute(any(OAuthRequest.class))).thenReturn(userResponse, emailsResponse);

    OAuthUserInfo userInfo = newService().getUserInfo(newToken());

    assertThat(userInfo.getExternalId()).isEqualTo("github-oauth:12345");
    assertThat(userInfo.getUserName()).isEqualTo("octocat");
    assertThat(userInfo.getEmailAddress()).isEqualTo("primary@example.com");
    assertThat(userInfo.getDisplayName()).isEqualTo("The Octocat");

    ArgumentCaptor<OAuthRequest> requestCaptor = ArgumentCaptor.forClass(OAuthRequest.class);
    verify(scribeService, times(2)).execute(requestCaptor.capture());
    assertThat(requestCaptor.getAllValues().get(0).getUrl())
        .isEqualTo("https://api.github.com/user");
    assertThat(requestCaptor.getAllValues().get(1).getUrl())
        .isEqualTo("https://api.github.com/user/emails");
  }

  @Test
  public void getUserInfoUsesPublicEmailWithoutFetchingEmailsEndpoint() throws Exception {
    Response userResponse = response(USER_RESPONSE_WITH_PUBLIC_EMAIL);
    when(scribeService.execute(any(OAuthRequest.class))).thenReturn(userResponse);

    OAuthUserInfo userInfo = newService().getUserInfo(newToken());

    assertThat(userInfo.getEmailAddress()).isEqualTo("public@example.com");
    ArgumentCaptor<OAuthRequest> requestCaptor = ArgumentCaptor.forClass(OAuthRequest.class);
    verify(scribeService).execute(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getUrl()).isEqualTo("https://api.github.com/user");
  }

  @Test
  public void getUserInfoFetchesPrivatePrimaryEmailWhenUserEmailIsMissing() throws Exception {
    Response userResponse = response(USER_RESPONSE_WITHOUT_EMAIL);
    Response emailsResponse = response(EMAILS_RESPONSE);
    when(scribeService.execute(any(OAuthRequest.class))).thenReturn(userResponse, emailsResponse);

    OAuthUserInfo userInfo = newService().getUserInfo(newToken());

    assertThat(userInfo.getEmailAddress()).isEqualTo("primary@example.com");
    ArgumentCaptor<OAuthRequest> requestCaptor = ArgumentCaptor.forClass(OAuthRequest.class);
    verify(scribeService, times(2)).execute(requestCaptor.capture());
    assertThat(requestCaptor.getAllValues().get(1).getUrl())
        .isEqualTo("https://api.github.com/user/emails");
  }

  private static Response response(String body) throws Exception {
    Response response = mock(Response.class);
    when(response.getCode()).thenReturn(HttpServletResponse.SC_OK);
    when(response.getBody()).thenReturn(body);
    return response;
  }

  private GitHubOAuthService newService() {
    return new GitHubOAuthService(configFactory, serviceFactory);
  }

  private static OAuthToken newToken() {
    return new OAuthToken("access-token", "secret", "raw-response");
  }
}
