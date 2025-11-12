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
        "@maven//:com_fasterxml_jackson_core_jackson_core",
        "@maven//:com_fasterxml_jackson_core_jackson_databind",
        "@maven//:org_json_json",
        "@maven//:com_sap_cloud_security_env",
        "@maven//:com_sap_cloud_security_java_api",
        "@maven//:com_sap_cloud_security_java_security",
        "@maven//:com_sap_cloud_security_xsuaa_token_client",
        "@maven//:com_github_scribejava_scribejava_apis",
        "@maven//:com_github_scribejava_scribejava_core",
    ],
)

junit_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
        ":oauth__plugin_test_deps",
        "@maven//:com_github_scribejava_scribejava_apis",
        "@maven//:com_github_scribejava_scribejava_core",
    ],
)

java_library(
    name = "oauth__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        ":oauth__plugin",
        "@maven//:com_google_gerrit_gerrit_acceptance_framework",
        "@maven//:com_google_gerrit_gerrit_plugin_api",
    ],
)

java_library(
    name = "commons-codec-neverlink",
    neverlink = 1,
    exports = ["@maven//:commons_codec_commons_codec"],
)
