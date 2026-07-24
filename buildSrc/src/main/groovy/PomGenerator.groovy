/*
 *  Copyright (c) 2009 - 2026 Red Hat, Inc.
 *
 *  This software is licensed to you under the GNU General Public License,
 *  version 2 (GPLv2). There is NO WARRANTY for this software, express or
 *  implied, including the implied warranties of MERCHANTABILITY or FITNESS
 *  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 *  along with this software; if not, see
 *  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 *  Red Hat trademarks are not licensed under GPLv2. No permission is
 *  granted to use or replicate Red Hat trademarks that are incorporated
 *  in this software or its documentation.
 */

import java.util.stream.Stream

import groovy.util.IndentPrinter
import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction


/**
 * Extension block for configuring the PomGenerator plugin from build.gradle.
 *
 * <p>Usage:
 * <pre>
 * pomGenerator {
 *     outputDir = "/some/other/path"
 * }
 * </pre>
 */
class PomGeneratorExtension {
    /** Output directory for generated pom files; defaults to the project root when null. */
    String outputDir = null
}

/**
 * Generates a Maven multi-module pom.xml hierarchy that mirrors the Gradle build configuration.
 *
 * <p>Produces three files: a parent pom, a WAR module pom, and an API JAR module pom.
 * All dependency versions, plugin versions, and build settings are read dynamically
 * from the Gradle project model so the output stays in sync with build.gradle and the
 * version catalog.
 *
 * <p>The task only runs when some aspect of the Gradle config changed (such as dependencies, plugins,
 * repos, etc.) based on its hash.
 */
class PomGeneratorTask extends DefaultTask {

    /** A declared or resolved dependency, mapped to Maven coordinates and scope. */
    static class DeclaredDep implements Comparable<DeclaredDep> {
        String group
        String name
        String version
        String classifier
        String mavenScope
        List<Map<String, String>> exclusions = []

        String sortKey() { group + ":" + name + ":" + (classifier ?: "") }

        @Override
        int compareTo(DeclaredDep other) {
            return this.sortKey() <=> other.sortKey()
        }
    }

    @Input
    String getProjectVersion() {
        return project.version.toString()
    }

    /** MD5 hash of all dependency coordinates and scopes; triggers regeneration when deps change. */
    @Input
    String getDependencyHash() {
        Map<String, String> resolvedVersions = collectResolvedVersions()
        List<DeclaredDep> deps = collectDeclaredDeps(resolvedVersions)
        Set<String> declaredKeys = deps.collect { it.group + ":" + it.name } as Set
        deps = Stream.of(
            deps,
            collectForcedDeps(declaredKeys),
            collectTransitiveVersionPins(declaredKeys)
        )
        .flatMap(Collection::stream)
        .sorted()
        .toList();
        return deps.collect { it.sortKey() + ":" + it.version + ":" + it.mavenScope + ":" +
            it.exclusions.collect { e -> e.groupId + "/" + e.artifactId }.sort().join(",")
        }.join("\n").md5()
    }

    /** MD5 hash of Maven plugin versions; triggers regeneration when plugin versions change. */
    @Input
    String getPluginVersionHash() {
        return collectPluginVersions().collect { it.key + ":" + it.value }.sort().join("\n").md5()
    }

    /** MD5 hash of non-Central Maven repository URLs; triggers regeneration when repos change. */
    @Input
    String getRepositoryHash() {
        return collectRepositories().collect { it.id + ":" + it.url }.join("\n").md5()
    }

    @OutputFiles
    List<File> getOutputPomFiles() {
        File baseDir = resolveOutputDir()
        return [
            new File(baseDir, "pom.xml"),
            new File(baseDir, "maven/war/pom.xml"),
            new File(baseDir, "maven/api/pom.xml")
        ]
    }

    /**
     * Collects all build metadata from the Gradle project and writes the three pom.xml files.
     */
    @TaskAction
    void generate() {
        File baseDir = resolveOutputDir()
        new File(baseDir, "maven/war").mkdirs()
        new File(baseDir, "maven/api").mkdirs()

        String projectVersion = project.version.toString()
        String javaVersion = project.java.sourceCompatibility.majorVersion
        Map<String, String> pluginVersions = collectPluginVersions()
        Map<String, String> resolvedVersions = collectResolvedVersions()
        List<DeclaredDep> allDeclaredDeps = collectDeclaredDeps(resolvedVersions)

        Set<String> declaredKeys = allDeclaredDeps.collect { it.group + ":" + it.name } as Set
        List<DeclaredDep> forcedDeps = collectForcedDeps(declaredKeys)
        allDeclaredDeps.addAll(forcedDeps)
        Collections.sort(allDeclaredDeps)

        List<DeclaredDep> versionPins = collectTransitiveVersionPins(declaredKeys)

        writeParentPom(baseDir, projectVersion, allDeclaredDeps, versionPins,
            pluginVersions, javaVersion)
        List<String> apiJarIncludes = collectApiJarIncludes()
        writeWarPom(baseDir, projectVersion, allDeclaredDeps)
        writeApiPom(baseDir, projectVersion, allDeclaredDeps, apiJarIncludes)

        logger.lifecycle("Generated Maven multi-module project at: ${baseDir.canonicalPath}")
    }

    /** Resolves the output directory from the extension, falling back to the project root. */
    File resolveOutputDir() {
        PomGeneratorExtension ext = project.extensions.getByType(PomGeneratorExtension)
        if (ext.outputDir) {
            return project.file(ext.outputDir)
        }
        return project.projectDir
    }

    /**
     * Reads Maven plugin versions from the Gradle version catalog.
     *
     * @return
     *     map of Maven plugin property name to version string
     */
    Map<String, String> collectPluginVersions() {
        def catalog = project.extensions.getByType(
            org.gradle.api.artifacts.VersionCatalogsExtension).named("libs")
        return [
            "openapi-generator-maven-plugin": catalog.findVersion("openapi-generator").get().toString(),
            "exec-maven-plugin": catalog.findVersion("maven-exec-plugin").get().toString(),
            "build-helper-maven-plugin": catalog.findVersion("maven-build-helper-plugin").get().toString(),
            "maven-compiler-plugin": catalog.findVersion("maven-compiler-plugin").get().toString(),
            "maven-resources-plugin": catalog.findVersion("maven-resources-plugin").get().toString(),
            "maven-war-plugin": catalog.findVersion("maven-war-plugin").get().toString(),
            "maven-jar-plugin": catalog.findVersion("maven-jar-plugin").get().toString()
        ]
    }

    /**
     * Builds a group:name to resolved-version map for first-level (direct) dependencies only.
     *
     * <p>Transitive dependency versions are NOT covered here; those are handled separately by
     * {@link #collectTransitiveVersionPins}. When a dependency appears in multiple configurations,
     * the version from the first match wins (compileClasspath, then runtimeClasspath, then
     * annotationProcessor).
     *
     * @return
     *     map of "group:name" to resolved version string for direct dependencies
     */
    Map<String, String> collectResolvedVersions() {
        Map<String, String> map = [:]

        // compileClasspath and runtimeClasspath are the standard resolvable configurations.
        // annotationProcessor is also resolvable.
        for (String configName : ["compileClasspath", "runtimeClasspath", "annotationProcessor"]) {
            Configuration config = project.configurations.findByName(configName)
            if (!config) {
                continue
            }
            Set<ResolvedDependency> deps = safeResolvedFirstLevelDeps(config)
            for (ResolvedDependency dep : deps) {
                String key = dep.moduleGroup + ":" + dep.moduleName
                if (!map.containsKey(key)) {
                    map[key] = dep.moduleVersion
                }
            }
        }

        return map
    }

    /**
     * Resolves first-level dependencies, returning an empty set on failure instead of throwing.
     *
     * @param config
     *     the Gradle configuration to resolve
     * @return
     *     resolved first-level dependencies, or empty set if resolution fails
     */
    Set<ResolvedDependency> safeResolvedFirstLevelDeps(Configuration config) {
        try {
            return config.resolvedConfiguration.firstLevelModuleDependencies
        }
        catch (Exception e) {
            logger.error("Could not resolve configuration '${config.name}': ${e.message}")
            return [] as Set
        }
    }

    /**
     * Collects explicitly declared dependencies from non-test Gradle configurations and maps
     * them to Maven scopes. Uses resolved versions where available, falling back to declared versions.
     *
     * @param resolvedVersions
     *     group:name to version map from {@link #collectResolvedVersions}
     * @return
     *     sorted list of declared dependencies with Maven scope assigned
     */
    List<DeclaredDep> collectDeclaredDeps(Map<String, String> resolvedVersions) {
        // Gradle configuration name → Maven scope. Test configs are excluded.
        Map<String, String> scopeMap = [
            "implementation": "compile",
            "compileOnly": "provided",
            "providedCompile": "provided",
            "runtimeOnly": "runtime"
        ]

        Set<String> seen = new HashSet<>()
        List<DeclaredDep> deps = []

        // Using for loops here to avoid calling instance methods from within closures,
        // which can trigger Gradle's methodMissing instead of normal method dispatch.
        for (Map.Entry<String, String> entry : scopeMap.entrySet()) {
            String configName = entry.key
            String mavenScope = entry.value
            Configuration config = project.configurations.findByName(configName)
            if (!config) {
                continue
            }

            for (Object depObj : config.dependencies) {
                if (!(depObj instanceof ExternalDependency)) {
                    continue
                }
                ExternalDependency extDep = (ExternalDependency) depObj
                String depKey = extDep.group + ":" + extDep.name
                if (seen.contains(depKey)) {
                    continue
                }
                seen.add(depKey)

                String classifier = extractClassifier(extDep)
                String resolvedVersion = resolvedVersions.get(depKey) ?: (extDep.version ?: "")

                List<Map<String, String>> exclusions = []
                for (Object rule : extDep.excludeRules) {
                    exclusions.add([groupId: rule.group ?: "*", artifactId: rule.module ?: "*"])
                }

                deps.add(new DeclaredDep(
                    group: extDep.group,
                    name: extDep.name,
                    version: resolvedVersion,
                    classifier: classifier,
                    mavenScope: mavenScope,
                    exclusions: exclusions
                ))
            }
        }

        Collections.sort(deps)
        return deps
    }

    /** Extracts the artifact classifier from a dependency, or null if none is set. */
    String extractClassifier(ExternalDependency dep) {
        if (dep.artifacts && !dep.artifacts.isEmpty()) {
            return dep.artifacts.first().classifier ?: null
        }
        return null
    }

    /**
     * Collects dependencies from resolution strategy force overrides that are not already declared.
     *
     * <p>Note: mutates {@code alreadyDeclared} by adding the keys of any forced deps found,
     * so subsequent collection methods skip duplicates.
     *
     * @param alreadyDeclared
     *     mutable set of "group:name" keys already collected (will be modified)
     * @return
     *     sorted list of forced dependencies not already in the declared set
     */
    List<DeclaredDep> collectForcedDeps(Set<String> alreadyDeclared) {
        List<DeclaredDep> forced = []

        for (Configuration config : project.configurations) {
            if (!config.canBeResolved) {
                continue
            }
            for (Object module : config.resolutionStrategy.forcedModules) {
                String key = module.group + ":" + module.name
                if (alreadyDeclared.contains(key)) {
                    continue
                }
                alreadyDeclared.add(key)
                forced.add(new DeclaredDep(
                    group: module.group,
                    name: module.name,
                    version: module.version,
                    classifier: null,
                    mavenScope: "compile",
                    exclusions: []
                ))
            }
        }

        Collections.sort(forced)
        return forced
    }

    /**
     * Collects version pins for transitive dependencies that Gradle resolved to a specific
     * version but that are not directly declared or forced.
     *
     * <p>These entries are written to the parent pom's {@code <dependencyManagement>} section
     * (version only, no scope or exclusions) to prevent Maven's "nearest definition wins"
     * strategy from picking a lower version than what Gradle resolved.
     *
     * @param alreadyDeclared
     *     set of "group:name" keys for declared and forced deps (read-only)
     * @return
     *     sorted list of transitive version pins
     */
    List<DeclaredDep> collectTransitiveVersionPins(Set<String> alreadyDeclared) {
        List<DeclaredDep> pins = []
        Configuration runtimeCp = project.configurations.findByName("runtimeClasspath")
        if (!runtimeCp) {
            return pins
        }

        Set<ResolvedArtifact> artifacts
        try {
            artifacts = runtimeCp.resolvedConfiguration.resolvedArtifacts
        }
        catch (Exception e) {
            logger.error("Could not resolve runtimeClasspath artifacts: ${e.message}")
            return pins
        }

        Set<String> seen = new HashSet<>(alreadyDeclared)
        for (ResolvedArtifact artifact : artifacts) {
            String group = artifact.moduleVersion.id.group
            String name = artifact.moduleVersion.id.name
            String key = group + ":" + name
            if (seen.contains(key)) {
                continue
            }
            seen.add(key)
            pins.add(new DeclaredDep(
                group: group,
                name: name,
                version: artifact.moduleVersion.id.version,
                classifier: null,
                mavenScope: null,
                exclusions: []
            ))
        }

        Collections.sort(pins)
        return pins
    }

    /**
     * Collects non-Central Maven repositories from the Gradle project, sorted by id.
     *
     * @return
     *     list of maps with id, name, and url keys
     */
    List<Map<String, String>> collectRepositories() {
        List<Map<String, String>> repos = []

        for (Object repo : project.repositories) {
            if (repo instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository) {
                org.gradle.api.artifacts.repositories.MavenArtifactRepository mavenRepo = repo
                String repoUrl = mavenRepo.url.toString()

                if (repoUrl.contains("repo.maven.apache.org") || repoUrl.contains("repo1.maven.org")) {
                    continue
                }

                String repoName = mavenRepo.name ?: repoUrl
                String repoId = repoName.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase()
                repos.add([id: repoId, name: repoName, url: repoUrl])
            }
        }

        return repos.sort { a, b -> a.id <=> b.id }
    }

    /**
     * Reads the package include patterns from the apiJar task and converts them to
     * Maven jar-plugin format (e.g. "org/candlepin/model/**").
     *
     * @return
     *     sorted list of include patterns
     * @throws GradleException
     *     if the apiJar task is not registered
     */
    List<String> collectApiJarIncludes() {
        def apiJarTask = project.tasks.findByName("apiJar")
        if (!apiJarTask) {
            throw new GradleException("apiJar task not found — cannot determine API JAR package includes")
        }
        return apiJarTask.includes.collect { String pattern ->
            pattern.replaceAll('^/', '').replaceAll('/$', '') + '/**'
        }.sort()
    }

    /**
     * Resolves the version of an annotation processor dependency by artifact name.
     *
     * @param artifactName
     *     the module name to look up (e.g. "hibernate-jpamodelgen")
     * @return
     *     the resolved version string, or null if not found or resolution fails
     */
    String resolveAnnotationProcessorVersion(String artifactName) {
        Configuration apConfig = project.configurations.findByName("annotationProcessor")
        if (!apConfig) {
            return null
        }
        try {
            ResolvedDependency found = safeResolvedFirstLevelDeps(apConfig).find {
                it.moduleName == artifactName
            }
            return found ? found.moduleVersion : null
        }
        catch (Exception e) {
            logger.error("Could not resolve annotationProcessor version for ${artifactName}: ${e.message}")
            return null
        }
    }

    // -------------------------------------------------------------------------
    // Shared plugin helpers (used by both WAR and API module poms)
    // -------------------------------------------------------------------------

    /** Emits the openapi-generator-maven-plugin configuration block. */
    void writeOpenapiPlugin(MarkupBuilder xml) {
        xml.plugin {
            groupId("org.openapitools")
            artifactId("openapi-generator-maven-plugin")
            delegate.version("\${openapi-generator-maven-plugin.version}")
            executions {
                execution {
                    id("generate-jaxrs-interfaces")
                    goals { goal("generate") }
                    configuration {
                        inputSpec("\${project.basedir}/../../api/candlepin-api-spec.yaml")
                        generatorName("jaxrs-spec")
                        configurationFile(
                            "\${project.basedir}/../../api/candlepin-api-config.json")
                        output("\${project.build.directory}/generated/api")
                        templateDirectory(
                            "\${project.basedir}/../../buildSrc/src/main/resources/templates")
                        configOptions {
                            interfaceOnly("true")
                            dateLibrary("java8")
                            useTags("true")
                            containerDefaultToNull("true")
                            useJakartaEe("true")
                        }
                    }
                }
            }
        }
    }

    /**
     * Emits the maven-compiler-plugin configuration block, including annotation processor
     * paths when a JPA modelgen version is available.
     */
    void writeCompilerPlugin(MarkupBuilder xml, String jpaModelgenVersion) {
        xml.plugin {
            groupId("org.apache.maven.plugins")
            artifactId("maven-compiler-plugin")
            delegate.version("\${maven-compiler-plugin.version}")
            configuration {
                source("\${java.version}")
                target("\${java.version}")
                release("\${java.version}")
                encoding("UTF-8")
                generatedSourcesDirectory(
                    "\${project.build.directory}/generated/annotations")
                if (jpaModelgenVersion) {
                    annotationProcessorPaths {
                        path {
                            groupId("org.hibernate.orm")
                            artifactId("hibernate-jpamodelgen")
                            delegate.version(jpaModelgenVersion)
                        }
                    }
                }
            }
        }
    }

    /** Emits the exec-maven-plugin configuration block for gettext/msgfmt translation generation. */
    void writeMsgfmtPlugin(MarkupBuilder xml) {
        xml.plugin {
            groupId("org.codehaus.mojo")
            artifactId("exec-maven-plugin")
            delegate.version("\${exec-maven-plugin.version}")
            executions {
                execution {
                    id("generate-translations")
                    phase("generate-sources")
                    goals { goal("exec") }
                    configuration {
                        executable(
                            "\${project.basedir}/../../bin/scripts/generate_translations.sh")
                        arguments {
                            argument("\${project.basedir}/../../po")
                            argument("\${project.build.directory}/generated/msgfmt")
                        }
                    }
                }
            }
        }
    }

    /** Emits the build-helper-maven-plugin configuration block for adding generated sources. */
    void writeBuildHelperPlugin(MarkupBuilder xml) {
        xml.plugin {
            groupId("org.codehaus.mojo")
            artifactId("build-helper-maven-plugin")
            delegate.version("\${build-helper-maven-plugin.version}")
            executions {
                execution {
                    id("add-openapi-sources")
                    phase("generate-sources")
                    goals { goal("add-source") }
                    configuration {
                        sources {
                            source("\${project.build.directory}/generated/api/src/gen/java")
                        }
                    }
                }
                execution {
                    id("add-msgfmt-sources")
                    phase("generate-sources")
                    goals { goal("add-source") }
                    configuration {
                        sources {
                            source("\${project.build.directory}/generated/msgfmt")
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parent pom.xml
    // -------------------------------------------------------------------------

    /**
     * Writes the parent pom.xml with shared properties, dependency management, and repositories.
     */
    void writeParentPom(File baseDir, String projectVersion, List<DeclaredDep> allDeclaredDeps,
            List<DeclaredDep> versionPins, Map<String, String> pluginVersions,
            String javaVersion) {
        File pomFile = new File(baseDir, "pom.xml")

        // Use distinct name to avoid conflict with the "release" XML element in MarkupBuilder closures.
        String releaseVal = project.findProperty("release") ?: "1"

        StringWriter sw = new StringWriter()
        MarkupBuilder xml = new MarkupBuilder(new IndentPrinter(sw, "    "))
        xml.doubleQuotes = true
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")

        xml."project"(
            xmlns: "http://maven.apache.org/POM/4.0.0",
            "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
            "xsi:schemaLocation": "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
        ) {
            modelVersion("4.0.0")
            groupId("org.candlepin")
            artifactId("candlepin-parent")
            delegate.version(projectVersion)
            packaging("pom")
            name("Candlepin Parent")
            delegate.description("Candlepin subscription management system - parent POM")

            modules {
                module("maven/war")
                module("maven/api")
            }

            // Use delegate."name"(value) for all property names to ensure they are dispatched
            // to MarkupBuilder and not confused with local variables or Groovy String invocation.
            properties {
                delegate."java.version"(javaVersion)
                delegate."project.build.sourceEncoding"("UTF-8")
                delegate."project.reporting.outputEncoding"("UTF-8")
                delegate."maven.test.skip"("true")
                delegate."release"(releaseVal)
                pluginVersions.each { String propName, String propValue ->
                    delegate."${propName}.version"(propValue)
                }
            }

            dependencyManagement {
                dependencies {
                    mkp.yieldUnescaped("\n\n            <!-- Declared dependencies (from build.gradle) -->")
                    allDeclaredDeps.each { DeclaredDep dep ->
                        dependency {
                            groupId(dep.group)
                            artifactId(dep.name)
                            delegate.version(dep.version)
                            if (dep.classifier) {
                                classifier(dep.classifier)
                            }
                            scope(dep.mavenScope)
                            if (dep.exclusions) {
                                exclusions {
                                    dep.exclusions.each { exc ->
                                        exclusion {
                                            groupId(exc.groupId)
                                            artifactId(exc.artifactId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (versionPins) {
                        mkp.yieldUnescaped("\n\n            <!-- Transitive version pins: not direct dependencies, but pinned here\n                 to prevent Maven's \"nearest definition wins\" strategy from resolving\n                 a lower version than what Gradle resolved. -->")
                        versionPins.each { DeclaredDep pin ->
                            dependency {
                                groupId(pin.group)
                                artifactId(pin.name)
                                delegate.version(pin.version)
                            }
                        }
                    }
                }
            }

            List repoList = collectRepositories()
            if (repoList) {
                repositories {
                    repoList.each { Map repo ->
                        repository {
                            id(repo.id)
                            name(repo.name)
                            url(repo.url)
                            releases {
                                enabled("true")
                            }
                            snapshots {
                                enabled("false")
                            }
                        }
                    }
                }
            }
        }

        pomFile.setText(sw.toString(), "UTF-8")
        logger.lifecycle("Generated: ${pomFile.canonicalPath}")
    }

    // -------------------------------------------------------------------------
    // WAR module pom
    // -------------------------------------------------------------------------

    /** Writes the WAR module pom.xml with all dependencies and build plugins. */
    void writeWarPom(File baseDir, String projectVersion, List<DeclaredDep> allDeclaredDeps) {
        File pomFile = new File(baseDir, "maven/war/pom.xml")
        String jpaModelgenVersion = resolveAnnotationProcessorVersion("hibernate-jpamodelgen")

        StringWriter sw = new StringWriter()
        MarkupBuilder xml = new MarkupBuilder(new IndentPrinter(sw, "    "))
        xml.doubleQuotes = true
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")

        xml."project"(
            xmlns: "http://maven.apache.org/POM/4.0.0",
            "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
            "xsi:schemaLocation": "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
        ) {
            modelVersion("4.0.0")
            parent {
                groupId("org.candlepin")
                artifactId("candlepin-parent")
                delegate.version(projectVersion)
                relativePath("../../pom.xml")
            }
            artifactId("candlepin")
            packaging("war")
            name("Candlepin WAR")
            delegate.description("Candlepin subscription management system")

            build {
                sourceDirectory("\${project.basedir}/../../src/main/java")

                resources {
                    resource {
                        directory("\${project.basedir}/../../src/main/resources")
                        filtering("false")
                    }
                    resource {
                        directory("\${project.basedir}/../../src/main/resources")
                        filtering("true")
                        includes {
                            include("version.properties")
                        }
                    }
                }

                plugins {
                    writeOpenapiPlugin(xml)
                    writeMsgfmtPlugin(xml)
                    writeBuildHelperPlugin(xml)
                    writeCompilerPlugin(xml, jpaModelgenVersion)

                    plugin {
                        groupId("org.apache.maven.plugins")
                        artifactId("maven-resources-plugin")
                        delegate.version("\${maven-resources-plugin.version}")
                        configuration {
                            encoding("UTF-8")
                        }
                        executions {
                            execution {
                                id("copy-api-spec")
                                phase("generate-resources")
                                goals { goal("copy-resources") }
                                configuration {
                                    outputDirectory(
                                        "\${project.build.directory}/\${project.build.finalName}/docs")
                                    resources {
                                        resource {
                                            directory("\${project.basedir}/../../api")
                                            includes {
                                                include("candlepin-api-spec.yaml")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    plugin {
                        groupId("org.apache.maven.plugins")
                        artifactId("maven-war-plugin")
                        delegate.version("\${maven-war-plugin.version}")
                        configuration {
                            warSourceDirectory(
                                "\${project.basedir}/../../src/main/webapp")
                            webResources {
                                resource {
                                    directory("\${project.basedir}/../../")
                                    includes { include("LICENSE") }
                                    targetPath("META-INF")
                                }
                            }
                            packagingExcludes("**/testext/**")
                        }
                    }
                }
            }

            dependencies {
                allDeclaredDeps.each { DeclaredDep dep ->
                    dependency {
                        groupId(dep.group)
                        artifactId(dep.name)
                        if (dep.classifier) {
                            classifier(dep.classifier)
                        }
                        if (dep.mavenScope != "compile") {
                            scope(dep.mavenScope)
                        }
                        if (dep.exclusions) {
                            exclusions {
                                dep.exclusions.each { exc ->
                                    exclusion {
                                        groupId(exc.groupId)
                                        artifactId(exc.artifactId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pomFile.setText(sw.toString(), "UTF-8")
        logger.lifecycle("Generated: ${pomFile.canonicalPath}")
    }

    // -------------------------------------------------------------------------
    // API JAR module pom
    // -------------------------------------------------------------------------

    /**
     * Writes the API JAR module pom.xml with compile/provided dependencies only and
     * a filtered jar-plugin include list.
     */
    void writeApiPom(File baseDir, String projectVersion, List<DeclaredDep> allDeclaredDeps,
            List<String> apiJarIncludes) {
        File pomFile = new File(baseDir, "maven/api/pom.xml")
        String jpaModelgenVersion = resolveAnnotationProcessorVersion("hibernate-jpamodelgen")
        List<DeclaredDep> apiDeps = allDeclaredDeps.findAll { it.mavenScope in ["compile", "provided"] }

        StringWriter sw = new StringWriter()
        MarkupBuilder xml = new MarkupBuilder(new IndentPrinter(sw, "    "))
        xml.doubleQuotes = true
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")

        xml."project"(
            xmlns: "http://maven.apache.org/POM/4.0.0",
            "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
            "xsi:schemaLocation": "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
        ) {
            modelVersion("4.0.0")
            parent {
                groupId("org.candlepin")
                artifactId("candlepin-parent")
                delegate.version(projectVersion)
                relativePath("../../pom.xml")
            }
            artifactId("candlepin-api")
            packaging("jar")
            name("Candlepin API JAR")
            delegate.description("Candlepin API interfaces and model classes")

            build {
                sourceDirectory("\${project.basedir}/../../src/main/java")

                resources {
                    resource {
                        directory("\${project.basedir}/../../src/main/resources")
                        filtering("false")
                    }
                }

                plugins {
                    writeOpenapiPlugin(xml)
                    writeMsgfmtPlugin(xml)
                    writeBuildHelperPlugin(xml)
                    writeCompilerPlugin(xml, jpaModelgenVersion)

                    plugin {
                        groupId("org.apache.maven.plugins")
                        artifactId("maven-jar-plugin")
                        delegate.version("\${maven-jar-plugin.version}")
                        configuration {
                            includes {
                                apiJarIncludes.each { String pkg ->
                                    include(pkg)
                                }
                            }
                        }
                    }
                }
            }

            dependencies {
                apiDeps.each { DeclaredDep dep ->
                    dependency {
                        groupId(dep.group)
                        artifactId(dep.name)
                        if (dep.classifier) {
                            classifier(dep.classifier)
                        }
                        if (dep.mavenScope == "provided") {
                            scope("provided")
                        }
                        if (dep.exclusions) {
                            exclusions {
                                dep.exclusions.each { exc ->
                                    exclusion {
                                        groupId(exc.groupId)
                                        artifactId(exc.artifactId)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        pomFile.setText(sw.toString(), "UTF-8")
        logger.lifecycle("Generated: ${pomFile.canonicalPath}")
    }

}

/**
 * Definition of the PomGenerator plugin. It registers the PomGeneratorTask, which defines the 'generatePom' custom gradle command.
 */
class PomGenerator implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("pomGenerator", PomGeneratorExtension)
        project.tasks.register("generatePom", PomGeneratorTask) {
            group = "build"
            description = "Generate Maven multi-module pom.xml files from Gradle configuration"
        }
    }
}
