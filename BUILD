load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)

gerrit_plugin(
    name = "oauth",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: gerrit-oauth-provider",
        "Gerrit-Module: com.googlesource.gerrit.plugins.oauth.Module",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.oauth.HttpModule",
        "Gerrit-InitStep: com.googlesource.gerrit.plugins.oauth.InitOAuth",
        "Implementation-Title: Gerrit OAuth authentication provider",
        "Implementation-URL: https://github.com/davido/gerrit-oauth-provider",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@external_plugin_deps//:com_fasterxml_jackson_core_jackson_databind",
        "@external_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@external_plugin_deps//:com_github_scribejava_scribejava_core",
        "@external_plugin_deps//:com_sap_cloud_security_env",
        "@external_plugin_deps//:com_sap_cloud_security_java_api",
        "@external_plugin_deps//:com_sap_cloud_security_java_security",
        "@external_plugin_deps//:com_sap_cloud_security_xsuaa_token_client",
    ],
)

gerrit_plugin_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
        ":oauth__plugin",
        "@external_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@external_plugin_deps//:com_github_scribejava_scribejava_core",
    ],
)
