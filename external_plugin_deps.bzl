load("//tools/bzl:maven_jar.bzl", "maven_jar")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")

def external_plugin_deps(omit_commons_codec = True):
    http_file(
        name = "scribe",
        downloaded_file_path = "scribe.jar",
        sha256 = "7a7f01789e3276a89fb61b86a2acff3b7aadeb7ff02edd0d70fdf8e03808df2e",
        urls = [
            "https://github.com/davido/scribejava/releases/download/v1.3.8/scribe-1.3.8.jar",
        ],
    )
    if not omit_commons_codec:
        maven_jar(
            name = "commons-codec",
            artifact = "commons-codec:commons-codec:1.4",
            sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
        )
