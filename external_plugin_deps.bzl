load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps(omit_commons_codec = True):
    JACKSON_VERS = "2.14.0"
    SCRIBEJAVA_VERS = "8.3.3"
    maven_jar(
        name = "scribejava-core",
        artifact = "com.github.scribejava:scribejava-core:" + SCRIBEJAVA_VERS,
        sha1 = "4a9b4ed9a6367f95945eb30382a1cc647b390bd5",
        deps = [
            "@scribejava-java8//jar",
        ],
    )
    maven_jar(
        name = "scribejava-apis",
        artifact = "com.github.scribejava:scribejava-apis:" + SCRIBEJAVA_VERS,
        sha1 = "0298af304ea01e420110b9d6880f2bef9c41bc8b",
    )
    maven_jar(
        name = "scribejava-java8",
        artifact = "com.github.scribejava:scribejava-java8:" + SCRIBEJAVA_VERS,
        sha1 = "32e216f872c4ff6b3a20627d1708794db5761bc0",
    )
    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VERS,
        sha1 = "fb7afb3c9c8ea363a9c88ea9c0a7177cf2fbd369",
    )
    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VERS,
        sha1 = "513b8ca3fea0352ceebe4d0bbeea527ab343dc1a",
        deps = [
            "@jackson-annotations//jar",
        ],
    )
    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERS,
        sha1 = "49d219171d6af643e061e9e1baaaf6a6a067918d",
    )
    if not omit_commons_codec:
        maven_jar(
            name = "commons-codec",
            artifact = "commons-codec:commons-codec:1.4",
            sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
        )
