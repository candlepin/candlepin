plugins {
    id("java")
    id("groovy-gradle-plugin")
    id("java-gradle-plugin")
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(libs.openapi)

    // Plugins are not yet available in convention plugins, so we have to load it here.
    implementation(libs.test.logger.plugin)

    // Fix for transitive dependency conflict between openapi and owasp
    configurations.configureEach {
        resolutionStrategy {
            force("org.yaml:snakeyaml:1.33")
        }
    }
}

gradlePlugin {
    plugins {
        create("specVersionPlugin") {
            id = "org.candlepin.gradle.SpecVersion"
            implementationClass = "org.candlepin.gradle.SpecVersion"
        }
        create("gettextPlugin") {
            id= "org.candlepin.gradle.gettext"
            implementationClass = "Gettext"
        }
        create("msgfmtPlugin") {
            id= "org.candlepin.gradle.msgfmt"
            implementationClass = "Msgfmt"
        }
    }
}
