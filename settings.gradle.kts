plugins {
    id("com.gradle.enterprise") version "3.16"
}

val runsOnCI = System.getenv("CI") == "true"

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"

        publishAlwaysIf(runsOnCI)
    }
}

rootProject.name = "candlepin"

include("client")
include("spec-tests")
