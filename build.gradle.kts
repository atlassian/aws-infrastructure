val kotlinVersion = "1.2.70"

plugins {
    kotlin("jvm").version("1.2.70")
    `java-library`
    id("com.atlassian.performance.tools.gradle-release").version("0.4.3")
}

configurations.all {
    resolutionStrategy {
        activateDependencyLocking()
        failOnVersionConflict()
        eachDependency {
            when (requested.module.toString()) {
                "com.google.guava:guava" -> useVersion("23.6-jre")
                "org.apache.httpcomponents:httpclient" -> useVersion("4.5.5")
                "com.fasterxml.jackson.core:jackson-core" -> useVersion("2.9.4")
                "org.slf4j:slf4j-api" -> useVersion("1.8.0-alpha2")
                "org.apache.httpcomponents:httpcore" -> useVersion("4.4.9")
                "commons-logging:commons-logging" -> useVersion("1.2")
                "org.codehaus.plexus:plexus-utils" -> useVersion("3.1.0")
                "com.google.code.gson:gson" -> useVersion("2.8.2")
                "org.jsoup:jsoup" -> useVersion("1.10.2")
                "com.jcraft:jzlib" -> useVersion("1.1.3")
            }
            when (requested.group) {
                "org.jetbrains.kotlin" -> useVersion(kotlinVersion)
            }
        }
    }
}

dependencies {
    api("com.atlassian.performance.tools:infrastructure:[4.4.0,5.0.0)")
    api("com.atlassian.performance.tools:aws-resources:[1.1.1,2.0.0)")
    api("com.atlassian.performance.tools:jira-actions:[2.0.0,4.0.0)")
    api("com.atlassian.performance.tools:ssh:[2.0.0,3.0.0)")
    api("com.atlassian.performance.tools:virtual-users:[3.3.0,4.0.0)")

    implementation("com.atlassian.performance.tools:jvm-tasks:[1.0.0,2.0.0)")
    implementation("com.atlassian.performance.tools:workspace:[2.0.0,3.0.0)")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.glassfish:javax.json:1.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
    log4j(
        "api",
        "core",
        "slf4j-impl"
    ).forEach { implementation(it) }

    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")
}

fun log4j(
    vararg modules: String
): List<String> = modules.map { module ->
    "org.apache.logging.log4j:log4j-$module:2.10.0"
}

tasks.getByName("test", Test::class).apply {
    filter {
        exclude("**/*IT.class")
    }
}

val testIntegration = task<Test>("testIntegration") {
    filter {
        include("**/*IT.class")
    }
    maxParallelForks = 4
}

tasks["check"].dependsOn(testIntegration)

task<Wrapper>("wrapper") {
    gradleVersion = "4.9"
    distributionType = Wrapper.DistributionType.ALL
}
