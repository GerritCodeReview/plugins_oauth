load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_ext_test_deps",
    "gerrit_plugin_tests",
)
load("@rules_java//java:defs.bzl", "java_library")

package_group(
    name = "visibility",
    packages = ["//plugins/oauth/..."],
)

PLUGIN = "oauth"

EXT_DEPS = [
    "com.nimbusds:nimbus-jose-jwt",
]

SAPIAS_EXT_DEPS = [
    "com.sap.cloud.security.java:api",
    "com.sap.cloud.security.java:security",
    "com.sap.cloud.security:env",
    "com.sap.cloud.security.xsuaa:token-client",
]

# Providers bundled in the default oauth.jar. SAP IAS ships only as the
# oauth-sapias artifact, so it is not listed here.
PROVIDER_LIBS = [
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/airvantage",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/azure",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/bitbucket",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/cas",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/dex",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/discovery",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/facebook",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/github",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/gitlab",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/google",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/keycloak",
    "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/phabricator",
]

# Shared-core libraries every OAuth artifact bundles, aggregated so each
# provider depends on a single label instead of re-listing the four.
java_library(
    name = "core",
    visibility = [":visibility"],
    exports = [
        "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/base",
        "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/client",
        "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/jwt",
        "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/utils",
    ],
)

# Shared plugin resources, exposed so the single-provider artifacts in each
# provider package can bundle them too.
filegroup(
    name = "oauth_resources",
    srcs = glob(["src/main/resources/**/*"]),
    visibility = [":visibility"],
)

gerrit_plugin(
    srcs = glob(["src/main/java/**/*.java"]),
    ext_deps = EXT_DEPS,
    manifest_entries = [
        "Gerrit-PluginName: gerrit-oauth-provider",
        "Gerrit-Module: com.googlesource.gerrit.plugins.oauth.Module",
        "Gerrit-InitStep: com.googlesource.gerrit.plugins.oauth.InitOAuth",
        "Implementation-Title: Gerrit OAuth authentication provider",
        "Implementation-URL: https://github.com/davido/gerrit-oauth-provider",
    ],
    plugin = PLUGIN,
    resources = [":oauth_resources"],
    deps = [":core"] + PROVIDER_LIBS,
)

# Short labels for the single-provider artifacts, which are defined in each
# provider package. Keeps the public plugins/oauth:oauth-<provider> targets.
[
    alias(
        name = "oauth-" + provider,
        actual = "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/" + provider + ":oauth-" + provider,
        visibility = ["//visibility:public"],
    )
    for provider in [
        "airvantage",
        "azure",
        "bitbucket",
        "cas",
        "dex",
        "discovery",
        "facebook",
        "github",
        "gitlab",
        "google",
        "keycloak",
        "phabricator",
        "sapias",
    ]
]

PROVIDERS_TEST_SRCS = [
    "src/test/java/com/googlesource/gerrit/plugins/oauth/airvantage/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/azure/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/bitbucket/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/cas/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/dex/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/discovery/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/facebook/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/github/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/gitlab/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/google/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/keycloak/**/*.java",
    "src/test/java/com/googlesource/gerrit/plugins/oauth/phabricator/**/*.java",
]

SAPIAS_TEST_SRCS = "src/test/java/com/googlesource/gerrit/plugins/oauth/sapias/**/*.java"

gerrit_plugin_ext_test_deps(
    name = "providers_test_deps",
    ext_deps = ["com.nimbusds:nimbus-jose-jwt"],
    plugin = PLUGIN,
)

gerrit_plugin_tests(
    name = "providers_tests",
    srcs = glob(PROVIDERS_TEST_SRCS),
    deps = [
        ":core",
        ":providers_test_deps",
    ] + PROVIDER_LIBS,
)

gerrit_plugin_tests(
    name = "sapias_tests",
    srcs = glob([SAPIAS_TEST_SRCS]),
    ext_deps = SAPIAS_EXT_DEPS,
    plugin = PLUGIN,
    deps = [
        ":core",
        "//plugins/oauth/src/main/java/com/googlesource/gerrit/plugins/oauth/sapias",
    ],
)

gerrit_plugin_tests(
    name = "oauth_plugin_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = PROVIDERS_TEST_SRCS + [SAPIAS_TEST_SRCS],
    ),
    ext_deps = EXT_DEPS,
    plugin = PLUGIN,
    deps = [":core"] + PROVIDER_LIBS,
)
