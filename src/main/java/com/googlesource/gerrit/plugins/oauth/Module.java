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
import com.google.gerrit.server.account.AccountExternalIdCreator;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.googlesource.gerrit.plugins.oauth.sap.SAPIasModule;
import com.googlesource.gerrit.plugins.oauth.sap.SAPIasOAuthLoginProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Module extends AbstractModule {
  private static final Logger log = LoggerFactory.getLogger(Module.class);
  private static final Map<Class<? extends OAuthLoginProvider>, AbstractModule>
      SUPPORTED_LOGIN_PROVIDERS =
          Map.of(SAPIasOAuthLoginProvider.class, new SAPIasModule());

  private final List<String> configuredProviders;
  private final ExternalIdFactory externalIdFactory;
  private final String pluginName;
  private final Config cfg;

  @Inject
  public Module(
      @GerritServerConfig Config config,
      @PluginName String pluginName,
      ExternalIdFactory externalIdFactory) {
    this.pluginName = pluginName;
    this.configuredProviders =
        config.getSubsections("plugin").stream()
            .filter(s -> s.startsWith(pluginName))
            .map(s -> s.substring(pluginName.length() + 1, s.length() - 6))
            .toList();
    this.externalIdFactory = externalIdFactory;
    this.cfg = config;
  }

  @Override
  protected void configure() {
    bind(OAuthPluginConfigFactory.class);
    bind(OAuth20ServiceFactory.class);
    bindExternalIdCreators();
    bindOAuthProviders();
  }

  private void bindExternalIdCreators() {
    for (String provider : configuredProviders) {
      bind(AccountExternalIdCreator.class)
          .annotatedWith(Exports.named(provider))
          .toInstance(
              new OAuthExternalIdCreator(
                  externalIdFactory, OAuthServiceProviderExternalIdScheme.create(provider)));
    }
  }

  private void bindOAuthProviders() {
    List<Map.Entry<Class<? extends OAuthLoginProvider>, AbstractModule>> gitHttpProviders =
        configuredProviders.stream()
            .flatMap(p -> tryGetSupportedLoginProvider(p).stream())
            .collect(Collectors.toList());

    if (gitHttpProviders.size() > 1) {
      String names =
          gitHttpProviders.stream()
              .map(e -> getLoginProviderName(e.getKey()))
              .collect(Collectors.joining(", "));
      throw new ProvisionException(
          "Multiple OAuth providers configured that support Git-over-HTTP ("
              + names
              + "). Exactly one provider that supports Git-over-HTTP must be configured.");
    }

    if (!gitHttpProviders.isEmpty()) {
      Map.Entry<Class<? extends OAuthLoginProvider>, AbstractModule> entry =
          gitHttpProviders.get(0);
      if (!installOAuthModule(entry.getKey(), entry.getValue())) {
        bindDisabledOAuthProvider();
      }
    } else {
      bindDisabledOAuthProvider();
    }
  }

  private boolean installOAuthModule(
      Class<? extends OAuthLoginProvider> loginClass, AbstractModule oAuthModule) {
    String loginProviderName = getLoginProviderName(loginClass);
    String cfgSuffix = OAuthPluginConfigFactory.getConfigSuffix(loginProviderName);
    if (cfg.getString("plugin", pluginName + cfgSuffix, InitOAuth.CLIENT_ID) != null) {
      install(oAuthModule);
      log.info("Successfully bound {} as OAuth login provider", loginProviderName);
      return true;
    }
    log.error("Failed to bind {} as OAuth login provider", loginProviderName);
    return false;
  }

  private void bindDisabledOAuthProvider() {
    bind(OAuthLoginProvider.class)
        .annotatedWith(Exports.named(pluginName))
        .to(DisabledOAuthLoginProvider.class);
    log.warn("Successfully bound the disabled OAuth login provider");
  }

  private static String getLoginProviderName(
      Class<? extends OAuthLoginProvider> loginProviderClass) {
    return loginProviderClass.getAnnotation(OAuthServiceProviderConfig.class).name();
  }

  private static Optional<Map.Entry<Class<? extends OAuthLoginProvider>, AbstractModule>>
      tryGetSupportedLoginProvider(String providerName) {
    return SUPPORTED_LOGIN_PROVIDERS.entrySet().stream()
        .filter(entry -> getLoginProviderName(entry.getKey()).equals(providerName))
        .findFirst();
  }
}
