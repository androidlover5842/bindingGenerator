fun properties(key: String) = providers.gradleProperty(key)

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "com.bindingGenerator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

intellij {
    version=properties("platformVersion")
    type=properties("platformType")
    updateSinceUntilBuild.set(false)

    plugins.set(listOf("org.jetbrains.kotlin"))
}

tasks {
    patchPluginXml {
        version.set(properties("platformVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
