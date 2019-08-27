import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI

val kotlinVersion = "1.2.70"

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow").version("2.0.4")
}

dependencies {
    implementation("com.atlassian.performance.tools:virtual-users:[3.10.0,4.0.0)")
}

tasks.getByName("shadowJar", ShadowJar::class).apply {
    manifest.attributes["Main-Class"] = "com.atlassian.performance.tools.virtualusers.api.EntryPointKt"
}

configurations.all {
    resolutionStrategy {
        activateDependencyLocking()
        failOnVersionConflict()
        eachDependency {
            when (requested.module.toString()) {
                "commons-codec:commons-codec" -> useVersion("1.10")
                "com.google.code.gson:gson" -> useVersion("2.8.2")
                "org.slf4j:slf4j-api" -> useVersion("1.8.0-alpha2")
            }
            when (requested.group) {
                "org.jetbrains.kotlin" -> useVersion(kotlinVersion)
                "org.apache.logging.log4j" -> useVersion("2.12.0")
            }
        }
    }
}

repositories {
    mavenLocal()
    maven(url = URI("https://packages.atlassian.com/maven-external/"))
}
