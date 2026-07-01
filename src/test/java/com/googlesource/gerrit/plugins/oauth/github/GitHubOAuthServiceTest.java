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

package com.googlesource.gerrit.plugins.oauth.github;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import java.lang.reflect.Field;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitHubOAuthServiceTest {
  private static final String TEST_CANONICAL_WEB_URL = "http://gerrit.example.com";
  private static final String TEST_CLIENT_ID = "test-client-id";
  private static final String TEST_CLIENT_SECRET = "test-client-secret";

  private static final String GITHUB_USER_ID = "12345";
  private static final String GITHUB_LOGIN = "octocat";
  private static final String GITHUB_EMAIL = "octocat@github.com";
  private static final String GITHUB_NAME = "Octo Cat";

  @Mock private OAuthPluginConfigFactory mockConfigFactory;
  @Mock private PluginConfig mockPluginConfig;
  @Mock private OAuth20Service mockScribeOAuthService;

  private GitHubOAuthService service;

  @Before
  public void setUp() throws Exception {
    when(mockConfigFactory.create(GitHubOAuthService.PROVIDER_NAME)).thenReturn(mockPluginConfig);
    when(mockPluginConfig.getString(eq(InitOAuth.ROOT_URL), anyString()))
        .thenReturn(GitHubOAuthService.GITHUB_ROOT_URL);
    when(mockPluginConfig.getString(eq(InitOAuth.CLIENT_ID), anyString()))
        .thenReturn(TEST_CLIENT_ID);
    when(mockPluginConfig.getString(eq(InitOAuth.CLIENT_SECRET), anyString()))
        .thenReturn(TEST_CLIENT_SECRET);
    when(mockPluginConfig.getBoolean(eq(InitOAuth.FIX_LEGACY_USER_ID), anyBoolean()))
        .thenReturn(false);

    OAuth20ServiceFactory serviceFactory =
        new OAuth20ServiceFactory(mockConfigFactory, TEST_CANONICAL_WEB_URL);
    service = new GitHubOAuthService(mockConfigFactory, serviceFactory);

    Field serviceField = GitHubOAuthService.class.getDeclaredField("service");
    serviceField.setAccessible(true);
    serviceField.set(service, mockScribeOAuthService);
  }

  @Test
  public void getUserInfoWithPublicEmail() throws Exception {
    String userJson =
        String.format(
            "{\"login\":\"%s\",\"id\":%s,\"email\":\"%s\",\"name\":\"%s\"}",
            GITHUB_LOGIN, GITHUB_USER_ID, GITHUB_EMAIL, GITHUB_NAME);

    when(mockScribeOAuthService.execute(any(OAuthRequest.class)))
        .thenReturn(mockResponse(HttpServletResponse.SC_OK, userJson));

    OAuthUserInfo userInfo =
        service.getUserInfo(new OAuthToken("token", "bearer", "raw"));

    assertThat(userInfo.getExternalId()).isEqualTo("github-oauth:" + GITHUB_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(GITHUB_LOGIN);
    assertThat(userInfo.getEmailAddress()).isEqualTo(GITHUB_EMAIL);
    assertThat(userInfo.getDisplayName()).isEqualTo(GITHUB_NAME);
  }

  @Test
  public void getUserInfoWithPrivateEmail() throws Exception {
    String userJson =
        String.format(
            "{\"login\":\"%s\",\"id\":%s,\"email\":null,\"name\":\"%s\"}",
            GITHUB_LOGIN, GITHUB_USER_ID, GITHUB_NAME);
    String emailsJson =
        String.format(
            "[{\"email\":\"%s\",\"primary\":true,\"verified\":true,\"visibility\":\"private\"}]",
            GITHUB_EMAIL);

    when(mockScribeOAuthService.execute(any(OAuthRequest.class)))
        .thenReturn(
            mockResponse(HttpServletResponse.SC_OK, userJson),
            mockResponse(HttpServletResponse.SC_OK, emailsJson));

    OAuthUserInfo userInfo =
        service.getUserInfo(new OAuthToken("token", "bearer", "raw"));

    assertThat(userInfo.getExternalId()).isEqualTo("github-oauth:" + GITHUB_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(GITHUB_LOGIN);
    assertThat(userInfo.getEmailAddress()).isEqualTo(GITHUB_EMAIL);
    assertThat(userInfo.getDisplayName()).isEqualTo(GITHUB_NAME);
  }

  @Test
  public void getUserInfoWithPrivateEmailAndEmailsFailure() throws Exception {
    String userJson =
        String.format(
            "{\"login\":\"%s\",\"id\":%s,\"email\":null,\"name\":\"%s\"}",
            GITHUB_LOGIN, GITHUB_USER_ID, GITHUB_NAME);

    when(mockScribeOAuthService.execute(any(OAuthRequest.class)))
        .thenReturn(
            mockResponse(HttpServletResponse.SC_OK, userJson),
            mockResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error"));

    OAuthUserInfo userInfo =
        service.getUserInfo(new OAuthToken("token", "bearer", "raw"));

    assertThat(userInfo.getExternalId()).isEqualTo("github-oauth:" + GITHUB_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(GITHUB_LOGIN);
    assertThat(userInfo.getDisplayName()).isEqualTo(GITHUB_NAME);
    assertThat(userInfo.getEmailAddress()).isNull();
  }

  @Test
  public void getUserInfoWithPrivateEmailAndNoPrimaryInList() throws Exception {
    String userJson =
        String.format(
            "{\"login\":\"%s\",\"id\":%s,\"email\":null,\"name\":\"%s\"}",
            GITHUB_LOGIN, GITHUB_USER_ID, GITHUB_NAME);
    String emailsJson =
        "[{\"email\":\"secondary@github.com\",\"primary\":false,\"verified\":true}]";

    when(mockScribeOAuthService.execute(any(OAuthRequest.class)))
        .thenReturn(
            mockResponse(HttpServletResponse.SC_OK, userJson),
            mockResponse(HttpServletResponse.SC_OK, emailsJson));

    OAuthUserInfo userInfo =
        service.getUserInfo(new OAuthToken("token", "bearer", "raw"));

    assertThat(userInfo.getExternalId()).isEqualTo("github-oauth:" + GITHUB_USER_ID);
    assertThat(userInfo.getUserName()).isEqualTo(GITHUB_LOGIN);
    assertThat(userInfo.getDisplayName()).isEqualTo(GITHUB_NAME);
    assertThat(userInfo.getEmailAddress()).isNull();
  }

  private static Response mockResponse(int code, String body) throws Exception {
    Response mock = mock(Response.class);
    when(mock.getCode()).thenReturn(code);
    when(mock.getBody()).thenReturn(body);
    return mock;
  }
}
