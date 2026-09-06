// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.azure;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.jwtPayloadJson;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.AbstractOAuthService;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.net.URI;

@Singleton
@OAuthServiceProviderConfig(name = AzureActiveDirectoryService.PROVIDER_NAME)
public class AzureActiveDirectoryService extends AbstractOAuthService {
  // Canonical provider name (Azure AD)
  public static final String PROVIDER_NAME = "azure";
  // Deprecated provider name kept for backward compatibility
  private static final String PROVIDER_DEPRECATED_NAME = "office365";
  private static final String PROTECTED_RESOURCE_URL = "https://graph.microsoft.com/v1.0/me";
  private static final String SCOPE =
      "openid offline_access https://graph.microsoft.com/user.readbasic.all";
  public static final String DEFAULT_TENANT = "organizations";
  private static final ImmutableSet<String> TENANTS_WITHOUT_VALIDATION =
      ImmutableSet.<String>builder().add(DEFAULT_TENANT).add("common").add("consumers").build();
  private final Gson gson;
  private final boolean useEmailAsUsername;
  private final String tenant;
  private final String clientId;
  private final boolean linkOffice365Id;
  private final String extIdScheme;
  // The deprecated Office365 external ID is used for linking
  // existing accounts from previous Gerrit installations.
  private final String extIdDeprecatedScheme;

  @Inject
  AzureActiveDirectoryService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory clientFactory) {
    super("Office365 OAuth2");
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    this.extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
    this.extIdDeprecatedScheme =
        OAuthServiceProviderExternalIdScheme.create(PROVIDER_DEPRECATED_NAME);
    this.useEmailAsUsername = cfg.getBoolean(InitOAuth.USE_EMAIL_AS_USERNAME, false);
    this.tenant = cfg.getString(InitOAuth.TENANT, DEFAULT_TENANT);
    this.clientId = cfg.getString(InitOAuth.CLIENT_ID);
    this.client =
        clientFactory.createClient(
            PROVIDER_NAME, new AzureApi(tenant), SCOPE);
    this.gson = JSON.newGson();
    if (log.isDebugEnabled()) {
      log.debug("OAuth2: scope={}", SCOPE);
      log.debug("OAuth2: useEmailAsUsername={}", useEmailAsUsername);
    }
    this.linkOffice365Id = cfg.getBoolean(InitOAuth.LINK_TO_EXISTING_OFFICE365_ACCOUNT, false);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    // ?: Have we set a custom tenant and is this a tenant other than the one set in
    // TENANTS_WITHOUT_VALIDATION
    if (!TENANTS_WITHOUT_VALIDATION.contains(tenant)) {
      // -> Yes, we are using a tenant that should be validated, so verify that is issued for the
      // same one that we
      // have set.
      String tid = getTokenJson(token.getToken()).get("tid").getAsString();

      // ?: Verify that this token has the same tenant as we are currently using
      if (!tenant.equals(tid)) {
        // -> No, this tenant does not equals the one in the token. So we should stop processing
        log.warn(
            String.format(
                "The token was issued by the tenant [%s] while we are set to use [%s]",
                tid, tenant));
        // Return null so the user will be shown Unauthorized.
        return null;
      }
    }

    // Due to scribejava does not expose the id_token we need to do this a bit convoluted way to
    // extract this our self
    // see <a href="https://github.com/scribejava/scribejava/issues/968">Obtaining id_token from
    // access_token</a> for
    // the scribejava issue on this.
    String rawToken = token.getRaw();
    JsonObject jwtJson = gson.fromJson(rawToken, JsonObject.class);
    String idTokenBase64 = jwtJson.get("id_token").getAsString();
    String aud = getTokenJson(idTokenBase64).get("aud").getAsString();

    // ?: Does this token have the same clientId set in the 'aud' part of the id_token as we are
    // using.
    // If not we should reject it
    // see <a href="https://docs.microsoft.com/en-us/azure/active-directory/develop/id-tokens">id
    // tokens Payload claims></a>
    // for information on the aud claim.
    if (!clientId.equals(aud)) {
      log.warn(
          String.format(
              "The id_token had aud [%s] while we expected it to be equal to the clientId [%s]",
              aud, clientId));
      // Return null so the user will be shown Unauthorized.
      return null;
    }

    String body =
        client.get(
            URI.create(PROTECTED_RESOURCE_URL), token, ImmutableMap.of("Accept", "*/*"));
    if (log.isDebugEnabled()) {
      log.debug("User info response: {}", body);
    }
    JsonElement userJson = JSON.newGson().fromJson(body, JsonElement.class);
    if (userJson.isJsonObject()) {
      JsonObject jsonObject = userJson.getAsJsonObject();
      JsonElement id = jsonObject.get("id");
      if (isNull(id)) {
        throw new IOException("Response doesn't contain id field");
      }
      JsonElement email = jsonObject.get("mail");
      JsonElement name = jsonObject.get("displayName");
      String login = null;

      if (useEmailAsUsername && !email.isJsonNull()) {
        login = email.getAsString().split("@")[0];
      }

      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          login,
          asString(email),
          asString(name),
          linkOffice365Id ? extIdDeprecatedScheme + ":" + id.getAsString() : null);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }

  /** Get the {@link JsonObject} of a given token. */
  private JsonObject getTokenJson(String tokenBase64) {
    try {
      return gson.fromJson(jwtPayloadJson(tokenBase64), JsonObject.class);
    } catch (IOException e) {
      throw new IllegalStateException("Invalid token payload encoding", e);
    }
  }
}
