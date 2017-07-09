load("//tools/bzl:plugin.bzl", "gerrit_plugin")

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
        "@commons_codec//jar:neverlink",
        "@scribe//jar",
    ],
)

java_binary(
    name = "gjf-binary",
    main_class = "com.google.googlejavaformat.java.Main",
    visibility = ["//visibility:public"],
    runtime_deps = ["@google_java_format//jar"],
)

genrule(
    name = "gjf-check-invocation",
    srcs = glob(["src/main/java/**/*.java"]),
    outs = ["check-java-format.log"],
    cmd = "find . -name \"*.java\" | xargs $(location :gjf-binary) --dry-run > $@",
    tools = [":gjf-binary"],
)

sh_test(
    name = "gjf-check",
    srcs = [":gjf-check-invocation"],
    tags = ["oauth"],
)
