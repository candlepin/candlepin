import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    id("java")
    id("checkstyle-conventions")
    id("test-logging-conventions")
}

repositories {
    mavenCentral()
}

description = "Candlepin Spec Tests"

dependencies {
    implementation(project(":client"))

    implementation(libs.assertj)
    implementation(libs.commons.codec)
    implementation(libs.commons.io)
    implementation(libs.commons.lang)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jdk8)
    implementation(libs.jackson.jsr310)
    implementation(libs.junit.api)
    implementation(libs.junit.params)
    implementation(libs.oauth)
    implementation(libs.okhttp)
    implementation(libs.okhttp.tls)
    implementation(libs.slf4j)

    testImplementation(libs.awaitility)
    testImplementation(libs.jimfs)

    testRuntimeOnly(libs.junit.engine)

    implementation(fileTree("/usr/lib64/jss").include("*.jar"))
}

testlogger {
    theme = ThemeType.STANDARD_PARALLEL
}

// Disable empty test task
tasks.named("test", Test::class.java) {
    enabled = false
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<Test>("spec") {
    description = "Run Java based spec tests"
    group = "Verification"
    outputs.upToDateWhen { false }

    debugOptions {
        host = "*"
    }

    useJUnitPlatform()
    // maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
    reports.html.required = false
    reports.junitXml.required = false

    // We have to propagate the -D params if we want them available in tests
    System.getProperties().keys().iterator().forEach { key ->
        val properyKey = key.toString()
        // Propagate spec config
        if (properyKey.startsWith("spec.test.client")) {
            systemProperty(properyKey, System.getProperty(properyKey))
        }

        // Propagate current working directory
        if (properyKey == "user.dir") {
            systemProperty(properyKey, System.getProperty(properyKey))
        }
    }
}
