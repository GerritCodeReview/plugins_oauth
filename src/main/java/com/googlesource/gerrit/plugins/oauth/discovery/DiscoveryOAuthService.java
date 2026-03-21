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

package com.googlesource.gerrit.plugins.oauth.discovery;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static org.slf4j.LoggerFactory.getLogger;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.io.CharStreams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.auth.oauth.OAuthAuthorizationInfo;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;

@Singleton
@OAuthServiceProviderConfig(name = DiscoveryOAuthService.PROVIDER_NAME)
public class DiscoveryOAuthService implements OAuthServiceProvider {
  private static final Logger log = getLogger(DiscoveryOAuthService.class);

  public static final String PROVIDER_NAME = "discovery";
  private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";
  private static final String SCOPE = "openid profile email";

  private final OAuth20Service service;
  private final boolean enablePKCE;
  private final String extIdScheme;
  private final String userinfoEndpoint;

  @Inject
  DiscoveryOAuthService(
      OAuthPluginConfigFactory cfgFactory, OAuth20ServiceFactory oauth20ServiceFactory) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);

    String rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    URI rootUri = validateRootUrl(rootUrl);

    DiscoveryOpenIdConnect discovery = fetchDiscoveryDocument(rootUri.toString() + WELL_KNOWN_PATH);
    validateDiscoveryDocument(discovery);

    enablePKCE = cfg.getBoolean(InitOAuth.ENABLE_PKCE, false);
    service =
        oauth20ServiceFactory.create(
            PROVIDER_NAME,
            new DiscoveryApi(discovery.getAuthorizationEndpoint(), discovery.getTokenEndpoint()),
            SCOPE);

    userinfoEndpoint = discovery.getUserinfoEndpoint();

    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);

    if (log.isDebugEnabled()) {
      log.debug("OAuth2: discovery issuer={}", discovery.getIssuer());
      log.debug("OAuth2: authorization endpoint={}", discovery.getAuthorizationEndpoint());
      log.debug("OAuth2: token endpoint={}", discovery.getTokenEndpoint());
      log.debug("OAuth2: userinfo endpoint={}", discovery.getUserinfoEndpoint());
    }
  }

  private URI validateRootUrl(String rootUrl) {
    return validateUrl(
        rootUrl,
        true,
        "Root URL must be configured",
        "Root URL is not a valid URL",
        "Root URL must be absolute URL",
        "Root URL must use http or https");
  }

  private void validateDiscoveryDocument(DiscoveryOpenIdConnect discovery) {
    if (discovery == null) {
      throw new ProvisionException("Discovery document is empty");
    }

    validateUrlField("issuer", discovery.getIssuer());
    validateUrlField("authorization_endpoint", discovery.getAuthorizationEndpoint());
    validateUrlField("token_endpoint", discovery.getTokenEndpoint());
    validateUrlField("userinfo_endpoint", discovery.getUserinfoEndpoint());
  }

  private void validateUrlField(String fieldName, String value) {
    validateUrl(
        value,
        false,
        "Discovery document missing required field: " + fieldName,
        "Discovery document field is not a valid URL: " + fieldName,
        "Discovery document field must be absolute URL: " + fieldName,
        "Discovery document field must use http or https: " + fieldName);
  }

  private static URI validateUrl(
      String value,
      boolean trimTrailingSlashes,
      String missingMessage,
      String invalidMessage,
      String absoluteMessage,
      String schemeMessage) {
    if (value == null || value.isBlank()) {
      throw new ProvisionException(missingMessage);
    }

    String normalizedValue = trimTrailingSlashes ? value.replaceAll("/+$", "") : value;

    URI uri;
    try {
      uri = URI.create(normalizedValue);
    } catch (IllegalArgumentException e) {
      throw new ProvisionException(invalidMessage, e);
    }

    if (!uri.isAbsolute()) {
      throw new ProvisionException(absoluteMessage);
    }

    if (uri.getScheme() == null
        || (!"http".equalsIgnoreCase(uri.getScheme())
            && !"https".equalsIgnoreCase(uri.getScheme()))) {
      throw new ProvisionException(schemeMessage);
    }

    return uri;
  }

  DiscoveryOpenIdConnect fetchDiscoveryDocument(String discoveryUrl) {
    HttpURLConnection connection = null;
    try {
      URL url = URI.create(discoveryUrl).toURL();
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      int responseCode = connection.getResponseCode();
      String responseBody;

      try (InputStream in =
          (responseCode >= 200 && responseCode < 300)
              ? connection.getInputStream()
              : connection.getErrorStream()) {
        responseBody =
            (in == null)
                ? ""
                : CharStreams.toString(new InputStreamReader(in, StandardCharsets.UTF_8));
      }

      if (responseCode != HttpServletResponse.SC_OK) {
        log.error(
            "Failed to fetch OIDC discovery from {}. Status: {}. Response: {}",
            discoveryUrl,
            responseCode,
            responseBody);
        throw new IOException("HTTP " + responseCode);
      }

      return JSON.newGson().fromJson(responseBody, DiscoveryOpenIdConnect.class);
    } catch (IOException e) {
      throw new ProvisionException(
          "Cannot fetch OpenID Connect discovery document: " + discoveryUrl, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuthRequest request = new OAuthRequest(Verb.GET, userinfoEndpoint);
    OAuth2AccessToken t = new OAuth2AccessToken(token.getToken(), token.getRaw());
    service.signRequest(t, request);

    JsonElement userJson = null;
    try (Response response = service.execute(request)) {
      if (response.getCode() != HttpServletResponse.SC_OK) {
        throw new IOException(
            String.format(
                "Status %s (%s) for request %s",
                response.getCode(), response.getBody(), request.getUrl()));
      }

      userJson = JSON.newGson().fromJson(response.getBody(), JsonElement.class);
      if (log.isDebugEnabled()) {
        log.debug("User info response: {}", response.getBody());
      }

      if (userJson != null && userJson.isJsonObject()) {
        JsonObject jsonObject = userJson.getAsJsonObject();
        JsonElement sub = jsonObject.get("sub");
        if (sub == null || sub.isJsonNull()) {
          throw new IOException("Response doesn't contain sub field");
        }

        JsonElement username = getPreferredValue(jsonObject, "preferred_username", "username");
        JsonElement email = jsonObject.get("email");
        JsonElement name = getPreferredValue(jsonObject, "name", "display_name");

        return new OAuthUserInfo(
            extIdScheme + ":" + sub.getAsString(),
            asString(username),
            asString(email),
            asString(name),
            null);
      }
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException("Cannot retrieve user info resource", e);
    }

    throw new IOException(String.format("Invalid JSON '%s': not a JSON Object", userJson));
  }

  private static JsonElement getPreferredValue(JsonObject obj, String... keys) {
    for (String key : keys) {
      JsonElement value = obj.get(key);
      if (value != null && !value.isJsonNull()) {
        return value;
      }
    }
    return null;
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier rv, @Nullable String codeVerifier) {
    try {
      AccessTokenRequestParams reqParams = AccessTokenRequestParams.create(rv.getValue());
      if (enablePKCE && codeVerifier != null) {
        reqParams.pkceCodeVerifier(codeVerifier);
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
  public OAuthAuthorizationInfo getAuthorizationInfo() {
    AuthorizationUrlBuilder builder = service.createAuthorizationUrlBuilder();
    String verifier = null;

    if (enablePKCE) {
      builder.initPKCE();
      verifier = builder.getPkce().getCodeVerifier();
    }

    return new OAuthAuthorizationInfo(builder.build(), verifier);
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
