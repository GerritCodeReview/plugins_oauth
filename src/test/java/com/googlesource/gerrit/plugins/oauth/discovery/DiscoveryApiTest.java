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

import com.github.scribejava.core.oauth2.clientauthentication.HttpBasicAuthenticationScheme;
import org.junit.Before;
import org.junit.Test;

public class DiscoveryApiTest {
  private static final String AUTHORIZATION_URL = "https://id.example.com/oauth2/authorize";
  private static final String TOKEN_URL = "https://id.example.com/oauth2/token";

  private DiscoveryApi api;

  @Before
  public void setUp() {
    api = new DiscoveryApi(AUTHORIZATION_URL, TOKEN_URL);
  }

  @Test
  public void testGetAuthorizationBaseUrl() {
    assertThat(api.getAuthorizationBaseUrl()).isEqualTo(AUTHORIZATION_URL);
  }

  @Test
  public void testGetAccessTokenEndpoint() {
    assertThat(api.getAccessTokenEndpoint()).isEqualTo(TOKEN_URL);
  }

  @Test
  public void testGetClientAuthentication() {
    assertThat(api.getClientAuthentication())
        .isSameInstanceAs(HttpBasicAuthenticationScheme.instance());
  }
}
