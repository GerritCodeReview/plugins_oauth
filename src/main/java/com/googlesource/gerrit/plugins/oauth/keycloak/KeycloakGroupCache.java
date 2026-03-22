// Copyright (C) 2025 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.oauth.keycloak;

import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.oauth.GroupCache;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KeycloakGroupCache implements GroupCache {
  private static final Logger log = LoggerFactory.getLogger(KeycloakGroupCache.class);

  private final ConcurrentMap<String, Set<String>> groupsByExternalId = new ConcurrentHashMap<>();
  private final Set<String> allGroups = ConcurrentHashMap.newKeySet();

  @Override
  public void put(String externalId, Set<String> groups) {
    if (log.isDebugEnabled()) {
      log.debug("Caching groups {} for user with externalId {}", groups, externalId);
    }
    groupsByExternalId.put(externalId, groups);
    allGroups.addAll(groups);
  }

  @Override
  public Set<String> get(String externalId) {
    Set<String> result = groupsByExternalId.getOrDefault(externalId, Collections.emptySet());
    if (log.isDebugEnabled()) {
      log.debug("Retrieved groups {} for user with externalId {}", result, externalId);
    }
    return result;
  }

  @Override
  public Set<String> getAllGroups() {
    return new HashSet<>(allGroups);
  }
}
