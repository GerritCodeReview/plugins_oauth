package com.googlesource.gerrit.plugins.oauth;

import com.github.scribejava.apis.microsoftazureactivedirectory.BaseMicrosoftAzureActiveDirectoryApi;

public class SovereignMicrosoftAzureApi extends BaseMicrosoftAzureActiveDirectoryApi {

    private static final String OAUTH_2 = "/oauth2";
    private final String loginUrl;
    private final String tenant;

    public SovereignMicrosoftAzureApi(String tenant, String loginUrl) {
        super(tenant);
        this.loginUrl = loginUrl;
        this.tenant = tenant;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return loginUrl + tenant + OAUTH_2 + getEndpointVersionPath() + "/token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return loginUrl + tenant + OAUTH_2 + getEndpointVersionPath() + "/authorize";
    }

    @Override
    protected String getEndpointVersionPath() {
        return "/v2.0";
    }
}
