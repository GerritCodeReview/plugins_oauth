load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:in_gerrit_tree.bzl",
    "in_gerrit_tree_enabled",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_allowlist.bzl",
    "runtime_jars_allowlist_test",
)
load(
    "@com_googlesource_gerrit_bazlets//tools:runtime_jars_overlap.bzl",
    "runtime_jars_overlap_test",
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
        "@oauth_plugin_deps//:com_fasterxml_jackson_core_jackson_databind",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_core",
        "@oauth_plugin_deps//:com_sap_cloud_security_env",
        "@oauth_plugin_deps//:com_sap_cloud_security_java_api",
        "@oauth_plugin_deps//:com_sap_cloud_security_java_security",
        "@oauth_plugin_deps//:com_sap_cloud_security_xsuaa_token_client",
    ],
)

gerrit_plugin_tests(
    name = "oauth_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["oauth"],
    deps = [
<<<<<<< PATCH SET (b258d294b3cc49b342b72584f1f36cf5a297f7b3 [SAP IAS] Add tests for SAP IAS)
        ":oauth__plugin_test_deps",
        "@scribejava-apis//jar",
        "@scribejava-core//jar",
        "@sap-java-security//jar",
        "@sap-java-api//jar",
||||||| BASE      (62e16441ea3b73c1fe17a70f2f1eb89f0e0b5088 Replace commons-codec Base64 with JDK Base64 for JWT decodin)
        ":oauth__plugin_test_deps",
        "@scribejava-apis//jar",
        "@scribejava-core//jar",
=======
        ":oauth__plugin",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_core",
>>>>>>> BASE      (775e886efab693987170bead272f86159ea3fd1a Discovery OAuth: Validate discovery config and add tests)
    ],
)

runtime_jars_allowlist_test(
    name = "check_oauth_third_party_runtime_jars",
    allowlist = ":oauth_third_party_runtime_jars.allowlist.txt",
    hint = ":check_oauth_third_party_runtime_jars_manifest",
    target = ":oauth__plugin",
)

runtime_jars_overlap_test(
    name = "oauth_no_overlap_with_gerrit",
    against = "//:headless.war.jars.txt",
    hint = "Exclude overlaps via maven.install(excluded_artifacts=[...]) and re-run this test.",
    target = ":oauth__plugin",
    target_compatible_with = in_gerrit_tree_enabled(),
)
