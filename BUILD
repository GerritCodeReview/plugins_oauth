load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin_tests_with_ext_deps",
    "gerrit_plugin_with_ext_deps",
)

EXT_DEPS = [
    "com.github.scribejava:scribejava-apis",
    "com.github.scribejava:scribejava-core",
    "com.sap.cloud.security.java:api",
    "com.sap.cloud.security.java:security",
]

gerrit_plugin_with_ext_deps(
    srcs = glob(["src/main/java/**/*.java"]),
    ext_deps = [
        "com.fasterxml.jackson.core:jackson-databind",
        "com.sap.cloud.security:env",
        "com.sap.cloud.security.xsuaa:token-client",
    ] + EXT_DEPS,
    manifest_entries = [
        "Gerrit-PluginName: gerrit-oauth-provider",
        "Gerrit-Module: com.googlesource.gerrit.plugins.oauth.Module",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.oauth.HttpModule",
        "Gerrit-InitStep: com.googlesource.gerrit.plugins.oauth.InitOAuth",
        "Implementation-Title: Gerrit OAuth authentication provider",
        "Implementation-URL: https://github.com/davido/gerrit-oauth-provider",
    ],
    plugin = "oauth",
    resources = glob(["src/main/resources/**/*"]),
)

gerrit_plugin_tests_with_ext_deps(
    srcs = glob(["src/test/java/**/*.java"]),
    ext_deps = EXT_DEPS,
    plugin = "oauth",
)
