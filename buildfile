# vi: set ft=ruby:

### Repositories
repositories.remote << "http://awood.fedorapeople.org/ivy/candlepin/"
repositories.remote << "http://repository.jboss.org/nexus/content/groups/public/"
repositories.remote << "https://repo.maven.apache.org/maven2/"
repositories.remote << "http://central.maven.org/maven2/"

require 'date'
require 'json'
require 'net/http'
require 'rspec/core/rake_task'

# Don't require findbugs by default.
# needs "buildr-findBugs" gem installed
# (and findbugs and its large set of deps)
findbugs = ENV['findbugs']
unless findbugs.nil?
  require 'buildr-findBugs'
end

use_pmd = ENV['pmd']
unless use_pmd.nil?
  require 'buildr/pmd'
end

### Dependencies
RESTEASY = [group('jaxrs-api',
                  'resteasy-jaxrs',
                  'resteasy-jaxb-provider',
                  'resteasy-guice',
                  'resteasy-atom-provider',
                  'resteasy-multipart-provider',
                  :under => 'org.jboss.resteasy',
                  # XXX: this version requires us to use
                  # ContentTypeHackFilter.java when updating,
                  # please check if its still needed, and remove if not.
                  #
                  # Note that we can go no higher.  Later versions require
                  # servlet spec 3.0 which Tomcat 6 does not support
                  :version => '3.0.10.Final'),
            'org.scannotation:scannotation:jar:1.0.3',
            'org.apache.httpcomponents:httpclient:jar:4.3.2',
            'org.apache.james:apache-mime4j:jar:0.6',
            'javax.mail:mail:jar:1.4.4',
            'javax.ws.rs:javax.ws.rs-api:jar:2.0.1']

JACKSON_NS = "com.fasterxml.jackson"
JACKSON_VERSION = "2.9.4"
JACKSON = [group('jackson-annotations', 'jackson-core', 'jackson-databind',
                 :under=> "#{JACKSON_NS}.core",
                 :version => JACKSON_VERSION),
           group('jackson-jaxrs-base', 'jackson-jaxrs-json-provider',
                 :under=> "#{JACKSON_NS}.jaxrs",
                 :version => JACKSON_VERSION),
           group('jackson-module-jsonSchema', 'jackson-module-jaxb-annotations',
                 :under=> "#{JACKSON_NS}.module",
                 :version => JACKSON_VERSION),
           group('jackson-datatype-hibernate5',
                :under=> "#{JACKSON_NS}.datatype",
                :version => JACKSON_VERSION)]

SUN_JAXB = 'com.sun.xml.bind:jaxb-impl:jar:2.1.12'

CORE_TESTING = [
  'junit:junit:jar:4.12',
  'org.hamcrest:hamcrest-library:jar:1.3',
  'org.hamcrest:hamcrest-core:jar:1.3',
  'org.mockito:mockito-all:jar:1.9.5',
  'pl.pragmatists:JUnitParams:jar:1.0.3',
]
Buildr.settings.build['junit'] = '4.12' # Buildr uses 4.11 by default

JUKITO = ['org.jukito:jukito:jar:1.4']

LOGBACK = [group('logback-core', 'logback-classic',
                 :under => 'ch.qos.logback',
                 :version => '1.2.3')]

# Artifacts that bridge other logging frameworks to slf4j. Mime4j uses
# JCL for example.
SLF4J_BRIDGES = [group('jcl-over-slf4j', 'log4j-over-slf4j',
                       :under => 'org.slf4j',
                       :version => '1.7.12')]
SLF4J = 'org.slf4j:slf4j-api:jar:1.7.25'

LOGGING = [LOGBACK, SLF4J_BRIDGES, SLF4J]

JAVAX = ['org.hibernate.javax.persistence:hibernate-jpa-2.1-api:jar:1.0.0.Final',
         'javax.transaction:jta:jar:1.1']
ANTLR = ['antlr:antlr:jar:2.7.7']

EHCACHE = ['org.hibernate:hibernate-ehcache:jar:5.1.1.Final',
           'net.sf.ehcache:ehcache:jar:2.10.1',
           'org.ehcache:jcache:jar:1.0.0', 'javax.cache:cache-api:jar:1.0.0',
           'net.sf.ehcache:management-ehcache-v2:jar:2.10.1']

VALIDATOR = ['org.hibernate.validator:hibernate-validator:jar:6.0.7.Final',
             'org.hibernate.validator:hibernate-validator-annotation-processor:jar:6.0.7.Final',
             'javax.validation:validation-api:jar:2.0.1.Final']

HIBERNATE = [group('hibernate-core', 'hibernate-entitymanager', 'hibernate-c3p0',
                   :under => 'org.hibernate',
                   :version => '5.1.1.Final'),
             'org.hibernate.common:hibernate-commons-annotations:jar:5.0.1.Final',
             'org.hibernate:hibernate-tools:jar:3.2.4.GA',
             ANTLR,
             EHCACHE,
             VALIDATOR,
             'asm:asm:jar:3.0',
             'cglib:cglib:jar:2.2',
             'org.javassist:javassist:jar:3.20.0-GA',
             'com.fasterxml:classmate:jar:1.3.0',
             'org.freemarker:freemarker:jar:2.3.15',
             'com.mchange:c3p0:jar:0.9.5.2',
             'com.mchange:mchange-commons-java:jar:0.2.11',
             'dom4j:dom4j:jar:1.6.1',
             'org.jboss:jandex:jar:2.0.0.Final',
              'org.jboss.logging:jboss-logging:jar:3.3.0.Final'
             ] + JAVAX

POSTGRESQL = 'org.postgresql:postgresql:jar:42.2.2'

SWAGGER = [group('swagger-jaxrs', 'swagger-core','swagger-models','swagger-annotations',
                     :under => 'io.swagger',
                     :version => '1.5.7'),
          'org.reflections:reflections:jar:0.9.10',
          'org.apache.commons:commons-lang3:jar:3.2.1',
           group('jackson-dataformat-yaml', 'jackson-dataformat-xml',
                  :under => 'com.fasterxml.jackson.dataformat',
                  :version => JACKSON_VERSION)
         ]

MYSQL = 'mysql:mysql-connector-java:jar:5.1.26'

DB = [POSTGRESQL, MYSQL]

HSQLDB = 'org.hsqldb:hsqldb:jar:2.3.2'
HSQLDB_OLD = 'org.hsqldb:hsqldb:jar:1.8.0.10'

ORACLE = ['com.oracle:ojdbc6:jar:11.2.0', 'org.quartz-scheduler:quartz-oracle:jar:2.1.5']

COMMONS = ['commons-codec:commons-codec:jar:1.4',
           'commons-collections:commons-collections:jar:3.2.2',
           'commons-io:commons-io:jar:1.4',
           'commons-lang:commons-lang:jar:2.5']

LIQUIBASE = 'org.liquibase:liquibase-core:jar:3.1.0'
LIQUIBASE_SLF4J = 'com.mattbertolini:liquibase-slf4j:jar:1.2.1'

GETTEXT_COMMONS = 'com.googlecode.gettext-commons:gettext-commons:jar:0.9.8'

BOUNCYCASTLE = group('bcpkix-jdk15on', 'bcprov-jdk15on',
                     :under => 'org.bouncycastle',
                     :version => '1.59')

SERVLET = 'javax.servlet:servlet-api:jar:2.5'

GUICE =  [group('guice-assistedinject', 'guice-multibindings',
                'guice-servlet', 'guice-throwingproviders', 'guice-persist',
                :under=>'com.google.inject.extensions',
                :version=>'3.0'),
           'com.google.inject:guice:jar:3.0',
           'aopalliance:aopalliance:jar:1.0',
           'javax.inject:javax.inject:jar:1']

COLLECTIONS = 'com.google.guava:guava:jar:19.0'

OAUTH= [group('oauth',
              'oauth-provider',
              :under => 'net.oauth.core',
              :version => '20100527')]

QUARTZ = 'org.quartz-scheduler:quartz:jar:2.2.1'

ACTIVEMQ = [group('artemis-server',
                  'artemis-core-client',
                  'artemis-commons',
                  'artemis-selector',
                  'artemis-journal',
                  :under => 'org.apache.activemq',
                  :version => '2.4.0'),
            'io.netty:netty-all:jar:4.1.16.Final',
            'commons-beanutils:commons-beanutils:jar:1.9.3',
            # 'commons-logging:commons-logging:jar:1.2',
            'org.jgroups:jgroups:jar:3.6.13.Final',
            'org.apache.geronimo.specs:geronimo-json_1.0_spec:jar:1.0-alpha-1',
            'org.apache.johnzon:johnzon-core:jar:0.9.5'
           ]

SCHEMASPY = 'net.sourceforge:schemaSpy:jar:4.1.1'

AMQP  = [group('qpid-common', 'qpid-client',
               :under => 'org.apache.qpid',
               :version => '0.32'),
         group('mina-core', 'mina-filter-ssl',
               :under => 'org.apache.mina',
               :version => '1.0.1'),
         'geronimo-spec:geronimo-spec-jms:jar:1.1-rc4']

RHINO = 'org.mozilla:rhino:jar:1.7R3'

# required by LOGDRIVER
LOG4J_BRIDGE = 'org.slf4j:log4j-over-slf4j:jar:1.7.5'
LOGDRIVER = 'logdriver:logdriver:jar:1.0'

# servlet-api is provided by the servlet container and Tomcat won't
# even load servlet API classes seen in WEB-INF/lib.  See section 9.7.2 of
# Servlet Spec 2.4 and http://stackoverflow.com/questions/15601469
PROVIDED = [SERVLET]

# We need to mark JAVA_HOME/lib/tools.jar as a dependency in order for
# Buildr to include it in the Eclipse .classpath file.
JAVA_TOOLS = file(Java.tools_jar)

SCANNOTATION = 'org.scannotation:scannotation:jar:1.0.3'

CHECKSTYLE = ['com.puppycrawl.tools:checkstyle:jar:8.2',
              'org.antlr:antlr4-runtime:jar:4.5.3'] + ANTLR

# Make Util available in all projects.  See http://buildr.apache.org/extending.html#extensions
class Project
  include Candlepin::Util
end

def enhance_checkstyle_task
  task('checkstyle:plain').enhance([project('checks').build])
end

### Project
desc "The Candlepin Project"
define "candlepin" do
  project.group = "org.candlepin"
  project.version = pom_version(path_to('pom.xml'))
  manifest["Copyright"] = "Red Hat, Inc. #{Date.today.strftime('%Y')}"

  compile.using(:debug => true, :source => '1.8', :target => '1.8')

  desc "Custom Checkstyle checks for candlepin"
  define "checks" do
    pom.name = "Candlepin Style Checks"
    pom.description = "Custom Checkstyle checks for Candlepin"
    project.version = '0.3'
    eclipse.natures :java
    package(:jar)

    compile_classpath = [
      CHECKSTYLE,
    ]

    compile.with(compile_classpath)
  end

  # path_to() (and it's alias _()) simply provides the absolute path to
  # a directory relative to the project.
  # See http://buildr.apache.org/rdoc/Buildr/Project.html#method-i-path_to
  checkstyle_config_directory = path_to(:project_conf)
  checkstyle_eclipse_xml = path_to(:project_conf, 'eclipse-checkstyle.xml')
  checkstyle_extra_dependencies = project('checks').path_to('target', 'classes')
  rpmlint_conf = path_to("rpmlint.config")
  rubocop.patterns = ['*.rb']

  use_logdriver = ENV['logdriver']
  if use_logdriver
    info "Compiling with logdriver"
    download artifact(LOGDRIVER) => 'http://awood.fedorapeople.org/ivy/candlepin/logdriver/logdriver/1.0/logdriver-1.0.jar'
  end
  download artifact(SCHEMASPY) => 'http://downloads.sourceforge.net/project/schemaspy/schemaspy/SchemaSpy%204.1.1/schemaSpy_4.1.1.jar'
  include_hostedtest = ENV['hostedtest']

  desc "Common Candlepin Code"
  define "common" do
    pom.name = "Candlepin Common"
    pom.description = "Common code for Candlepin"

    project.version = spec_version('candlepin-common.spec.tmpl')

    eclipse.natures :java

    checkstyle.config_directory = checkstyle_config_directory
    checkstyle.eclipse_xml = checkstyle_eclipse_xml
    checkstyle.extra_dependencies << checkstyle_extra_dependencies
    enhance_checkstyle_task
    rpmlint.rpmlint_conf = rpmlint_conf

    msgfmt.resource = "#{project.group}.common.i18n.Messages"

    compile_classpath = [
      COMMONS,
      LOGGING,
      GUICE,
      GETTEXT_COMMONS,
      COLLECTIONS,
      PROVIDED,
      RESTEASY,
      JACKSON,
      JAVAX,
      OAUTH,
      VALIDATOR,
    ]
    compile.with(compile_classpath)

    test_classpath = [
      CORE_TESTING,
      JUKITO,
      LIQUIBASE,
      LIQUIBASE_SLF4J,
    ]
    test.with(test_classpath)
    test.using :java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ]

    package(:jar).tap do |jar|
      jar.include(:from => msgfmt.destination)
    end

    pom.plugin_procs << Proc.new do |xml, proj|
      xml.groupId("com.googlecode.gettext-commons")
      xml.artifactId("gettext-maven-plugin")
    end

    pom.plugin_procs << Proc.new do |xml, proj|
      xml.groupId("org.owasp")
      xml.artifactId("dependency-check-maven")
    end
  end

  desc "The Candlepin Server"
  define "server" do
    pom.name = "Candlepin"
    pom.description = "The Candlepin Entitlement Engine"

    spec_file = "candlepin.spec.tmpl"
    project.version = spec_version(spec_file)
    release_number = spec_release(spec_file)

    checkstyle.config_directory = checkstyle_config_directory
    checkstyle.eclipse_xml = checkstyle_eclipse_xml
    checkstyle.extra_dependencies << checkstyle_extra_dependencies

    swagger.enabled = true
    swagger.json_source = "https://localhost:8443/candlepin/swagger.json"
    swagger.config_options = { 'gemName' => 'candlepin_client', 'moduleName' => 'Candlepin' }

    enhance_checkstyle_task
    rpmlint.rpmlint_conf = rpmlint_conf
    liquibase.changelogs = ['changelog-update.xml', 'changelog-create.xml', 'changelog-testing.xml']
    liquibase.file_time_prefix_format = "%Y%m%d%H%M%S"

    gettext.keys_destination = project("common").gettext.keys_destination

    rubocop.patterns = ['server/client/ruby/candlepin.rb',
                        #'server/spec/*.rb',
                        'server/client/ruby/test/*.rb']

    # eclipse settings
    # http://buildr.apache.org/more_stuff.html#eclipse
    eclipse.natures :java

    # IntelliJ IDEA settings
    iml.add_web_facet
    iml.add_jpa_facet

    resource_substitutions = {
      'version' => project.version,
      'release' => release_number,
    }
    resources.filter.using(resource_substitutions)
    test.resources.filter.using(resource_substitutions)

    unless use_pmd.nil?
      pmd.enabled = true
    end

    msgfmt.resource = "#{project.group}.server.i18n.Messages"

    ### Building
    compile_classpath = [
      AMQP,
      BOUNCYCASTLE,
      COLLECTIONS,
      COMMONS,
      GETTEXT_COMMONS,
      GUICE,
      HIBERNATE,
      ACTIVEMQ,
      JACKSON,
      LIQUIBASE,
      LOGGING,
      OAUTH,
      PROVIDED,
      QUARTZ,
      RESTEASY,
      RHINO,
      SUN_JAXB,
      SWAGGER,
    ]

    compile.with(compile_classpath)
    compile.with(project('common'))
    compile.with(LOGDRIVER, LOG4J_BRIDGE) if use_logdriver

    if Buildr.environment == 'oracle'
      compile.with(ORACLE)
    else
      compile.with(DB)
    end

    ### Testing
    test.setup do |task|
      filter(path_to(:src, :main, :resources)).into(path_to(:target, :classes)).run
    end

    test_classpath = [
      CORE_TESTING,
      JUKITO,
      HSQLDB,
      LIQUIBASE_SLF4J,
    ]
    # The entries in test_classpath will be supplemented automatically with the compile_classpath
    test.with(test_classpath)
    test.using(:java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ])

    ### Javadoc
    doc.using :tag => 'httpcode:m:HTTP Code:'

    ### Packaging
    # NOTE: changes here must also be made in build.xml!
    candlepin_path = "org/candlepin"
    compiled_cp_path = "#{compile.target}/#{candlepin_path}"

    # We need to add this dependency so that the Maven assembly plugin will
    # include the source of the common project in the final assembly.
    common = project('common')
    pom.dependency_procs << Proc.new do |xml, project|
      xml.groupId(common.group)
      xml.artifactId(common.id)
      xml.version(common.version)
      xml.classifier("complete")
      xml.type("tar.gz")
      xml.scope("provided")
    end
    pom.additional_properties['release'] = release_number
    pom.provided_dependencies.concat(PROVIDED)

    package(:jar, :id=>'candlepin-api').tap do |jar|
      jar.clean
      pkgs = %w{auth config jackson model pki resteasy service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
      p = jar.path(candlepin_path)
      p.include(pkgs)
    end

    package(:jar, :id=>"candlepin-certgen").tap do |jar|
      jar.clean
      pkgs = %w{config jackson model pinsetter pki service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
      p = jar.path(candlepin_path)
      p.include(pkgs)
    end

    package(:war, :id=>"candlepin").tap do |war|
      war.libs += artifacts(HSQLDB)
      war.libs -= artifacts(PROVIDED)
      war.classes.clear
      war.classes << resources.target
      war.classes << msgfmt.destination if msgfmt.enabled?
      web_inf = war.path('WEB-INF/classes')
      web_inf.path(candlepin_path).include("#{compiled_cp_path}/**")
      web_inf.path(candlepin_path).exclude('*hostedtest*') unless include_hostedtest
    end

    pom.plugin_procs << Proc.new do |xml, proj|
      xml.groupId("org.owasp")
      xml.artifactId("dependency-check-maven")
    end

    desc 'Create an html report of the schema'
    task :schemaspy do
     cp = Buildr.artifacts(DB, SCHEMASPY).each(&:invoke).map(&:name).join(File::PATH_SEPARATOR)
     command = "-t pgsql -db candlepin -s public -host localhost -u candlepin -p candlepin -o target/schemaspy"
     ant('java') do |ant|
       ant.java(:classname => "net.sourceforge.schemaspy.Main", :classpath => cp, :fork => true) do |java|
         command.split(/\s+/).each {|value| ant.arg :value => value}
       end
     end
    end
  end
end

desc 'Run all the linters'
task :lint => [:checkstyle, :rubocop, :rpmlint]

desc 'Make sure eventhing is working as it should'
task :check_all => [:clean, :lint, :validate_translation, :test]

desc 'Miscellaneous validation tasks to be run on jenkins with every pull request'
task :jenkins => [:validate_translation]
