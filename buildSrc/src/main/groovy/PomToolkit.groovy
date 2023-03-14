/*
 *  Copyright (c) 2009 - 2019 Red Hat, Inc.
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


import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.Plugin
import org.gradle.api.Project

class PomToolkit implements Plugin<Project> {

    void apply(Project project) {
    }

    public static void generatePomDependencies(
        Node parent,
        DefaultDependencySet compileOnlyDependencies,
        DefaultDependencySet implementationDependencies,
        DefaultDependencySet providedCompileDependencies,
        DefaultDependencySet runtimeOnlyDependencies,
        DefaultDependencySet testImplementationDependencies,
        DefaultDependencySet testRuntimeDependencies) {

        def dependenciesNode = parent.appendNode('dependencies')
        createDependencyNode(dependenciesNode, 'org.mozilla', 'jss', '${jssVersion}', 'system', '${jssLocation}')

        compileOnlyDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'provided', null)
        }

        implementationDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'compile', null)
        }

        providedCompileDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'provided', null)
        }

        runtimeOnlyDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'runtime', null)
        }

        testImplementationDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'test', null)
        }

        testRuntimeDependencies.each { dependency ->
            createDependencyNode(dependenciesNode, dependency.group, dependency.name, dependency.version, 'test', null)
        }
    }

    public static void generateRepositories(Node parent) {
        def repositoriesNode = parent.appendNode('repositories')
        createRepoNode(repositoriesNode, 'jboss', 'JBoss', 'https://repository.jboss.org/nexus/content/groups/public/')
        createRepoNode(repositoriesNode, 'oauth', 'Oauth', 'https://oauth.googlecode.com/svn/code/maven/')
        createRepoNode(repositoriesNode, 'awood', 'awood.fedorapeople.org', 'https://awood.fedorapeople.org/ivy/candlepin/')
        createRepoNode(repositoriesNode, 'barnabycourt', 'barnabycourt.fedorapeople.org', 'https://barnabycourt.fedorapeople.org/repo/candlepin/')
    }

    public static void generateBuild(Node parent, DefaultDependencySet annotationProcessorDependencies) {
        def buildNode = parent.appendNode('build')
        def resourcesNode = buildNode.appendNode('resources')
        def resource1 = resourcesNode.appendNode('resource')
        resource1.appendNode('filtering', 'true')
        resource1.appendNode('directory', 'src/main/resources')

        def resource2 = resourcesNode.appendNode('resource')
        resource2.appendNode('filtering', 'true')
        resource2.appendNode('directory', '${project.build.directory}/generated-resources')

        def testResourcesNode = buildNode.appendNode('testResources')
        def testResource = testResourcesNode.appendNode('testResource')
        testResource.appendNode('filtering', 'false')
        testResource.appendNode('directory', 'src/test/resources')

        def pluginsNode = buildNode.appendNode('plugins')
        createOpenapiGeneratorPlugin(pluginsNode)
        createMavenProcessorPlugin(pluginsNode)
        createBuilderHelperMavenPlugin(pluginsNode)
        createGettextMavenPlugin(pluginsNode)
        createMavenResourcePlugin(pluginsNode)
        createMavenCompilerPlugin(pluginsNode, annotationProcessorDependencies)
        createMavenSurefirePlugin(pluginsNode)
        createMavenAssemblyPlugin(pluginsNode)
        createMavenCleanPlugin(pluginsNode)
        createMavenHelpPlugin(pluginsNode)
    }

    public static void generateProfiles(Node parent) {
        def profilesNode = parent.appendNode('profiles')
        createProfileNode(profilesNode, 'build-with-jss4', '/usr/lib64/jss/jss4.jar', '4.9.1')
        createProfileNode(profilesNode, 'build-with-jss5-plus', '/usr/lib64/jss/jss.jar', '5.0.0')
    }

    public static void generateDependencyManagement(Node parent) {
        def depManagementNode = parent.appendNode('dependencyManagement')
        def depsNode = depManagementNode.appendNode('dependencies')
        /* This is to avoid issue https://groups.google.com/g/ehcache-users/c/U1bO6QArswQ where ehcache has set a
         * version range for the jaxb-runtime dependency, and maven is trying to resolve all transitive depencency
         * versions in that range and failing due to some of them pointing to insecure maven repos.
         *
         * The solution here is to pin the jaxb-runtime lib to a specific version.
         */
        createDependencyNode(depsNode, 'org.glassfish.jaxb', 'jaxb-runtime', '2.3.6', null, null)
    }

    private static void createDependencyNode(Node parentNode, String groupId, String artifactId, String version, String scope, String systemPath) {
        if (groupId) {
            def dependencyNode = parentNode.appendNode('dependency')
            dependencyNode.appendNode('groupId', groupId)
            dependencyNode.appendNode('artifactId', artifactId)
            if (version != null)
                dependencyNode.appendNode('version', version)
            if (scope != null)
                dependencyNode.appendNode('scope', scope)
            if (systemPath != null)
                dependencyNode.appendNode('systemPath', systemPath)
        }
    }

    private static void createProfileNode(Node parentNode, String id, String jarLocation, String version) {
        def profileNode = parentNode.appendNode('profile')
        profileNode.appendNode('id', id)
        def profileActivationNode = profileNode.appendNode('activation')
        def activationFileNode = profileActivationNode.appendNode('file')
        activationFileNode.appendNode('exists', jarLocation)
        def profilePropertiesNode = profileNode.appendNode('properties')
        profilePropertiesNode.appendNode('jssVersion', version)
        profilePropertiesNode.appendNode('jssLocation', jarLocation)
    }

    private static void createRepoNode(Node parentNode, String id, String name, String url) {
        def repoNode = parentNode.appendNode('repository')
        repoNode.appendNode('id', id)
        repoNode.appendNode('name', name)
        repoNode.appendNode('url', url)
    }

    private static void createOpenapiGeneratorPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('groupId', 'org.openapitools')
        plugin.appendNode('artifactId', 'openapi-generator-maven-plugin')
        plugin.appendNode('version', '6.3.0')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'generate')
        def configurationsNode = executionNode.appendNode('configuration')
        configurationsNode.appendNode('inputSpec', '${project.basedir}/api/candlepin-api-spec.yaml')
        configurationsNode.appendNode('generatorName', 'jaxrs-spec')
        configurationsNode.appendNode('configurationFile', '${project.basedir}/api/candlepin-api-config.json')
        def configOptions = configurationsNode.appendNode('configOptions')
        configOptions.appendNode('interfaceOnly', 'true')
        configOptions.appendNode('generatePom', 'false')
        configOptions.appendNode('dateLibrary', 'java8')
        configOptions.appendNode('useTags', 'true')
        configOptions.appendNode('sourceFolder', 'src/gen/java/main')
        configurationsNode.appendNode('templateDirectory', '${project.basedir}/buildSrc/src/main/resources/templates')
    }

    private static void createMavenProcessorPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('groupId', 'org.bsc.maven')
        plugin.appendNode('artifactId', 'maven-processor-plugin')
        plugin.appendNode('version', '5.0-jdk8-rc1')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('id', 'process')
        executionNode.appendNode('phase', 'generate-sources')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'process')

        def configurationsNode = executionNode.appendNode('configuration')
        configurationsNode.appendNode('outputDirectory', '${project.build.directory}/generated-sources/jpamodel/gen/java')
        def processorsNode = configurationsNode.appendNode('processors')
        processorsNode.appendNode('processor', 'org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor')
    }

    private static void createBuilderHelperMavenPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('groupId', 'org.codehaus.mojo')
        plugin.appendNode('artifactId', 'build-helper-maven-plugin')
        plugin.appendNode('version', '3.2.0')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('id', 'add-source')
        executionNode.appendNode('phase', 'generate-sources')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'add-source')
        def configurationsNode = executionNode.appendNode('configuration')
        def sourcesNode = configurationsNode.appendNode('sources')
        sourcesNode.appendNode('source', '${project.build.directory}/generated-sources/jpamodel/gen/java')
        sourcesNode.appendNode('source', '${project.build.directory}/generated-sources/openapi/src/gen/java')
        sourcesNode.appendNode('source', 'src/main/java')
    }

    private static void createGettextMavenPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('groupId', 'com.googlecode.gettext-commons')
        plugin.appendNode('artifactId', 'gettext-maven-plugin')
        plugin.appendNode('version', '1.2.4')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('phase', 'generate-resources')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'dist')
        def configurationsNode = plugin.appendNode('configuration')
        configurationsNode.appendNode('targetBundle', 'org.candlepin.common.i18n.Messages')
        configurationsNode.appendNode('poDirectory', 'po')
    }

    private static void createMavenResourcePlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-resources-plugin')
        plugin.appendNode('version', '3.1.0')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('phase', 'validate')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'copy-resources')
        def configurationsNode = executionNode.appendNode('configuration')
        configurationsNode.appendNode('outputDirectory', 'src/main/webapp/docs/')
        def resourcesNode = configurationsNode.appendNode('resources')
        def resourceNode = resourcesNode.appendNode('resource')
        resourceNode.appendNode('directory', '${basedir}/api')
        def includesNode = resourceNode.appendNode('includes')
        includesNode.appendNode('include', 'candlepin-api-spec.yaml')
    }

    private static void createMavenCompilerPlugin(Node parentNode, DefaultDependencySet annotationProcessorDependencies) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-compiler-plugin')
        plugin.appendNode('version', '3.8.0')
        def configurationsNode = plugin.appendNode('configuration')
        configurationsNode.appendNode('source', '17')
        configurationsNode.appendNode('target', '17')
        configurationsNode.appendNode('debug', 'true')
        configurationsNode.appendNode('debuglevel', 'lines,vars,source')
        configurationsNode.appendNode('compilerArgument', '-proc:none')

        def annotationProcessors = configurationsNode.appendNode('annotationProcessorPaths')
        annotationProcessorDependencies.each { dependency ->
            createAnnotationProcessorPath(annotationProcessors, dependency.group, dependency.name, dependency.version)
        }
    }

    private static void createAnnotationProcessorPath(Node parent, String groupId, String artifactId, String version) {
        if (groupId) {
            def annotationProcessorPath = parent.appendNode('annotationProcessorPath')
            annotationProcessorPath.appendNode('groupId', groupId)
            annotationProcessorPath.appendNode('artifactId', artifactId)
            if (version != null) {
                annotationProcessorPath.appendNode('version', version)
            }
        }
    }

    private static void createMavenSurefirePlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-surefire-plugin')
        plugin.appendNode('version', '2.22.1')
        def configurationsNode = plugin.appendNode('configuration')
        configurationsNode.appendNode('skipTests', 'true')
    }

    private static void createMavenAssemblyPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-assembly-plugin')
        plugin.appendNode('version', '2.4')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('id', 'create-archive')
        executionNode.appendNode('phase', 'package')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'single')
        def configurationsNode = plugin.appendNode('configuration')
        def descriptorsNode = configurationsNode.appendNode('descriptors')
        descriptorsNode.appendNode('descriptor', '${project.basedir}/assembly.xml')
    }

    private static void createMavenCleanPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-clean-plugin')
        plugin.appendNode('version', '3.1.0')
        def configurationsNode = plugin.appendNode('configuration')
        def fileSetsNode = configurationsNode.appendNode('filesets')
        def fileSetNode = fileSetsNode.appendNode('fileset')
        fileSetNode.appendNode('directory', 'src/main/webapp/docs/')
        def includesNode = fileSetNode.appendNode('includes')
        includesNode.appendNode('include', 'candlepin-api-spec.yaml')
    }

    private static void createMavenHelpPlugin(Node parentNode) {
        def plugin = parentNode.appendNode('plugin')
        plugin.appendNode('artifactId', 'maven-help-plugin')
        plugin.appendNode('version', '3.2.0')
        def executionsNode = plugin.appendNode('executions')
        def executionNode = executionsNode.appendNode('execution')
        executionNode.appendNode('id', 'show-profiles')
        executionNode.appendNode('phase', 'compile')
        def goalsNode = executionNode.appendNode('goals')
        goalsNode.appendNode('goal', 'active-profiles')
    }
}