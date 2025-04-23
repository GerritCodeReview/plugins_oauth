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
import com.google.gerrit.server.account.AccountExternalIdCreator;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import java.util.List;
import org.eclipse.jgit.lib.Config;

public class Module extends AbstractModule {
  private final List<String> configuredProviders;
  private final ExternalIdFactory externalIdFactory;

  @Inject
  public Module(
      @GerritServerConfig Config config,
      @PluginName String pluginName,
      ExternalIdFactory externalIdFactory) {
    configuredProviders =
        config.getSubsections("plugin").stream()
            .filter(s -> s.startsWith(pluginName))
            .map(s -> s.substring(pluginName.length() + 1, s.length() - 6))
            .toList();
    this.externalIdFactory = externalIdFactory;
  }

  @Override
  protected void configure() {
    bind(OAuthPluginConfigFactory.class);
    for (String provider : configuredProviders) {
      bind(AccountExternalIdCreator.class)
          .annotatedWith(Exports.named(provider))
          .toInstance(
              new OAuthExternalIdCreator(
                  externalIdFactory, OAuthServiceProviderExternalIdScheme.create(provider)));
    }
  }
}
