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

package com.googlesource.gerrit.plugins.oauth.base;

import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.oauth.client.HttpOAuthClient;
import com.googlesource.gerrit.plugins.oauth.client.OAuthClient;
import com.googlesource.gerrit.plugins.oauth.client.OAuthProviderEndpoints;

/**
 * Builds the ScribeJava-free {@link HttpOAuthClient} for a provider described by an {@link
 * OAuthProviderEndpoints}. Deliberately depends on nothing from {@code com.github.scribejava} so
 * the providers that use it can live in a Bazel target with no ScribeJava on its classpath.
 */
public class HttpOAuthClientFactory {
  private final OAuthPluginConfigFactory cfgFactory;
  private final String canonicalWebUrl;

  @Inject
  public HttpOAuthClientFactory(
      OAuthPluginConfigFactory cfgFactory, @CanonicalWebUrl String canonicalWebUrl) {
    this.cfgFactory = cfgFactory;
    this.canonicalWebUrl = canonicalWebUrl;
  }

  /**
   * client-id/client-secret come from the provider's config and the callback from the canonical web
   * URL, matching the ScribeJava {@code ServiceBuilder} wiring the native path replaces.
   */
  public OAuthClient create(String providerName, OAuthProviderEndpoints endpoints) {
    PluginConfig cfg = cfgFactory.create(providerName);
    return new HttpOAuthClient(
        endpoints,
        cfg.getString(OAuthConfigKeys.CLIENT_ID),
        cfg.getString(OAuthConfigKeys.CLIENT_SECRET),
        canonicalWebUrl + "oauth");
  }
}
