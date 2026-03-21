// Copyright (C) 2023 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.discovery;

import static com.google.gerrit.json.OutputFormat.JSON;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.slf4j.LoggerFactory.getLogger;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuth20ServiceFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;

@Singleton
@OAuthServiceProviderConfig(name = DiscoveryOAuthService.PROVIDER_NAME)
public class DiscoveryOAuthService implements OAuthServiceProvider {
  private static final Logger log = getLogger(DiscoveryOAuthService.class);
  public static final String PROVIDER_NAME = "discovery";
  private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
  private final OAuth20Service service;
  private final boolean enablePKCE;
  private final AuthorizationUrlBuilder authorizationUrlBuilder;
  private final String extIdScheme;
  private final String userinfoEndpoint;

  @Inject
  DiscoveryOAuthService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory oauth20ServiceFactory) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);

    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }

    // Fetch the entrypoint from discovery
    DiscoveryOpenIdConnect discovery = fetchDiscoveryDocument(rootUrl + WELL_KNOWN_PATH);

    // Log the discovery endpoints for debugging
    log.info(
        "OpenID Connect discovery:\n"
            + "issuer: {}\n"
            + "endpoint:\n"
            + "\tauth: {}\n"
            + "\ttoken: {}\n"
            + "\tuser_info: {}",
        discovery.getIssuer(),
        discovery.getAuthorizationEndpoint(),
        discovery.getTokenEndpoint(),
        discovery.getUserinfoEndpoint());

    this.userinfoEndpoint = discovery.getUserinfoEndpoint();

    enablePKCE = cfg.getBoolean(InitOAuth.ENABLE_PKCE, false);
    service =
        oauth20ServiceFactory.create(
            PROVIDER_NAME,
            new DiscoveryApi(discovery.getAuthorizationEndpoint(), discovery.getTokenEndpoint()),
            "openid profile email");
    authorizationUrlBuilder = service.createAuthorizationUrlBuilder();
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  private DiscoveryOpenIdConnect fetchDiscoveryDocument(String discoveryUrl) {
    try {
      URL url = new URL(discoveryUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      int responseCode = connection.getResponseCode();
      if (responseCode != SC_OK) {
        throw new IOException("Failed to fetch discovery document: " + responseCode);
      }

      StringBuilder response = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
      }
      return JSON.newGson().fromJson(response.toString(), DiscoveryOpenIdConnect.class);
    } catch (IOException e) {
      throw new ProvisionException("Cannot fetch OpenID Connect discovery document", e);
    }
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuthRequest request = new OAuthRequest(Verb.GET, userinfoEndpoint);
    OAuth2AccessToken t = new OAuth2AccessToken(token.getToken(), token.getRaw());
    service.signRequest(t, request);

    try (Response response = service.execute(request)) {
      if (response.getCode() != SC_OK) {
        throw new IOException(
            String.format(
                "Status %s (%s) for request %s",
                response.getCode(), response.getBody(), request.getUrl()));
      }
      JsonElement userJson = JSON.newGson().fromJson(response.getBody(), JsonElement.class);
      if (log.isDebugEnabled()) {
        log.debug("User info response: {}", response.getBody());
      }
      JsonObject jsonObject = userJson.getAsJsonObject();
      if (jsonObject == null || jsonObject.isJsonNull()) {
        throw new IOException("Response doesn't contain valid user info: " + jsonObject);
      }

      // Try to get user info from standard fields
      JsonElement sub = jsonObject.get("sub");
      JsonElement username = getPreferredValue(jsonObject, "preferred_username", "username");
      JsonElement email = jsonObject.get("email");
      JsonElement name = getPreferredValue(jsonObject, "name", "display_name");

      return new OAuthUserInfo(
          extIdScheme + ":" + (sub != null ? sub.getAsString() : ""),
          username != null && !username.isJsonNull() ? username.getAsString() : null,
          email != null && !email.isJsonNull() ? email.getAsString() : null,
          name != null && !name.isJsonNull() ? name.getAsString() : null,
          null);
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException("Cannot retrieve user info resource", e);
    }
  }

  // Get the prefered one from multiple possible vaules
  private JsonElement getPreferredValue(JsonObject obj, String... keys) {
    for (String key : keys) {
      JsonElement value = obj.get(key);
      if (value != null && !value.isJsonNull()) {
        return value;
      }
    }
    return null;
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier rv) {
    try {
      AccessTokenRequestParams reqParams = AccessTokenRequestParams.create(rv.getValue());
      if (enablePKCE) {
        reqParams.pkceCodeVerifier(authorizationUrlBuilder.getPkce().getCodeVerifier());
      }
      OAuth2AccessToken accessToken = service.getAccessToken(reqParams);
      return new OAuthToken(
          accessToken.getAccessToken(), accessToken.getTokenType(), accessToken.getRawResponse());
    } catch (InterruptedException | ExecutionException | IOException e) {
      String msg = "Cannot retrieve access token";
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  @Override
  public String getAuthorizationUrl() {
    if (enablePKCE) {
      authorizationUrlBuilder.initPKCE();
    }
    return authorizationUrlBuilder.build();
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }

  @Override
  public String getName() {
    return "Discovery OAuth2";
  }
}
