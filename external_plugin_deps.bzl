load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps(omit_commons_codec = True):
    maven_jar(
        name = "scribe",
        artifact = "org.scribe:scribe:1.3.7",
        sha1 = "583921bed46635d9f529ef5f14f7c9e83367bc6e",
    )
    maven_jar(
        name = "mockito",
        artifact = "org.mockito:mockito-core:2.13.0",
        sha1 = "8e372943974e4a121fb8617baced8ebfe46d54f0",
        deps = [
          '@byte-buddy//jar',
          '@objenesis//jar',
        ],
    )
    maven_jar(
        name = "byte-buddy",
        artifact = "net.bytebuddy:byte-buddy:1.7.9",
        sha1 = "51218a01a882c04d0aba8c028179cce488bbcb58",
    )
    maven_jar(
        name = "objenesis",
        artifact = "org.objenesis:objenesis:2.6",
        sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
    )
    if not omit_commons_codec:
        maven_jar(
            name = "commons-codec",
            artifact = "commons-codec:commons-codec:1.4",
            sha1 = "4216af16d38465bbab0f3dff8efa14204f7a399a",
        )
