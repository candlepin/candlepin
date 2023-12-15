import groovy.text.GStringTemplateEngine
import org.yaml.snakeyaml.Yaml
import java.io.FileFilter
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("java")
    id("war")
    id("distribution")
    id("maven-publish")
    id("jacoco")
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.sonarqube)

    // The following are plugins we wrote in the buildSrc folder
    id("org.candlepin.gradle.gettext")
    id("org.candlepin.gradle.msgfmt")
    id("org.candlepin.gradle.SpecVersion")
    id("checkstyle-conventions")
    id("test-logging-conventions")
}

group = "org.candlepin"

// TODO extract to plugin
run {
    extra["cpdb_username"] = "candlepin"
    extra["cpdb_password"] = ""
    extra["db_name"] = "candlepin"
    extra["amqp_enabled"] = "false"
    extra["hidden_resources"] = ""
    extra["hidden_capabilities"] = ""
    extra["hostedtest"] = "false"
    extra["auth_cloud_enable"] = "false"
    extra["manifestgen"] = "false"
    extra["additional_properties"] = ""

    if (project.hasProperty("hidden_resources")) {
        extra["hidden_resources"] = project.property("hidden_resources")
    }

    if (project.hasProperty("hidden_capabilities")) {
        extra["hidden_capabilities"] = project.property("hidden_capabilities")
    }

    if (project.hasProperty("db_host") && project.property("db_host").toString().isNotBlank()) {
        extra["db_host"] = project.property("db_host").toString()
    } else {
        extra["db_host"] = "localhost"
    }

    if (project.hasProperty("app_db_name") && project.property("app_db_name").toString().isNotBlank()) {
        extra["db_name"] = project.property("app_db_name").toString()
    }

    // If MYSQL set up the mysql stuff else set up postgres (default)
    val databaseServer: String = project.findProperty("database_server")?.toString() ?: ""
    if (databaseServer == "mysql") {
        extra["jdbc_driver_class"] = "org.mariadb.jdbc.Driver"
        extra["jdbc_dialect"] = "org.hibernate.dialect.MySQL5InnoDBDialect"
        extra["jdbc_quartz_driver_class"] = "org.quartz.impl.jdbcjobstore.StdJDBCDelegate"
        extra["jdbc_url"] = "jdbc:mariadb://${extra["db_host"]}/${extra["db_name"]}"
    } else {
        extra["jdbc_driver_class"] = "org.postgresql.Driver"
        extra["jdbc_dialect"] = "org.hibernate.dialect.PostgreSQL92Dialect"
        extra["jdbc_quartz_driver_class"] = "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate"
        extra["jdbc_url"] = "jdbc:postgresql://${extra["db_host"]}/${extra["db_name"]}"
    }

    extra["external_broker"] = project.findProperty("external_broker")?: "false"
    extra["async_scheduler_enabled"] = project.findProperty("async_scheduler_enabled")?: "true"
}

repositories {
    mavenCentral()

    maven { url = uri("https://repo.jenkins-ci.org/public/") }
    maven { url = uri("https://repository.jboss.org/nexus/content/groups/public/") }
}

dependencies {
    implementation(project(":api"))

    // Commons
    implementation(libs.commons.codec)
    implementation(libs.commons.collections)
    implementation(libs.commons.io)
    implementation(libs.commons.lang)

    // Collections
    implementation(libs.guava)

    // Gettext libraries used for internationalization & translation
    implementation(libs.gettext)

    // Guice Libraries
    implementation(libs.guice.servlet)
    implementation(libs.guice.persist)

    // Jackson
    implementation(libs.jackson.hibernate)
    implementation(libs.jackson.jaxrs)
    implementation(libs.jackson.jdk8)
    implementation(libs.jackson.json.schema)
    implementation(libs.jackson.jsr310)
    implementation(libs.jackson.xml)
    implementation(libs.jackson.yaml)

    // Bean validation API is explicitly added to this version
    // This is a transitive dependency of
    // com.fasterxml.jackson.module:jackson-module-jsonSchema
    implementation(libs.javax.validation)

    // Javax
    implementation(libs.hibernate)
    implementation(libs.javax.annotation)

    // Liquibase
    implementation(libs.liquibase)
    implementation(libs.picocli)

    // Logging
    implementation(libs.logback)
    // Artifacts that bridge other logging frameworks to slf4j. Mime4j uses
    // JCL for example.
    implementation(libs.jcl.over.slf4j)
    implementation(libs.log4j.over.slf4j)
    implementation(libs.logstash)

    // Oauth
    implementation(libs.oauth)

    // Resteasy
    implementation(libs.resteasy.atom)
    implementation(libs.resteasy.guice)
    implementation(libs.resteasy.multipart)
    implementation(libs.resteasy.validator)

    implementation(libs.javax.rs)

    // Sun jaxb
    implementation(libs.jaxb)
    implementation(libs.jaxb.core)

    // Swagger
    implementation(libs.swagger)

    // Validator
    implementation(libs.hibernate.validator)
    implementation(libs.hibernate.validator.processor)

    // Hibernate
    implementation(libs.hibernate.c3p0)
    // Ehcache (for use with hibernate primarily)
    implementation(libs.hibernate.jcache)
    implementation(libs.ehcache)
    implementation(libs.javax.cache)

    // Artemis server & client
    implementation(libs.artemis.server)
    implementation(libs.artemis.stomp)

    // Javascript Engine
    implementation(libs.rhino)

    implementation(libs.quartz)

    // Keycloak
    implementation(libs.keycloak)

    // Hibernate JPA integration
    implementation(libs.hibernate.jpamodelgen)
    annotationProcessor(libs.hibernate.jpamodelgen)

    // Smallrye Config
    implementation(libs.smallrye.config)
    implementation(libs.tomcat.annotations)

    // Use wildcard because this could be called jss4.jar or jss.jar
    // (depending on if we are before Fedora35/RHEL9 or after)
    providedCompile(fileTree("/usr/lib64/jss").include("*.jar"))
    providedCompile(libs.javax.servlet)

    // DB Drivers
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mariadb)

    testRuntimeOnly(libs.hsqldb)
    testRuntimeOnly(libs.javax.el)

    // Core testing libraries
    // Junit 5
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)

    testImplementation(libs.hamcrest)
    testImplementation(libs.mockito.junit)
    testImplementation(libs.liquibase.slf4j)
    testImplementation(libs.assertj)
}

sourceSets {
    main {
        // Copy the resources to the main classes directory so that the
        // persistence context is in the the same classpath entry for
        // Hibernate annotation based discovery.
        output.setResourcesDir(file("${buildDir}/classes/java/main"))
        java {
            // Add the openapi generated classes to the main java source
            // sets to compile in the generated interfaces
            srcDir("${buildDir}/generated/api/src/gen/java")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // options.compilerArgs << "-Xlint:unchecked"
    // options.compilerArgs << "-Xlint:deprecation"
    // options.compilerArgs.addAll(['--release', '8'])
}

tasks.register("openApiValidate") {
    dependsOn(":api:openApiValidate")
}

tasks.register("openApiGenerate") {
    dependsOn(":api:openApiGenerate")
    dependsOn(":client:openApiGenerate")
}

tasks.register("openApiGenerateJson") {
    dependsOn(":api:openApiGenerateJson")
}

gettext {
    keys_project_dir = "${project.rootDir}/"
}

msgfmt {
    resource = "org.candlepin.common.i18n.Messages"
}

val test by tasks.getting(Test::class) {
    debugOptions {
        host = "*"
    }

    useJUnitPlatform()

    // Sometimes causes out of memory on vagrant
    maxHeapSize = "2g"
    jvmArgs = listOf(
        // We need to load the native jss lib (either libjss4.so or libjss.so) from this directory
        "-Djava.library.path=/usr/lib64/jss",
        "-XX:+HeapDumpOnOutOfMemoryError"
    )

}

val jacocoTestReport by tasks.getting(JacocoReport::class) {
    dependsOn(test)

    reports {
        csv.required = false
        html.required = false
        xml.required = true
    }

    doLast {
        logger.lifecycle("Generated unit test coverage report at $buildDir/reports/jacoco/test/jacocoTestReport.xml")
    }
}

// User-friendly alias for jacocoTestReport task
val coverage = tasks.register("coverage") {
    dependsOn(jacocoTestReport)
}

project.tasks["sonar"].dependsOn(coverage)
sonar {
    properties {
        property("sonar.sourceEncoding", "Utf-8")
        property("sonar.projectKey", "candlepin_candlepin")
        property("sonar.organization", "candlepin")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.BIN
}

tasks.clean {
    delete += listOf(
        "${rootDir}/buildSrc/build",
        // Maven clean up
        "${rootDir}/src/main/webapp/docs/candlepin-api-spec.yaml",
        "${rootDir}/target"
    )
}

////Update the compileJava & processResourcesTasks depend on the openapi generation so that the generated classes
////can be used during compilation.
//compileJava.dependsOn tasks.openApiGenerate
//processResources.dependsOn(":api:openApiGenerateJson")
//processResources.dependsOn("specVersion")
//compileJava.dependsOn(processResources)
tasks.getByName<JavaCompile>("compileJava")
    .dependsOn(tasks.getByName("processResources"))


// substitute the version & release in the version.properties used by the status resource at runtime
project.tasks.named<ProcessResources>("processResources") {
    from("src/main/resources") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        include("version.properties")
        expand(
            mapOf(
                "version" to project.version,
                "release" to project.findProperty("release")
            )
        )
    }
}

// A task to generate the candlepin config file for use in etc or other locations.
val generateConfig = tasks.register("generateConfig") {
    dependsOn(":processResources")
    val template = file("$projectDir/config/candlepin/candlepin.conf.template")
    val targetFile = file("$buildDir/candlepin.conf")
    doLast {
        val defaults = mapOf("candlepin" to project.extensions.extraProperties)
        val binding = mapOf("candlepin" to defaults["candlepin"])

        // todo
//        try {
//            val custom: Map<String, String> = Yaml().load(File("$projectDir/custom.yaml").inputStream())
//            // Overwrite the defaults with the values from custom.yaml
//            // We have to use the key 'candlepin' instead of 'candlepin.conf' since the dot in the
//            // key name would otherwise be interpreted as a dereference in the template.
//            if (custom != null && custom["candlepin.conf"] != null) {
//                (binding["candlepin"] as MutableMap<String, Any>).putAll((custom["candlepin.conf"] as Map<*, *>).filterKeys { it != null })
//            }
//        } catch (e: FileNotFoundException) {
//            println("No custom.yaml found. Using defaults.")
//        }

        // change contents via cli options
        // change file contents
        val tmp = GStringTemplateEngine().createTemplate(template).make(binding)
        targetFile.writeText(tmp.toString(), Charsets.UTF_8)
    }
}

tasks.named("assemble") {
    dependsOn(generateConfig)
}

// task to generate candlepin-api jar that Hosted adapters build against
// invoked as `./gradlew apiJar`
val apiJar = tasks.register<Jar>("apiJar") {
    archiveBaseName = "candlepin-api"
    from(sourceSets["main"].output)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    includes.addAll(setOf(
        "auth",
        "config",
        "controller",
        "exceptions",
        "jackson",
        "model",
        "pki",
        "resteasy",
        "service",
        "util"
    ).map { "/org/candlepin/${it}/" })
}

tasks.war {
    // This is a bit wonky and has potential to miss stuff, but we don't really have a much nicer
    // way of doing this, outside the real correct answer of converting them to subprojects like
    // they probably should be.
    if (project.hasProperty("test_extensions")) {
        val testExts = project.findProperty("test_extensions")?.toString()?.split(",") ?: emptyList()

        // If we've defined one or more test extensions, selectively exclude subpackages that are
        // not in the list of test extensions
        val testExtRootPath = "${buildDir}/classes/java/main/org/candlepin/testext/";
        val packages = File(testExtRootPath)
            .listFiles(File::isDirectory) ?: emptyArray()

        packages.forEach { pkg ->
            if (!testExts.contains(pkg.getName())) {
                rootSpec.exclude("**/testext/${pkg.getName()}/**");
            } else {
                println("Including test extension: ${pkg.getName()}")
            }
        }
    } else {
        // Building with no test extensions; exclude the whole package
        rootSpec.exclude("**/testext/**");
    }

    manifest {
        attributes(
            "Implementation-Title" to "The Candlepin Project",
            "Copyright" to "Red Hat, Inc. 2009-${SimpleDateFormat("y").format(Date())}"
        )
    }

    // Copy the license file into place in the final manifest
    from(project.projectDir) {
        include("LICENSE")
        into("META-INF")
    }

    from(project.file("./api")) {
        include("candlepin-api-spec.yaml")
        into("docs")
    }
    from(project.file("$buildDir/generated/api/src/main/openapi")) {
        include("openapi.yaml")
        into("WEB-INF/classes")
    }
    from(project.file("$buildDir/generated/json")) {
        include("openapi.json")
        into("WEB-INF/classes")
    }
}

publishing {
    publications {
        // Publish the candlepin war
        create<MavenPublication>("warArtifact") {
            from(components["web"])

            groupId = "org.candlepin"
            artifactId = "candlepin"
        }

        // Publish the candlepin-api jar
        create<MavenPublication>("jarArtifact") {
            artifact(tasks["apiJar"])

            groupId = "org.candlepin"
            artifactId = "candlepin-api"
        }
    }
}
