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

package com.googlesource.gerrit.plugins.oauth.keycloak;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;

public class KeycloakModule extends AbstractModule {
  @Override
  public void configure() {
    String extIdScheme =
        OAuthServiceProviderExternalIdScheme.create(KeycloakOAuthService.PROVIDER_NAME);
    bind(OAuthLoginProvider.class)
        .annotatedWith(Exports.named(extIdScheme))
        .to(KeycloakOAuthLoginProvider.class);
  }
}
