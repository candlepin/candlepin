plugins {
    alias(libs.plugins.openapi.generator)
    id("java-library")
    id("java")
}

description = "Candlepin Generated Client Library"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.gsonfire)
    implementation(libs.javax.annotation)
    implementation(libs.javax.rs)
    implementation(libs.okhttp)
    implementation(libs.okhttp.interceptor)
    implementation(libs.spotbugs)
    implementation(libs.swagger)
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", "${buildDir}/generated/src/main/java")
        }
    }
}
tasks.getByName<JavaCompile>("compileJava").dependsOn(tasks.getByName("openApiGenerate"))

openApiGenerate {
    inputSpec = "${projectDir}/../api/candlepin-api-spec.yaml"
    outputDir = "${buildDir}/generated"
    configFile = "${projectDir}/../api/candlepin-api-config.json"
    generatorName = "java"
    validateSpec = true
    skipValidateSpec = true
    configOptions = mapOf(
        "dateLibrary" to "java8"
    )
    modelPackage = "org.candlepin.dto.api.client.v1"
    apiPackage = "org.candlepin.resource.client.v1"
    invokerPackage = "org.candlepin.invoker.client"
    additionalProperties = mapOf(
        "useRuntimeException" to true
    )
}
