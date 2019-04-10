// Copyright (C) 2019 The Android Open Source Project
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
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Provider;
import java.net.URLEncoder;
import org.eclipse.jgit.lib.Config;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GithubApiUrlTest {
  private static final String PLUGIN_NAME = "gerrit-oauth-provider";
  private static final String CANONICAL_URL = "https://localhost";
  private static final String TEST_CLIENT_ID = "test_client_id";

  @Mock private PluginConfigFactory pluginConfigFactoryMock;
  @Mock private Provider<String> urlProviderMock;

  private OAuthServiceProvider getGithubOAuthProvider(String rootUrl) {
    PluginConfig pluginConfig = new PluginConfig(PLUGIN_NAME + GitHubOAuthService.CONFIG_SUFFIX, new Config());
    if (rootUrl != null) {
        pluginConfig.setString(InitOAuth.ROOT_URL, rootUrl);
    }
    pluginConfig.setString(InitOAuth.CLIENT_ID, TEST_CLIENT_ID);
    pluginConfig.setString(InitOAuth.CLIENT_SECRET, "test_client_scret");
    when(pluginConfigFactoryMock.getFromGerritConfig(PLUGIN_NAME + GitHubOAuthService.CONFIG_SUFFIX))
        .thenReturn(pluginConfig);
    when(urlProviderMock.get()).thenReturn(CANONICAL_URL);

    return new GitHubOAuthService(
            pluginConfigFactoryMock, PLUGIN_NAME, urlProviderMock);
  }

  private String getExpectedUrl(String rootUrl) {
      if (rootUrl == null) {
          rootUrl = GitHubOAuthService.GITHUB_ROOT_URL;
      }
      if (!rootUrl.endsWith("/")) {
          rootUrl += "/";
      }
      return new StringBuilder(rootUrl).append("login/oauth/authorize?client_id=")
          .append(TEST_CLIENT_ID)
          .append("&redirect_uri=")
          .append(URLEncoder.encode(CANONICAL_URL))
          .append(URLEncoder.encode("/oauth"))
          .toString();
  }

  @Test
  public void nullUrlIsLoaded() throws Exception {
    String rootUrl = null;
    OAuthServiceProvider provider = getGithubOAuthProvider(rootUrl);
    String expected = getExpectedUrl(rootUrl);
    assertThat(provider.getAuthorizationUrl()).isEqualTo(expected);
  }

  @Test
  public void githubUrlIsLoaded() throws Exception {
    String rootUrl = "https://github.com";
    OAuthServiceProvider provider = getGithubOAuthProvider(rootUrl);
    String expected = getExpectedUrl(rootUrl);
    assertThat(provider.getAuthorizationUrl()).isEqualTo(expected);
  }

  @Test
  public void githubUrlWithTrailIsLoaded() throws Exception {
    String rootUrl = "https://github.com/";
    OAuthServiceProvider provider = getGithubOAuthProvider(rootUrl);
    String expected = getExpectedUrl(rootUrl);
    assertThat(provider.getAuthorizationUrl()).isEqualTo(expected);
  }

  @Test
  public void gheUrlIsLoaded() throws Exception {
    String rootUrl = "https://git.yourcompany.com";
    OAuthServiceProvider provider = getGithubOAuthProvider(rootUrl);
    String expected = getExpectedUrl(rootUrl);
    assertThat(provider.getAuthorizationUrl()).isEqualTo(expected);
  }

  @Test
  public void gheUrlWithTrailIsLoaded() throws Exception {
    String rootUrl = "https://git.yourcompany.com/";
    OAuthServiceProvider provider = getGithubOAuthProvider(rootUrl);
    String expected = getExpectedUrl(rootUrl);
    assertThat(provider.getAuthorizationUrl()).isEqualTo(expected);
  }

}
