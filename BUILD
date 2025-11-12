load("@rules_java//java:defs.bzl", "java_library")
load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
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
        "@maven_deps//commons-codec/jar:neverlink",
        "@maven_deps//jackson-core/jar",
        "@maven_deps//jackson-databind/jar",
        "@maven_deps//json/jar",
        "@maven_deps//sap-env/jar",
        "@maven_deps//sap-java-api/jar",
        "@maven_deps//sap-java-security/jar",
        "@maven_deps//sap-xsuaa-token-client/jar",
        "@maven_deps//scribejava-apis/jar",
        "@maven_deps//scribejava-core/jar",
    ],
)

junit_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
        ":oauth__plugin_test_deps",
        "@maven_deps//scribejava-apis/jar",
        "@maven_deps//scribejava-core/jar",
    ],
)

java_library(
    name = "oauth__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":oauth__plugin",
    ],
)
