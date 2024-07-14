load("//tools/bzl:maven_jar.bzl", "maven_jar")

def external_plugin_deps():
    JACKSON_VERS = "2.21.1"
    SCRIBEJAVA_VERS = "8.3.3"
    SAP_SECURITY_VERS = "3.6.0"
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
        artifact = "com.fasterxml.jackson.core:jackson-annotations:2.21",
        sha1 = "b1bc1868bf02dc0bd6c7836257a036a331005309",
    )
    maven_jar(
        name = "jackson-databind",
        artifact = "com.fasterxml.jackson.core:jackson-databind:" + JACKSON_VERS,
        sha1 = "5615fb77652bfd386d87b95a1d663e1c0e38b372",
        deps = [
            "@jackson-annotations//jar",
        ],
    )
    maven_jar(
        name = "jackson-core",
        artifact = "com.fasterxml.jackson.core:jackson-core:" + JACKSON_VERS,
        sha1 = "47b013fc85dbb819f3ba51e95a5560d0f1c4121c",
    )
    maven_jar(
        name = "sap-java-security",
        artifact = "com.sap.cloud.security:java-security:" + SAP_SECURITY_VERS,
        sha1 = "6e6bef72b84110538b1d72d93c6e4b94988e7113",
    )
    maven_jar(
        name = "sap-java-api",
        artifact = "com.sap.cloud.security:java-api:" + SAP_SECURITY_VERS,
        sha1 = "a954bcf647f3e8ed7fb980bc55f08788f9b984e9",
    )
    maven_jar(
        name = "sap-env",
        artifact = "com.sap.cloud.security:env:" + SAP_SECURITY_VERS,
        sha1 = "b0d84d8a7d0a73fef9329941226e725d0de00322",
    )
    maven_jar(
        name = "sap-xsuaa-token-client",
        artifact = "com.sap.cloud.security.xsuaa:token-client:" + SAP_SECURITY_VERS,
        sha1 = "dea8c6f5cb7d02c014dd19006a249df376bcbf38",
    )
    maven_jar(
        name = "json",
        artifact = "org.json:json:20250517",
        sha1 = "d67181bbd819ccceb929b580a4e2fcb0c8b17cd8",
    )
