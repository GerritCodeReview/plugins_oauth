// Copyright (C) 2016 The Android Open Source Project
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

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;

public class OAuth20ServiceFactory {
  private final OAuthPluginConfigFactory cfgFactory;
  private final String canonicalWebUrl;

  @Inject
  public OAuth20ServiceFactory(
      OAuthPluginConfigFactory cfgFactory, @CanonicalWebUrl String canonicalWebUrl) {
    this.cfgFactory = cfgFactory;
    this.canonicalWebUrl = canonicalWebUrl;
  }

  public OAuth20Service create(String providerName, DefaultApi20 api) {
    return create(providerName, api, null);
  }

  public OAuth20Service create(String providerName, DefaultApi20 api, @Nullable String scope) {
    PluginConfig cfg = cfgFactory.create(providerName);
    ServiceBuilder builder =
        new ServiceBuilder(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .callback(canonicalWebUrl + "oauth");

    if (!Strings.isNullOrEmpty(scope)) {
      builder.defaultScope(scope);
    }

    return builder.build(api);
  }
}
