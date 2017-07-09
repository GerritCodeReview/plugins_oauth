load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
  maven_jar(
      name = "scribe",
      artifact = "org.scribe:scribe:1.3.7",
      sha1 = "583921bed46635d9f529ef5f14f7c9e83367bc6e",
   )

  # TODO(davido): Switch to use vanilla upstream gjf version when one of these PRs are merged:
  # https://github.com/google/google-java-format/pull/106
  # https://github.com/google/google-java-format/pull/154
  native.http_jar(
      name = "google_java_format",
      sha256 = "9fe87113a2cf27e827b72ce67c627b36d163d35d1b8a10a584e4ae024a15c854",
      url = "https://github.com/davido/google-java-format/releases/download/1.3-1-gec5ce10/google-java-format-1.3-1-gec5ce10-all-deps.jar",
  )
