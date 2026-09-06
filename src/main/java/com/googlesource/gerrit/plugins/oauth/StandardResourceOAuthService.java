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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for providers that read the user information from a protected resource fetched with the
 * access token.
 *
 * <p>Subclasses supply only the resource URL ({@link #resourceUrl()}), the mapping of its response
 * ({@link #parseUserInfo(String)}), the service name, and construct the {@link OAuthClient}. Token
 * exchange, the authorization redirect, the version and the fetch itself are handled here, once.
 */
public abstract class StandardResourceOAuthService implements OAuthServiceProvider {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** The client for this provider; must be assigned by the subclass constructor. */
  protected OAuthClient client;

  private final String name;

  protected StandardResourceOAuthService(String name) {
    this.name = name;
  }

  /** @return the protected resource URL to fetch the user information from. */
  protected abstract String resourceUrl();

  /** Maps the resource response body to Gerrit's user information. */
  protected abstract OAuthUserInfo parseUserInfo(String body) throws IOException;

  @Override
  public final OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    String body = client.get(URI.create(resourceUrl()), token);
    if (log.isDebugEnabled()) {
      log.debug("User info response: {}", body);
    }
    return parseUserInfo(body);
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier verifier, @Nullable String codeVerifier) {
    try {
      return client.exchangeCode(verifier, codeVerifier);
    } catch (IOException e) {
      String msg = "Cannot retrieve access token";
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public OAuthAuthorizationInfo getAuthorizationInfo() {
    return client.getAuthorizationInfo();
  }

  @Override
  public String getVersion() {
    return client.getVersion();
  }

  @Override
  public String getName() {
    return name;
  }
}
