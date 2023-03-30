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

package com.googlesource.gerrit.plugins.oauth;

import com.github.scribejava.core.builder.api.DefaultApi20;

public class TuleapApi extends DefaultApi20 {
  private static final String AUTHORIZE_URL = "%s/oauth2/authorize";
  private static final String ACCESS_TOKEN_URL = "%s/oauth2/token";

  private final String rootUrl;

  public TuleapApi(String rootUrl) {
    this.rootUrl = rootUrl;
  }

  @Override
  public String getAccessTokenEndpoint() {
    return String.format(ACCESS_TOKEN_URL, rootUrl);
  }

  @Override
  protected String getAuthorizationBaseUrl() {
    return String.format(AUTHORIZE_URL, rootUrl);
  }
}
