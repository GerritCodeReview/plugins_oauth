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

import org.scribe.builder.api.DefaultApi20;
import org.scribe.model.OAuthConfig;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;
import org.scribe.utils.OAuthEncoder;

public class CasApi extends DefaultApi20 {
  private static final String AUTHORIZE_URL =
      "%s/oauth2.0/authorize?response_type=code&client_id=%s&redirect_uri=%s";

  private final String rootUrl;

  public CasApi(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  @Override
  public String getAccessTokenEndpoint() {
    return String.format("%s/oauth2.0/accessToken", rootUrl);
  }

  @Override
  public String getAuthorizationUrl(OAuthConfig config) {
    return String.format(
        AUTHORIZE_URL, rootUrl, config.getApiKey(), OAuthEncoder.encode(config.getCallback()));
  }

  @Override
  public Verb getAccessTokenVerb() {
    return Verb.POST;
  }

  @Override
  public OAuthService createService(OAuthConfig config) {
    return new OAuth20ServiceImpl(this, config);
  }
}
