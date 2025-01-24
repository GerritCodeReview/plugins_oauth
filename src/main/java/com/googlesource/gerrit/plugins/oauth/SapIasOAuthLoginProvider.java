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

package com.googlesource.gerrit.plugins.oauth;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
class SapIasOAuthLoginProvider implements OAuthLoginProvider {
  private final SapIasOAuthService service;

  @Inject
  SapIasOAuthLoginProvider(SapIasOAuthService service) {
    this.service = service;
  }

  @Override
  public OAuthUserInfo login(String username, String token) throws IOException {
    if (username == null || token == null) {
      throw new IOException("Authentication error");
    }
    OAuth2AccessToken accessToken = new OAuth2AccessToken(token);
    return service.getUserInfo(accessToken);
  }
}
