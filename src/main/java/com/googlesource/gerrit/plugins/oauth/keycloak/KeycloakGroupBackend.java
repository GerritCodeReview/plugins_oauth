package com.googlesource.gerrit.plugins.oauth.keycloak;

import static com.google.gerrit.server.account.GroupBackends.GROUP_REF_NAME_COMPARATOR;

import com.google.common.collect.Sets;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KeycloakGroupBackend implements GroupBackend {
  private static final String KEYCLOAK_UUID_PREFIX = "ldap/";
  private static final Logger log = LoggerFactory.getLogger(KeycloakGroupBackend.class);

  private final KeycloakGroupCache keycloakGroupCache;

  @Inject
  public KeycloakGroupBackend(KeycloakGroupCache keycloakGroupCache) {
    this.keycloakGroupCache = keycloakGroupCache;
  }

  private boolean isKeycloakUUID(AccountGroup.UUID uuid) {
    return uuid.get().startsWith(KEYCLOAK_UUID_PREFIX);
  }

  @Override
  public boolean handles(AccountGroup.UUID uuid) {
    return isKeycloakUUID(uuid);
  }

  @Override
  public GroupDescription.Basic get(AccountGroup.UUID uuid) {
    if (!handles(uuid)) {
      return null; 
    }
    String groupName = uuid.get().substring(KEYCLOAK_UUID_PREFIX.length());
    return new GroupDescription.Basic() {
      @Override
      public AccountGroup.UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return KEYCLOAK_UUID_PREFIX + groupName;
      }

      @Override
      public String getEmailAddress() {
        return null;
      }

      @Override public String getUrl() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    AccountGroup.UUID uuid = AccountGroup.uuid(name);
    if (isKeycloakUUID(uuid)) {
      GroupDescription.Basic g = get(uuid);
      if (g == null) {
        return Collections.emptySet();
      }
      return Collections.singleton(GroupReference.forGroup(g));
    } else if (name.startsWith(KEYCLOAK_UUID_PREFIX)) {
      return suggestKeycloak(name.substring(KEYCLOAK_UUID_PREFIX.length()));
    } else {
      return suggestKeycloak(name);
    }
  }

  @Override
  public GroupMembership membershipsOf(CurrentUser user) {
    if (!user.isIdentifiedUser()) {
        log.warn("CurrentUser {} is not IdentifiedUser, returning empty membership", user);
        return GroupMembership.EMPTY;
    }
    String extId = user.asIdentifiedUser()
                      .getExternalIdKeys()
                      .stream()
                      .findFirst()
                      .map(Object::toString)
                      .orElse(null);
    if (log.isDebugEnabled()) {
      log.debug("Checking memberships for user {} with extId={}", user.getUserName(), extId);
    }
    Set<AccountGroup.UUID> groupUuids = keycloakGroupCache.get(extId)
        .stream()
        .map(group -> AccountGroup.uuid(KEYCLOAK_UUID_PREFIX + group))
        .collect(Collectors.toSet());
    if (log.isDebugEnabled()) {
      log.debug("User {} mapped to {} group UUIDs: {}", user.getUserName(), groupUuids.size(), groupUuids);
    }
    return new ListGroupMembership(groupUuids);
  }

  @Override
  public boolean isVisibleToAll(AccountGroup.UUID uuid) {
    return handles(uuid);
  }

  private Set<GroupReference> suggestKeycloak(String partialName) {
    if (partialName.isEmpty()) {
      return Collections.emptySet();
    }
    Set<GroupReference> matches = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    Set<String> allGroups = keycloakGroupCache.getAllGroups();
    String lowercasePartialName = partialName.toLowerCase();
    for (String groupName : allGroups) {
      if (groupName.toLowerCase().contains(lowercasePartialName)) {
        AccountGroup.UUID groupUuid = AccountGroup.uuid(KEYCLOAK_UUID_PREFIX + groupName);
        GroupDescription.Basic groupDescription = get(groupUuid);
        if (groupDescription != null) {
          matches.add(GroupReference.forGroup(groupDescription));
        }
      }
    }
    return matches;
  }
}
