package com.googlesource.gerrit.plugins.oauth;

import java.util.Set;

public interface GroupCache {
  void put(String externalId, Set<String> groups);
  Set<String> get(String externalId);
  Set<String> getAllGroups();
}
