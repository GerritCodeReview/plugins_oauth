workspace(name = "com_github_davido_gerrit_oauth_provider")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    # commit = "e15ad03897f040435d6c5e808b697b1125b964c1",
    local_path = "/home/davido/projects/bazlets",
)

# Multiversion Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api_multiversion.bzl",
    "gerrit_api_multiversion",
)

gerrit_api_multiversion()

load(":external_plugin_deps.bzl", "external_plugin_deps")
external_plugin_deps(omit_commons_codec = False)
