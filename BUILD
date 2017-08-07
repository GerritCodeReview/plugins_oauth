load("//tools/bzl:plugin.bzl", "gerrit_plugin", "PLUGIN_DEPS_NEVERLINK")

config_setting(
    name = "2_14_1",
    values = {
        "define": "api_2_14_1=1",
    },
)

config_setting(
    name = "2_14_2",
    values = {
        "define": "api_2_14_2=1",
    },
)

plugin_deps_neverlink_tmpl = "//external:gerrit-plugin-api-neverlink_%s"

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
    plugin_deps_neverlink = select({
        ":2_14_1": [plugin_deps_neverlink_tmpl % "2.14.1"],
        ":2_14_2": [plugin_deps_neverlink_tmpl % "2.14.2"],
        "//conditions:default": PLUGIN_DEPS_NEVERLINK,
    }),
    provided_deps = [
        "@commons_codec//jar:neverlink",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        "@scribe//jar",
    ],
)
