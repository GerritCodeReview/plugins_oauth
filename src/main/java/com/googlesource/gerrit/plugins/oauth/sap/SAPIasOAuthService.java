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

package com.googlesource.gerrit.plugins.oauth.sap;

import static org.slf4j.LoggerFactory.getLogger;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import com.sap.cloud.security.json.DefaultJsonObject;
import com.sap.cloud.security.token.SapIdToken;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;

@Singleton
public class SAPIasOAuthService implements OAuthServiceProvider {
  private static final Logger log = getLogger(SAPIasOAuthService.class);
  static final String PROVIDER_NAME = "sapias";
  private final OAuth20Service service;
  private final String serviceName;
  private final String rootUrl;
  private final boolean linkExistingGerrit;
  private final boolean enablePKCE;
  private final AuthorizationUrlBuilder authorizationUrlBuilder;
  private final String extIdScheme;

  @Inject
  SAPIasOAuthService(
      OAuthPluginConfigFactory cfgFactory, @CanonicalWebUrl Provider<String> urlProvider) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    String canonicalWebUrl = CharMatcher.is('/').trimTrailingFrom(urlProvider.get()) + "/";
    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    serviceName = cfg.getString(InitOAuth.SERVICE_NAME, "SAP IAS");
    linkExistingGerrit = cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false);
    enablePKCE = cfg.getBoolean(InitOAuth.ENABLE_PKCE, false);
    service =
        new ServiceBuilder(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .callback(canonicalWebUrl + "oauth")
            .defaultScope("openid profile email")
            .build(new SAPIasApi(rootUrl));
    authorizationUrlBuilder = service.createAuthorizationUrlBuilder();
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuth2AccessToken t = new OAuth2AccessToken(token.getToken(), token.getRaw());
    return getUserInfo(t);
  }

  public OAuthUserInfo getUserInfo(OAuth2AccessToken token) {
    SapIdToken sapToken = new SapIdToken(getIdToken(token));
    String username = sapToken.getClaimAsString("sub");
    String externalId = this.extIdScheme + ":" + username;
    String email = sapToken.getClaimAsString("email");
    String firstName = sapToken.getClaimAsString("first_name");
    String lastName = sapToken.getClaimAsString("last_name");
    String displayName = null;
    if (!Strings.isNullOrEmpty(firstName) && !Strings.isNullOrEmpty(lastName)) {
      displayName = firstName + " " + lastName;
    } else if (!Strings.isNullOrEmpty(firstName)) {
      displayName = firstName;
    } else if (!Strings.isNullOrEmpty(lastName)) {
      displayName = lastName;
    }
    String claimedIdentity = linkExistingGerrit ? "gerrit:" + username : null;
    return new OAuthUserInfo(externalId, username, email, displayName, claimedIdentity);
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier rv) {
    try {
      AccessTokenRequestParams reqParams = AccessTokenRequestParams.create(rv.getValue());
      if (enablePKCE) {
        reqParams.pkceCodeVerifier(authorizationUrlBuilder.getPkce().getCodeVerifier());
      }
      OAuth2AccessToken accessToken = service.getAccessToken(reqParams);
      return new OAuthToken(
          accessToken.getAccessToken(), accessToken.getTokenType(), accessToken.getRawResponse());
    } catch (InterruptedException | ExecutionException | IOException e) {
      String msg = "Cannot retrieve access token";
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public String getAuthorizationUrl() {
    if (enablePKCE) {
      authorizationUrlBuilder.initPKCE();
    }
    return authorizationUrlBuilder.build();
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }

  @Override
  public String getName() {
    return serviceName;
  }

  private String getIdToken(OAuth2AccessToken token) {
    try {
      String raw = token.getRawResponse();
      return new DefaultJsonObject(raw).getAsString("id_token");
    } catch (IllegalStateException e) {
      return token.getAccessToken();
    }
  }
}
