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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.AbstractOAuthService;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import com.sap.cloud.security.json.DefaultJsonObject;
import com.sap.cloud.security.token.SapIdToken;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.validation.CombiningValidator;
import com.sap.cloud.security.token.validation.ValidationResult;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@OAuthServiceProviderConfig(name = SAPIasOAuthService.PROVIDER_NAME)
public class SAPIasOAuthService extends AbstractOAuthService {
  static final String PROVIDER_NAME = "sapias";
  private final boolean linkExistingGerrit;
  private final String extIdScheme;
  private final CombiningValidator<Token> tokenValidator;

  @Inject
  SAPIasOAuthService(
      OAuthPluginConfigFactory cfgFactory,
      OAuth20ServiceFactory clientFactory,
      CombiningValidator<Token> tokenValidator) {
    super(cfgFactory.create(PROVIDER_NAME).getString(InitOAuth.SERVICE_NAME, "SAP IAS"));
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    linkExistingGerrit = cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_GERRIT_ACCOUNT, false);
    boolean enablePKCE = cfg.getBoolean(InitOAuth.ENABLE_PKCE, false);
    client =
        clientFactory.createClient(
            PROVIDER_NAME,
            new SAPIasApi(rootUrl),
            "openid profile email",
            true,
            enablePKCE);
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
    this.tokenValidator = tokenValidator;
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    SapIdToken sapToken = new SapIdToken(getIdToken(token));
    ValidationResult res = tokenValidator.validate(sapToken);
    if (!res.isValid()) {
      log.warn("Invalid token received for " + sapToken.getClaimAsString("sub"));
      throw new IOException("Authentication error");
    }

    String username = sapToken.getClaimAsString("sub");
    String externalId = extIdScheme + ":" + username;
    String email = sapToken.getClaimAsString("email");
    String firstName = sapToken.getClaimAsString("first_name");
    String lastName = sapToken.getClaimAsString("last_name");
    String displayName =
        Strings.emptyToNull(
            Stream.of(firstName, lastName)
                .filter(s -> !Strings.isNullOrEmpty(s))
                .collect(Collectors.joining(" ")));
    String claimedIdentity = linkExistingGerrit ? "gerrit:" + username : null;
    return new OAuthUserInfo(externalId, username, email, displayName, claimedIdentity);
  }

  /** Exchanges resource-owner credentials for a token (resource-owner password flow). */
  public OAuthToken getAccessToken(String username, String password) {
    try {
      return client.passwordGrant(username, password);
    } catch (IOException e) {
      String msg = "Cannot retrieve access token";
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  private static String getIdToken(OAuthToken token) {
    try {
      return new DefaultJsonObject(token.getRaw()).getAsString("id_token");
    } catch (IllegalStateException e) {
      return token.getToken();
    }
  }
}
