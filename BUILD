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
        ":oauth__plugin",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_apis",
        "@oauth_plugin_deps//:com_github_scribejava_scribejava_core",
<<<<<<< PATCH SET (f6511c37396dab74380b1848482aad7a1f1decc7 [SAP IAS] Add tests for SAP IAS)
        "@oauth_plugin_deps//:com_sap_cloud_security_env",
        "@oauth_plugin_deps//:com_sap_cloud_security_java_api",
        "@oauth_plugin_deps//:com_sap_cloud_security_java_security",
        "@oauth_plugin_deps//:com_sap_cloud_security_xsuaa_token_client",
||||||| BASE      (775e886efab693987170bead272f86159ea3fd1a Discovery OAuth: Validate discovery config and add tests)
=======
        "@oauth_plugin_deps//:com_sap_cloud_security_java_api",
        "@oauth_plugin_deps//:com_sap_cloud_security_java_security",
>>>>>>> BASE      (f255dcc44146a48eb9121133a46dd14cf1b1aeb9 [SAP IAS] Fix thread-safety race condition in PKCE handling)
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
