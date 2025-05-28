// Copyright (C) 2020 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.cas;

import static com.google.common.truth.Truth.assertThat;

import com.github.scribejava.core.extractors.OAuth2AccessTokenExtractor;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import org.junit.Test;

public class CasApiTest {

  @Test
  public void testAccessTokenExtractor() {
    CasApi api = new CasApi("", false);
    assertThat(api.getAccessTokenExtractor()).isInstanceOf(OAuth2AccessTokenExtractor.class);
  }

  @Test
  public void testJsonAccessTokenExtractor() {
    CasApi api = new CasApi("", true);
    assertThat(api.getAccessTokenExtractor()).isInstanceOf(OAuth2AccessTokenJsonExtractor.class);
  }
}
