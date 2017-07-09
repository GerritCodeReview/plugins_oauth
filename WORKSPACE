workspace(name = "com_github_davido_gerrit_oauth_provider")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "0f87babe07a555425d829c6e7951e296e9e24579",
    #    local_path = "/home/<user>/projects/bazlets",
)

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

gerrit_api()

load("@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl", "maven_jar")

maven_jar(
    name = "scribe",
    artifact = "org.scribe:scribe:1.3.7",
    sha1 = "583921bed46635d9f529ef5f14f7c9e83367bc6e",
)

maven_jar(
    name = "commons_codec",
    artifact = "commons-codec:commons-codec:1.4",
    sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
)

# TODO(davido): Switch to use vanilla upstream gjf version when one of these PRs are merged:
# https://github.com/google/google-java-format/pull/106
# https://github.com/google/google-java-format/pull/154
http_jar(
    name = "google_java_format",
    sha256 = "9fe87113a2cf27e827b72ce67c627b36d163d35d1b8a10a584e4ae024a15c854",
    url = "https://github.com/davido/google-java-format/releases/download/1.3-1-gec5ce10/google-java-format-1.3-1-gec5ce10-all-deps.jar",
)
