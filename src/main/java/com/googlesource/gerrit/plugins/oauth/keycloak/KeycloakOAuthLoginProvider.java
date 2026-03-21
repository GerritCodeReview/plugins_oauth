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

package com.googlesource.gerrit.plugins.oauth.keycloak;

import com.google.common.base.Splitter;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@OAuthServiceProviderConfig(name = KeycloakOAuthService.PROVIDER_NAME)
public class KeycloakOAuthLoginProvider implements OAuthLoginProvider {
  private static final Logger log = LoggerFactory.getLogger(KeycloakOAuthLoginProvider.class);

  private final KeycloakOAuthService service;

  @Inject
  KeycloakOAuthLoginProvider(KeycloakOAuthService service) {
    this.service = service;
  }

  @Override
  public OAuthUserInfo login(String username, String secret) throws IOException {
    if (secret == null) {
      throw new IOException("Authentication error");
    }
    if (!isJwt(secret)) {
      throw new IOException("Authentication error");
    }
    OAuthUserInfo userInfo = service.getUserInfoFromBearerToken(secret);
    // Username is optional, but if provided it must match the identity returned by Keycloak
    // to prevent account confusion. The actual authentication decision is based on the
    // external ID returned by the userinfo endpoint, not the provided username.
    if (username != null && !username.equals(userInfo.getUserName())) {
      throw new IOException("Authentication error: username does not match");
    }
    return userInfo;
  }

  /** Returns true if {@code s} looks like a JWT (three base64url segments separated by dots). */
  private boolean isJwt(String s) {
    return Splitter.on('.').splitToList(s).size() == 3;
  }
}
