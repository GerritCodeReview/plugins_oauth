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

package com.googlesource.gerrit.plugins.oauth.auth0;

import static com.google.gerrit.json.OutputFormat.JSON;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.asString;
import static com.googlesource.gerrit.plugins.oauth.JsonUtil.isNull;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.base.CharMatcher;
import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.auth.oauth.OAuthVerifier;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.InitOAuth;
import com.googlesource.gerrit.plugins.oauth.OAuthPluginConfigFactory;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderConfig;
import com.googlesource.gerrit.plugins.oauth.OAuthServiceProviderExternalIdScheme;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@OAuthServiceProviderConfig(name = Auth0OAuthService.PROVIDER_NAME)
public class Auth0OAuthService implements OAuthServiceProvider {
  private static final Logger log = LoggerFactory.getLogger(Auth0OAuthService.class);
  public static final String PROVIDER_NAME = "auth0";
  private static final String PROTECTED_RESOURCE_URL = "%s/userinfo";
  private final OAuth20Service service;
  private final String serviceName;
  private final String rootUrl;
  private final String extIdScheme;

  @Inject
  Auth0OAuthService(
      OAuthPluginConfigFactory cfgFactory, @CanonicalWebUrl Provider<String> urlProvider) {
    PluginConfig cfg = cfgFactory.create(PROVIDER_NAME);
    String canonicalWebUrl = CharMatcher.is('/').trimTrailingFrom(urlProvider.get()) + "/";

    rootUrl = cfg.getString(InitOAuth.ROOT_URL);
    if (!URI.create(rootUrl).isAbsolute()) {
      throw new ProvisionException("Root URL must be absolute URL");
    }
    serviceName = cfg.getString(InitOAuth.SERVICE_NAME, "Auth0");

    service =
        new ServiceBuilder(cfg.getString(InitOAuth.CLIENT_ID))
            .apiSecret(cfg.getString(InitOAuth.CLIENT_SECRET))
            .callback(canonicalWebUrl + "oauth")
            .defaultScope("openid profile email")
            .build(new Auth0Api(rootUrl));
    extIdScheme = OAuthServiceProviderExternalIdScheme.create(PROVIDER_NAME);
  }

  @Override
  public OAuthUserInfo getUserInfo(OAuthToken token) throws IOException {
    OAuthRequest request =
        new OAuthRequest(Verb.GET, String.format(PROTECTED_RESOURCE_URL, rootUrl));
    OAuth2AccessToken t = new OAuth2AccessToken(token.getToken(), token.getRaw());
    service.signRequest(t, request);

    try (Response response = service.execute(request)) {
      if (response.getCode() != HttpServletResponse.SC_OK) {
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
      if (isNull(jsonObject)) {
        throw new IOException("Response doesn't contain 'user' field" + jsonObject);
      }
      JsonElement id = jsonObject.get("sub");
      JsonElement username = jsonObject.get("preferred_username");
      JsonElement email = jsonObject.get("email");
      JsonElement name = jsonObject.get("name");
      return new OAuthUserInfo(
          extIdScheme + ":" + id.getAsString(),
          asString(username),
          asString(email),
          asString(name),
          id.getAsString());
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException("Cannot retrieve user info resource", e);
    }
  }

  @Override
  public OAuthToken getAccessToken(OAuthVerifier verifier) {
    try {
      OAuth2AccessToken accessToken = service.getAccessToken(verifier.getValue());
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
    return service.getAuthorizationUrl();
  }

  @Override
  public String getVersion() {
    return service.getVersion();
  }

  @Override
  public String getName() {
    return serviceName;
  }
}
