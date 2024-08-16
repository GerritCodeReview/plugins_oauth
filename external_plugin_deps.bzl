load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps(omit_commons_codec = True):
    JACKSON_VERS = "2.10.2"
    SCRIBEJAVA_VERS = "6.9.0"
    JAVAJWT_VERS = "4.4.0"
    JWKRSA_VERS = "0.22.1"
    maven_jar(
        name = "scribejava-core",
        artifact = "com.github.scribejava:scribejava-core:" + SCRIBEJAVA_VERS,
        sha1 = "ed761f450d8382f75787e8fee9ae52e7ec768747",
    )
    maven_jar(
        name = "scribejava-apis",
        artifact = "com.github.scribejava:scribejava-apis:" + SCRIBEJAVA_VERS,
        sha1 = "a374c7a36533e58e53b42b584a8b3751ab1e13c4",
    )
    maven_jar(
        name = "jackson-annotations",
        artifact = "com.fasterxml.jackson.core:jackson-annotations:" + JACKSON_VERS,
        sha1 = "3a13b6105946541b8d4181a0506355b5fae63260",
    )
    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VERS,
        sha1 = "0528de95f198afafbcfb0c09d2e43b6e0ea663ec",
        deps = [
            "@jackson-annotations//jar",
        ],
    )
    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERS,
        sha1 = "73d4322a6bda684f676a2b5fe918361c4e5c7cca",
    )
    maven_jar(
        name = "java-jwt",
        artifact = "com.auth0:java-jwt:" + JAVAJWT_VERS,
        sha1 = "0e02407d19971bfa241441212901dd327a37722b"

    )
    maven_jar(
        name = "jwks-rsa",
        artifact = "com.auth0:jwks-rsa:" + JWKRSA_VERS,
        sha1 = "6da617499e4614b5c22a52cb142dfe376a9a4f00"
)
    if not omit_commons_codec:
        maven_jar(
            name = "commons-codec",
            artifact = "commons-codec:commons-codec:1.4",
            sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
        )
