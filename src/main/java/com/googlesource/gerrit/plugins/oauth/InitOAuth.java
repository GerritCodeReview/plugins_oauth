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

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.googlesource.gerrit.plugins.oauth.airvantage.AirVantageOAuthService;
import com.googlesource.gerrit.plugins.oauth.auth0.Auth0OAuthService;
import com.googlesource.gerrit.plugins.oauth.authentik.AuthentikOAuthService;
import com.googlesource.gerrit.plugins.oauth.azure.AzureActiveDirectoryService;
import com.googlesource.gerrit.plugins.oauth.bitbucket.BitbucketOAuthService;
import com.googlesource.gerrit.plugins.oauth.cas.CasOAuthService;
import com.googlesource.gerrit.plugins.oauth.cognito.CognitoOAuthService;
import com.googlesource.gerrit.plugins.oauth.dex.DexOAuthService;
import com.googlesource.gerrit.plugins.oauth.facebook.FacebookOAuthService;
import com.googlesource.gerrit.plugins.oauth.github.GitHubOAuthService;
import com.googlesource.gerrit.plugins.oauth.gitlab.GitLabOAuthService;
import com.googlesource.gerrit.plugins.oauth.google.GoogleOAuthService;
import com.googlesource.gerrit.plugins.oauth.keycloak.KeycloakOAuthService;
import com.googlesource.gerrit.plugins.oauth.lemon.LemonLDAPOAuthService;
import com.googlesource.gerrit.plugins.oauth.phabricator.PhabricatorOAuthService;
import com.googlesource.gerrit.plugins.oauth.sap.SAPIasOAuthService;
import com.googlesource.gerrit.plugins.oauth.tuleap.TuleapOAuthService;
import java.net.URI;

public class InitOAuth implements InitStep {
  static final String PLUGIN_SECTION = "plugin";
  public static final String CLIENT_ID = "client-id";
  public static final String CLIENT_SECRET = "client-secret";
  public static final String ENABLE_PKCE = "enable-pkce";
  public static final String LINK_TO_EXISTING_OPENID_ACCOUNT = "link-to-existing-openid-accounts";
  public static final String FIX_LEGACY_USER_ID = "fix-legacy-user-id";
  public static final String DOMAIN = "domain";
  public static final String USE_EMAIL_AS_USERNAME = "use-email-as-username";
  public static final String USE_PREFERRED_USERNAME = "use-preferred-username";
  public static final String ROOT_URL = "root-url";
  public static final String REALM = "realm";
  public static final String TENANT = "tenant";
  public static final String LINK_TO_EXISTING_OFFICE365_ACCOUNT =
      "link-to-existing-office365-accounts";
  public static final String LINK_TO_EXISTING_GERRIT_ACCOUNT = "link-to-existing-gerrit-accounts";
  public static final String SERVICE_NAME = "service-name";
  static String FIX_LEGACY_USER_ID_QUESTION = "Fix legacy user id, without oauth provider prefix?";

  private final ConsoleUI ui;
  private final Section.Factory sections;
  private final String pluginName;
  private final Section iasOAuthProviderSection;
  private final Section googleOAuthProviderSection;
  private final Section githubOAuthProviderSection;
  private final Section bitbucketOAuthProviderSection;
  private final Section casOAuthProviderSection;
  private final Section facebookOAuthProviderSection;
  private final Section gitlabOAuthProviderSection;
  private final Section lemonldapOAuthProviderSection;
  private final Section dexOAuthProviderSection;
  private final Section keycloakOAuthProviderSection;
  private final Section office365OAuthProviderSection;
  private final Section azureActiveDirectoryAuthProviderSection;
  private final Section airVantageOAuthProviderSection;
  private final Section phabricatorOAuthProviderSection;
  private final Section tuleapOAuthProviderSection;
  private final Section auth0OAuthProviderSection;
  private final Section authentikOAuthProviderSection;
  private final Section cognitoOAuthProviderSection;

  @Inject
  InitOAuth(ConsoleUI ui, Section.Factory sections, @PluginName String pluginName) {
    this.ui = ui;
    this.sections = sections;
    this.pluginName = pluginName;
    this.googleOAuthProviderSection = getConfigSection(GoogleOAuthService.class);
    this.githubOAuthProviderSection = getConfigSection(GitHubOAuthService.class);
    this.bitbucketOAuthProviderSection = getConfigSection(BitbucketOAuthService.class);
    this.casOAuthProviderSection = getConfigSection(CasOAuthService.class);
    this.facebookOAuthProviderSection = getConfigSection(FacebookOAuthService.class);
    this.gitlabOAuthProviderSection = getConfigSection(GitLabOAuthService.class);
    this.lemonldapOAuthProviderSection = getConfigSection(LemonLDAPOAuthService.class);
    this.dexOAuthProviderSection = getConfigSection(DexOAuthService.class);
    this.keycloakOAuthProviderSection = getConfigSection(KeycloakOAuthService.class);
    this.azureActiveDirectoryAuthProviderSection =
        getConfigSection(AzureActiveDirectoryService.class);
    this.office365OAuthProviderSection =
        getConfigSection(AzureActiveDirectoryService.LEGACY_PROVIDER_NAME);
    this.airVantageOAuthProviderSection = getConfigSection(AirVantageOAuthService.class);
    this.phabricatorOAuthProviderSection = getConfigSection(PhabricatorOAuthService.class);
    this.tuleapOAuthProviderSection = getConfigSection(TuleapOAuthService.class);
    this.auth0OAuthProviderSection = getConfigSection(Auth0OAuthService.class);
    this.authentikOAuthProviderSection = getConfigSection(AuthentikOAuthService.class);
    this.cognitoOAuthProviderSection = getConfigSection(CognitoOAuthService.class);
    this.iasOAuthProviderSection = getConfigSection(SAPIasOAuthService.class);
  }

  @Override
  public void run() throws Exception {
    ui.header("OAuth Authentication Provider");

    boolean configureGoogleOAuthProvider =
        ui.yesno(
            isConfigured(googleOAuthProviderSection),
            "Use Google OAuth provider for Gerrit login ?");
    if (configureGoogleOAuthProvider && configureOAuth(googleOAuthProviderSection)) {
      googleOAuthProviderSection.string(FIX_LEGACY_USER_ID_QUESTION, FIX_LEGACY_USER_ID, "false");
    }

    boolean configueGitHubOAuthProvider =
        ui.yesno(
            isConfigured(githubOAuthProviderSection),
            "Use GitHub OAuth provider for Gerrit login ?");
    if (configueGitHubOAuthProvider && configureOAuth(githubOAuthProviderSection)) {
      githubOAuthProviderSection.string(FIX_LEGACY_USER_ID_QUESTION, FIX_LEGACY_USER_ID, "false");
    }

    boolean configureBitbucketOAuthProvider =
        ui.yesno(
            isConfigured(bitbucketOAuthProviderSection),
            "Use Bitbucket OAuth provider for Gerrit login ?");
    if (configureBitbucketOAuthProvider && configureOAuth(bitbucketOAuthProviderSection)) {
      bitbucketOAuthProviderSection.string(
          FIX_LEGACY_USER_ID_QUESTION, FIX_LEGACY_USER_ID, "false");
    }

    boolean configureCasOAuthProvider =
        ui.yesno(
            isConfigured(casOAuthProviderSection), "Use CAS OAuth provider for Gerrit login ?");
    if (configureCasOAuthProvider && configureOAuth(casOAuthProviderSection)) {
      checkRootUrl(casOAuthProviderSection.string("CAS Root URL", ROOT_URL, null));
      casOAuthProviderSection.string(FIX_LEGACY_USER_ID_QUESTION, FIX_LEGACY_USER_ID, "false");
    }

    boolean configueFacebookOAuthProvider =
        ui.yesno(
            isConfigured(facebookOAuthProviderSection),
            "Use Facebook OAuth provider for Gerrit login ?");
    if (configueFacebookOAuthProvider) {
      configureOAuth(facebookOAuthProviderSection);
    }

    boolean configureGitLabOAuthProvider =
        ui.yesno(
            isConfigured(gitlabOAuthProviderSection),
            "Use GitLab OAuth provider for Gerrit login ?");
    if (configureGitLabOAuthProvider && configureOAuth(gitlabOAuthProviderSection)) {
      checkRootUrl(gitlabOAuthProviderSection.string("GitLab Root URL", ROOT_URL, null));
    }

    boolean configureIASOAuthProvider =
        ui.yesno(
            isConfigured(iasOAuthProviderSection), "Use SAP IAS OAuth provider for Gerrit login ?");
    if (configureIASOAuthProvider && configureOAuth(iasOAuthProviderSection)) {
      checkRootUrl(iasOAuthProviderSection.string("SAP IAS Root URL", ROOT_URL, null));
      iasOAuthProviderSection.string(
          "Enable PKCE for SAP IAS OAuth provider?", ENABLE_PKCE, "false");
    }

    boolean configureLemonLDAPOAuthProvider =
        ui.yesno(
            isConfigured(lemonldapOAuthProviderSection),
            "Use LemonLDAP OAuth provider for Gerrit login ?");
    if (configureLemonLDAPOAuthProvider) {
      checkRootUrl(lemonldapOAuthProviderSection.string("LemonLDAP Root URL", ROOT_URL, null));
      configureOAuth(lemonldapOAuthProviderSection);
    }

    boolean configureDexOAuthProvider =
        ui.yesno(
            isConfigured(dexOAuthProviderSection), "Use Dex OAuth provider for Gerrit login ?");
    if (configureDexOAuthProvider && configureOAuth(dexOAuthProviderSection)) {
      checkRootUrl(dexOAuthProviderSection.string("Dex Root URL", ROOT_URL, null));
    }

    boolean configureKeycloakOAuthProvider =
        ui.yesno(
            isConfigured(keycloakOAuthProviderSection),
            "Use Keycloak OAuth provider for Gerrit login ?");
    if (configureKeycloakOAuthProvider && configureOAuth(keycloakOAuthProviderSection)) {
      checkRootUrl(keycloakOAuthProviderSection.string("Keycloak Root URL", ROOT_URL, null));
      keycloakOAuthProviderSection.string("Keycloak Realm", REALM, null);
      keycloakOAuthProviderSection.string("Link to existing gerrit accounts?", LINK_TO_EXISTING_GERRIT_ACCOUNT, "false");
    }

    // ?: Are there legacy office365 already configured on the system?
    if (isConfigured(office365OAuthProviderSection)) {
      // -> Yes, this system has already configured the old legacy office365.
      boolean configureOffice365OAuthProvider =
          ui.yesno(
              isConfigured(office365OAuthProviderSection),
              "Use Office365 OAuth provider for Gerrit login ?");
      if (configureOffice365OAuthProvider) {
        configureOAuth(office365OAuthProviderSection);
      }
    }
    // E-> No, we either are setting up on an new system or using the new azure config
    else {
      boolean configureAzureActiveDirectoryAuthProvider =
          ui.yesno(
              isConfigured(azureActiveDirectoryAuthProviderSection),
              "Use Azure OAuth provider for Gerrit login ?");
      if (configureAzureActiveDirectoryAuthProvider) {
        configureOAuth(azureActiveDirectoryAuthProviderSection);
        azureActiveDirectoryAuthProviderSection.string(
            "Tenant", TENANT, AzureActiveDirectoryService.DEFAULT_TENANT);
      }
    }

    boolean configureAirVantageOAuthProvider =
        ui.yesno(
            isConfigured(airVantageOAuthProviderSection),
            "Use AirVantage OAuth provider for Gerrit login ?");
    if (configureAirVantageOAuthProvider) {
      configureOAuth(airVantageOAuthProviderSection);
    }

    boolean configurePhabricatorOAuthProvider =
        ui.yesno(
            isConfigured(phabricatorOAuthProviderSection),
            "Use Phabricator OAuth provider for Gerrit login ?");
    if (configurePhabricatorOAuthProvider && configureOAuth(phabricatorOAuthProviderSection)) {
      checkRootUrl(phabricatorOAuthProviderSection.string("Phabricator Root URL", ROOT_URL, null));
    }

    boolean configureTuleapOAuthProvider =
        ui.yesno(
            isConfigured(tuleapOAuthProviderSection),
            "Use Tuleap OAuth provider for Gerrit login ?");
    if (configureTuleapOAuthProvider && configureOAuth(tuleapOAuthProviderSection)) {
      checkRootUrl(tuleapOAuthProviderSection.string("Tuleap Root URL", ROOT_URL, null));
    }

    boolean configureAuth0OAuthProvider =
        ui.yesno(
            isConfigured(auth0OAuthProviderSection), "Use Auth0 OAuth provider for Gerrit login ?");
    if (configureAuth0OAuthProvider && configureOAuth(auth0OAuthProviderSection)) {
      checkRootUrl(auth0OAuthProviderSection.string("Auth0 Root URL", ROOT_URL, null));
    }

    boolean configureAuthentikOAuthProvider =
        ui.yesno(
            isConfigured(authentikOAuthProviderSection),
            "Use Authentik OAuth provider for Gerrit login ?");
    if (configureAuthentikOAuthProvider && configureOAuth(authentikOAuthProviderSection)) {
      checkRootUrl(authentikOAuthProviderSection.string("Authentik Root URL", ROOT_URL, null));
      authentikOAuthProviderSection.string(
          "Link to existing gerrit accounts?", LINK_TO_EXISTING_GERRIT_ACCOUNT, "false");
    }

    boolean configureCognitoOAuthProvider =
        ui.yesno(
            isConfigured(cognitoOAuthProviderSection),
            "Use Cognito OAuth provider for Gerrit login ?");
    if (configureCognitoOAuthProvider && configureOAuth(cognitoOAuthProviderSection)) {
      checkRootUrl(cognitoOAuthProviderSection.string("Cognito Root URL", ROOT_URL, null));
      cognitoOAuthProviderSection.string(
          "Link to existing Gerrit LDAP accounts?", LINK_TO_EXISTING_GERRIT_ACCOUNT, "false");
    }
  }

  /**
   * Retrieve client id to check whether or not this provider was already configured.
   *
   * @param s OAuth provider section
   * @return true if client id key is present, false otherwise
   */
  private static boolean isConfigured(Section s) {
    return !Strings.isNullOrEmpty(s.get(CLIENT_ID));
  }

  /**
   * Configure OAuth provider section
   *
   * @param s section to configure
   * @return true if section is present, false otherwise
   */
  private static boolean configureOAuth(Section s) {
    if (!Strings.isNullOrEmpty(s.string("Application client id", CLIENT_ID, null))) {
      s.passwordForKey("Application client secret", CLIENT_SECRET);
      return true;
    }
    return false;
  }

  /**
   * Check root URL parameter. It must be not null and it must be an absolute URI.
   *
   * @param rootUrl root URL
   * @throws ProvisionException if rootUrl wasn't provided or is not absolute URI.
   */
  private static void checkRootUrl(String rootUrl) {
    requireNonNull(rootUrl);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
  }

  private Section getConfigSection(Class<? extends OAuthServiceProvider> serviceClass) {
    String serviceProviderName =
        serviceClass.getAnnotation(OAuthServiceProviderConfig.class).name();
    return getConfigSection(serviceProviderName);
  }

  private Section getConfigSection(String serviceProviderName) {
    String sectionName = pluginName + "-" + serviceProviderName + "-oauth";
    return sections.get(PLUGIN_SECTION, sectionName);
  }

  @Override
  public void postRun() throws Exception {}
}
