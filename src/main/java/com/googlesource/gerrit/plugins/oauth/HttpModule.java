// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.google.inject.servlet.ServletModule;

class HttpModule extends ServletModule {
  private final OAuthPluginConfigFactory cfgFactory;

  @Inject
  HttpModule(OAuthPluginConfigFactory cfgFactory) {
    this.cfgFactory = cfgFactory;
  }

  @Override
  protected void configureServlets() {
    bindOAuthProvider(GoogleOAuthService.class);
    bindOAuthProvider(GitHubOAuthService.class);
    bindOAuthProvider(BitbucketOAuthService.class);
    bindOAuthProvider(CasOAuthService.class);
    bindOAuthProvider(FacebookOAuthService.class);
    bindOAuthProvider(GitLabOAuthService.class);
    bindOAuthProvider(LemonLDAPOAuthService.class);
    bindOAuthProvider(DexOAuthService.class);
    bindOAuthProvider(KeycloakOAuthService.class);
    bindOAuthProvider(AzureActiveDirectoryService.class);
    bindOAuthProvider(AirVantageOAuthService.class);
    bindOAuthProvider(PhabricatorOAuthService.class);
    bindOAuthProvider(TuleapOAuthService.class);
    bindOAuthProvider(Auth0OAuthService.class);
    bindOAuthProvider(AuthentikOAuthService.class);
    bindOAuthProvider(CognitoOAuthService.class);
    bindOAuthProvider(SAPIasOAuthService.class);
  }

  private void bindOAuthProvider(Class<? extends OAuthServiceProvider> serviceClass) {
    String serviceProviderName =
        serviceClass.getAnnotation(OAuthServiceProviderConfig.class).name();
    PluginConfig cfg = cfgFactory.create(serviceProviderName);
    if (cfg.getString(InitOAuth.CLIENT_ID) != null) {
      String extIdScheme = OAuthServiceProviderExternalIdScheme.create(serviceProviderName);
      bind(OAuthServiceProvider.class).annotatedWith(Exports.named(extIdScheme)).to(serviceClass);
    }
  }
}
