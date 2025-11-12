load("@rules_java//java:defs.bzl", "java_library")
load("@com_googlesource_gerrit_bazlets//tools:junit.bzl", "junit_tests")
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
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
        ":commons-codec-neverlink",
        "@gerrit_plugin_deps//:com_fasterxml_jackson_core_jackson_core",
        "@gerrit_plugin_deps//:com_fasterxml_jackson_core_jackson_databind",
        "@gerrit_plugin_deps//:org_json_json",
        "@gerrit_plugin_deps//:com_sap_cloud_security_env",
        "@gerrit_plugin_deps//:com_sap_cloud_security_java_api",
        "@gerrit_plugin_deps//:com_sap_cloud_security_java_security",
        "@gerrit_plugin_deps//:com_sap_cloud_security_xsuaa_token_client",
        "@gerrit_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@gerrit_plugin_deps//:com_github_scribejava_scribejava_core",
    ],
)

junit_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
        ":oauth__plugin_test_deps",
        "@gerrit_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@gerrit_plugin_deps//:com_github_scribejava_scribejava_core",
    ],
)

java_library(
    name = "oauth__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        ":oauth__plugin",
        "@gerrit_plugin_deps//:com_google_gerrit_gerrit_acceptance_framework",
        "@gerrit_plugin_deps//:com_google_gerrit_gerrit_plugin_api",
    ],
)

java_library(
    name = "commons-codec-neverlink",
    neverlink = 1,
    exports = ["@gerrit_plugin_deps//:commons_codec_commons_codec"],
)
