load("@rules_java//java:defs.bzl", "java_library", "java_import")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)

java_import(
    name = "scribe",
    jars = ["@scribe//file"],
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
        ":scribe",
        "@commons-codec//jar:neverlink",
    ],
)

junit_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
        ":oauth__plugin_test_deps",
    ],
)

java_library(
    name = "oauth__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":oauth__plugin",
        "@scribe//jar",
    ],
)
