package com.googlesource.gerrit.plugins.oauth.keycloak;

import com.googlesource.gerrit.plugins.oauth.GroupCache;
import com.google.inject.Singleton;
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
