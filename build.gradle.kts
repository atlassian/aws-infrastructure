plugins {
    kotlin("jvm").version(Versions.kotlin)
    maven
}

maven {
    group = "com.atlassian.test.performance"
    version = "0.0.1-SNAPSHOT"
}

dependencies {
    compile(Libs.infrastructure)
    compile(Libs.awsResources)
    compile(Libs.tasks)
    compile(Libs.kotlinStandard)
    compile(Libs.json)
    compile("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")
    testCompile(Libs.junit)
    testCompile(Libs.hamcrest)

    Libs.log4jCore().forEach { compile(it) }
}