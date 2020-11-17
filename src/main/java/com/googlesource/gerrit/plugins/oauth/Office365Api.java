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

package com.googlesource.gerrit.plugins.oauth;

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication;
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme;

/**
 * Due to {@link MicrosoftAzureActiveDirectory20Api} uses the default tenant <b>common</b> we are extending this
 * so we can set the default tenant to <b>organizations</b>
 */
public class Office365Api extends MicrosoftAzureActiveDirectory20Api {
  public static final String DEFAULT_TENANT = "organizations";

  public Office365Api() {
    this(DEFAULT_TENANT);
  }

  public Office365Api(String tenant) {
    super(tenant);
  }

}
