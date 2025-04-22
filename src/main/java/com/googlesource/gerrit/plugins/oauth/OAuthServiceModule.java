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
import com.google.inject.servlet.ServletModule;

public class OAuthServiceModule extends ServletModule {
  private final OAuthPluginConfigFactory cfgFactory;
  private final Class<? extends OAuthServiceProvider> serviceProviderClass;
  private final String serviceProviderName;

  @Inject
  public OAuthServiceModule(
      OAuthPluginConfigFactory cfgFactory,
      Class<? extends OAuthServiceProvider> serviceProviderClass) {
    this.cfgFactory = cfgFactory;
    this.serviceProviderClass = serviceProviderClass;

    serviceProviderName =
        serviceProviderClass.getAnnotation(OAuthServiceProviderConfig.class).name();
  }

  @Override
  public void configureServlets() {
    PluginConfig cfg = cfgFactory.create(serviceProviderName);
    if (cfg.getString(InitOAuth.CLIENT_ID) != null) {
      bindOAuthServiceProvider();
      configureAdditionalServiceComponents();
    }
  }

  protected void bindOAuthServiceProvider() {
    String extIdScheme = OAuthServiceProviderExternalIdScheme.create(serviceProviderName);
    bind(OAuthServiceProvider.class)
        .annotatedWith(Exports.named(extIdScheme))
        .to(serviceProviderClass);
  }

  public void configureAdditionalServiceComponents() {}
}
