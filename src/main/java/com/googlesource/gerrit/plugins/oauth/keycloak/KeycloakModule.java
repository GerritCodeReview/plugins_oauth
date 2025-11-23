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
