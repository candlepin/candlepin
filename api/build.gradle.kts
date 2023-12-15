/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    id("java")
    alias(libs.plugins.openapi.generator)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jackson.jaxrs)
    implementation(libs.javax.annotation)
    implementation(libs.javax.rs)
    implementation(libs.resteasy.multipart)
    implementation(libs.resteasy.validator)
    implementation(libs.swagger)
}

//// Add the openapi generated classes to the main java source sets to compile in the generated interfaces
sourceSets {
    main {
        java {
            srcDir("${buildDir}/generated/api/src/gen/java")
        }
    }
}

val specFile = "${projectDir}/candlepin-api-spec.yaml"

openApiValidate {
    inputSpec = specFile
}

val generateApi = tasks.named<GenerateTask>("openApiGenerate") {
    generatorName = "jaxrs-spec"
    inputSpec = specFile
    configFile = "${projectDir}/candlepin-api-config.json"
    outputDir = "$buildDir/generated/api"
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "generatePom" to "false",
        "dateLibrary" to "java8",
        "useTags" to "true"
    )
    templateDir = "$rootDir/buildSrc/src/main/resources/templates"
}

val generateDocs = tasks.register<GenerateTask>("openApiGenerateDocs") {
    generatorName = "html"
    inputSpec = specFile
    outputDir = "$buildDir/docs"
    generateApiDocumentation = true
    generateModelDocumentation = true
    generateModelTests = false
    generateApiTests = false
    withXml = false
}

val generateApiJson = tasks.register<GenerateTask>("openApiGenerateJson") {
    generatorName = "openapi"
    inputSpec = specFile
    outputDir = "$buildDir/generated/json"
    generateApiDocumentation = true
    generateModelDocumentation = true
    generateModelTests = false
    generateApiTests = false
    withXml = false
}

//Update the compileJava & processResourcesTasks depend on the openapi generation so that the generated classes
//can be used during compilation.
tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateApi)
}
tasks.named<ProcessResources>("processResources") {
    dependsOn(generateApiJson)
}
