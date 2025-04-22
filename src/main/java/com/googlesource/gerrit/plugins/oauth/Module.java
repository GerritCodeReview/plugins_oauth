// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

public class Module extends AbstractModule {
  private final String pluginName;
  private final Config cfg;

  private boolean loginProviderBound;

  @Inject
  public Module(@GerritServerConfig Config config, @PluginName String pluginName) {
    this.pluginName = pluginName;
    this.cfg = config;
  }

  @Override
  protected void configure() {
    bind(OAuthPluginConfigFactory.class);

    bindOAuthLoginProvider(SAPIasOAuthLoginProvider.class);

    if (!loginProviderBound) {
      bind(OAuthLoginProvider.class)
          .annotatedWith(Exports.named(pluginName))
          .to(DisabledOAuthLoginProvider.class);
    }
  }

  private void bindOAuthLoginProvider(Class<SAPIasOAuthLoginProvider> loginClass) {
    String loginProviderName = loginClass.getAnnotation(OAuthServiceProviderConfig.class).name();
    String cfgSuffix = OAuthPluginConfigFactory.getConfigSuffix(loginProviderName);
    String extIdScheme = OAuthServiceProviderExternalIdScheme.create(loginProviderName);
    if (cfg.getString("plugin", pluginName + cfgSuffix, InitOAuth.CLIENT_ID) != null) {
      bind(OAuthLoginProvider.class).annotatedWith(Exports.named(extIdScheme)).to(loginClass);
      loginProviderBound = true;
    }
  }
}
