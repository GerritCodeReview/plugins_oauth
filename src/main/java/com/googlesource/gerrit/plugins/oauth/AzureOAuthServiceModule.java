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

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.ServletModule;
import com.googlesource.gerrit.plugins.oauth.azure.AzureActiveDirectoryService;

public class AzureOAuthServiceModule extends ServletModule {
  private final OAuthPluginConfigFactory cfgFactory;

  @Inject
  public AzureOAuthServiceModule(OAuthPluginConfigFactory cfgFactory) {
    this.cfgFactory = cfgFactory;
  }

  @Override
  public void configureServlets() {
    boolean office365LegacyProviderBound = false;
    PluginConfig cfg = cfgFactory.create(AzureActiveDirectoryService.LEGACY_PROVIDER_NAME);
    if (cfg.getString(InitOAuth.CLIENT_ID) != null) {
      office365LegacyProviderBound = true;
      bindOAuthServiceProvider(AzureActiveDirectoryService.LEGACY_PROVIDER_NAME);
    }
    cfg = cfgFactory.create(AzureActiveDirectoryService.PROVIDER_NAME);
    if (cfg.getString(InitOAuth.CLIENT_ID) != null) {
      // ?: Check if the legacy Office365 is already bound, we can only have one of these bound at
      // one time
      if (office365LegacyProviderBound) {
        // -> Yes, the legacy Office365 is already bound and we are trying to bind the
        // AzureActiveDirectoryService.CONFIG_SUFFIX at the same time.
        throw new ProvisionException("Legacy Office365 OAuth provider is already bound!");
      }
      bindOAuthServiceProvider(AzureActiveDirectoryService.PROVIDER_NAME);
    }
  }

  protected void bindOAuthServiceProvider(String serviceProviderName) {
    String extIdScheme = OAuthServiceProviderExternalIdScheme.create(serviceProviderName);
    bind(OAuthServiceProvider.class)
        .annotatedWith(Exports.named(extIdScheme))
        .to(AzureActiveDirectoryService.class);
  }
}
