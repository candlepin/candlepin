plugins {
    id("checkstyle")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val checkstyle: Provider<MinimalExternalModuleDependency> = libs.findLibrary("checkstyle").orElseThrow()
dependencies {
    checkstyle(checkstyle)
    libs.findLibrary("checkstyle-sevntu").ifPresent {
        checkstyle(it)
    }
}

//// This prevents error where checkstyle sometimes resolves its dependencies
//// to use google-collections instead of guava.
configurations.checkstyle {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.collections:google-collections") {
        select("com.google.guava:guava:0")
    }
}

checkstyle {
    // Use the latest checkstyle version instead of default one
    checkstyle.get().version?.let {
        toolVersion = it
    }
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    maxErrors = 0
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/i18n/Messages*")
}

// Create a single checkstyle task to make it easier to run the checkstyleMain
// and checkstyleTest targets at once.
tasks.register("checkstyle") {
    dependsOn("checkstyleMain", "checkstyleTest")
    description = "Run checkstyle for the Main and Test targets at once"
    group = "Verification"
}