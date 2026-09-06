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

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link OAuthClient} implemented over ScribeJava's {@link OAuth20Service}.
 *
 * <p>This is the only class outside the opt-out providers that imports ScribeJava's request,
 * response or token types; no ScribeJava type crosses the {@link OAuthClient} boundary.
 */
class ScribeOAuthClient implements OAuthClient {
  private final OAuth20Service service;
  private final boolean tolerateMissingTokenType;
  private final boolean enablePkce;

  ScribeOAuthClient(OAuth20Service service) {
    this(service, false, false);
  }

  /**
   * @param tolerateMissingTokenType when {@code true}, a missing {@code token_type} is stored as
   *     the empty string instead of failing. Providers such as CAS may omit it; the default keeps
   *     the value ScribeJava returns.
   * @param enablePkce when {@code true}, the authorization redirect initializes PKCE and carries
   *     the generated code verifier in {@link OAuthAuthorizationInfo}, to be replayed on the token
   *     exchange. The verifier is generated per call and never retained, keeping concurrent logins
   *     independent.
   */
  ScribeOAuthClient(OAuth20Service service, boolean tolerateMissingTokenType, boolean enablePkce) {
    this.service = service;
    this.tolerateMissingTokenType = tolerateMissingTokenType;
    this.enablePkce = enablePkce;
  }

  @Override
  public OAuthAuthorizationInfo getAuthorizationInfo() {
    if (!enablePkce) {
      return new OAuthAuthorizationInfo(service.getAuthorizationUrl(), null);
    }
    AuthorizationUrlBuilder builder = service.createAuthorizationUrlBuilder();
    builder.initPKCE();
    return new OAuthAuthorizationInfo(builder.build(), builder.getPkce().getCodeVerifier());
  }

  @Override
  public OAuthToken exchangeCode(OAuthVerifier verifier, @Nullable String codeVerifier)
      throws IOException {
    try {
      OAuth2AccessToken accessToken;
      if (!enablePkce || codeVerifier == null) {
        accessToken = service.getAccessToken(verifier.getValue());
      } else {
        accessToken =
            service.getAccessToken(
                AccessTokenRequestParams.create(verifier.getValue())
                    .pkceCodeVerifier(codeVerifier));
      }
      String tokenType = accessToken.getTokenType();
      if (tolerateMissingTokenType) {
        tokenType = Strings.nullToEmpty(tokenType);
      }
      return new OAuthToken(accessToken.getAccessToken(), tokenType, accessToken.getRawResponse());
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException("Cannot retrieve access token", e);
    }
  }

  @Override
  public OAuthToken passwordGrant(String username, String password) throws IOException {
    try {
      OAuth2AccessToken accessToken = service.getAccessTokenPasswordGrant(username, password);
      String tokenType = accessToken.getTokenType();
      if (tolerateMissingTokenType) {
        tokenType = Strings.nullToEmpty(tokenType);
      }
      return new OAuthToken(accessToken.getAccessToken(), tokenType, accessToken.getRawResponse());
    } catch (InterruptedException | ExecutionException e) {
      throw new IOException("Cannot retrieve access token", e);
    }
  }

  @Override
  public String get(URI resource, OAuthToken token) throws IOException {
    return get(resource, token, ImmutableMap.of());
  }

  @Override
  public String get(URI resource, OAuthToken token, Map<String, String> headers)
      throws IOException {
    OAuthRequest request = new OAuthRequest(Verb.GET, resource.toString());
    service.signRequest(new OAuth2AccessToken(token.getToken(), token.getRaw()), request);
    headers.forEach(request::addHeader);
    try (Response response = service.execute(request)) {
      if (response.getCode() != HttpServletResponse.SC_OK) {
        throw new IOException(
            String.format(
                "Status %s (%s) for request %s",
                response.getCode(), response.getBody(), request.getUrl()));
      }
      return response.getBody();
    } catch (InterruptedException | ExecutionException e) {
      // Matches the providers' previous behaviour: this path threw an unchecked
      // exception, not an IOException.
      throw new RuntimeException("Cannot retrieve protected resource", e);
    }
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }
}
