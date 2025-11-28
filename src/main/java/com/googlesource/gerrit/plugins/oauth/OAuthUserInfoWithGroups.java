package com.googlesource.gerrit.plugins.oauth;

import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import java.util.Set;

public class OAuthUserInfoWithGroups extends OAuthUserInfo {
    private final Set<String> groups;

    public OAuthUserInfoWithGroups(
        String externalId,
        String userName,
        String emailAddress,
        String displayName,
        String claimedIdentity,
        Set<String> groups) {
        super(externalId, userName, emailAddress, displayName, claimedIdentity);
        this.groups = groups;
    }

    public Set<String> getGroups() {
        return groups;
    }
}
