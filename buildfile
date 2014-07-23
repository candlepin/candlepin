# vi: set ft=ruby:

### Repositories
repositories.remote << "http://jmrodri.fedorapeople.org/ivy/candlepin/"
repositories.remote << "http://repository.jboss.org/nexus/content/groups/public/"
repositories.remote << "http://gettext-commons.googlecode.com/svn/maven-repository/"
repositories.remote << "http://oauth.googlecode.com/svn/code/maven/"
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
                  :version => '2.3.7.Final'),
            'org.scannotation:scannotation:jar:1.0.2',
            'org.apache.httpcomponents:httpclient:jar:4.1.2',
            'org.apache.james:apache-mime4j:jar:0.6',
            'javax.mail:mail:jar:1.4']

JACKSON_NS = "com.fasterxml.jackson"
JACKSON_VERSION = "2.3.0"
JACKSON = [group('jackson-annotations', 'jackson-core', 'jackson-databind',
                 :under=> "#{JACKSON_NS}.core",
                 :version => JACKSON_VERSION),
           group('jackson-jaxrs-base', 'jackson-jaxrs-json-provider',
                 :under=> "#{JACKSON_NS}.jaxrs",
                 :version => JACKSON_VERSION),
           group('jackson-module-jsonSchema', 'jackson-module-jaxb-annotations',
                 :under=> "#{JACKSON_NS}.module",
                 :version => JACKSON_VERSION),
           group('jackson-datatype-hibernate4',
                :under=> "#{JACKSON_NS}.datatype",
                :version => JACKSON_VERSION)]

SUN_JAXB = 'com.sun.xml.bind:jaxb-impl:jar:2.1.12'

TESTING = Buildr.transitive(['junit:junit:jar:4.11', 'org.mockito:mockito-all:jar:1.9.5'])
JUKITO = Buildr.transitive(['org.jukito:jukito:jar:1.4'])

LOGBACK = [group('logback-core', 'logback-classic',
                 :under => 'ch.qos.logback',
                 :version => '1.0.13')]

# Artifacts that bridge other logging frameworks to slf4j. Mime4j uses
# JCL for example.
SLF4J_BRIDGES = 'org.slf4j:jcl-over-slf4j:jar:1.7.5'
SLF4J = 'org.slf4j:slf4j-api:jar:1.7.5'

LOGGING = [LOGBACK, SLF4J_BRIDGES, SLF4J]

HIBERNATE = [group('hibernate-core', 'hibernate-entitymanager', 'hibernate-c3p0',
                   :under => 'org.hibernate',
                   :version => '4.2.5.Final'),
             'org.hibernate.common:hibernate-commons-annotations:jar:4.0.1.Final',
             'org.hibernate:hibernate-tools:jar:3.2.4.GA',
             'org.hibernate:hibernate-validator:jar:4.3.1.Final',
             'org.hibernate.javax.persistence:hibernate-jpa-2.0-api:jar:1.0.1.Final',
             'antlr:antlr:jar:2.7.7',
             'asm:asm:jar:3.0',
             'cglib:cglib:jar:2.2',
             'javassist:javassist:jar:3.12.0.GA',
             'javax.transaction:jta:jar:1.1',
             'org.freemarker:freemarker:jar:2.3.15',
             'c3p0:c3p0:jar:0.9.1.2',
             'dom4j:dom4j:jar:1.6.1',
             'org.jboss.logging:jboss-logging:jar:3.1.1.GA',
             'javax.validation:validation-api:jar:1.0.0.GA']

POSTGRESQL = 'postgresql:postgresql:jar:9.0-801.jdbc4'

MYSQL = 'mysql:mysql-connector-java:jar:5.1.26'

DB = [POSTGRESQL, MYSQL]

HSQLDB = 'hsqldb:hsqldb:jar:1.8.0.10'

ORACLE = ['com.oracle:ojdbc6:jar:11.2.0', 'org.quartz-scheduler:quartz-oracle:jar:2.1.5']

COMMONS = ['commons-codec:commons-codec:jar:1.4',
           'commons-collections:commons-collections:jar:3.1',
           'commons-io:commons-io:jar:1.3.2',
           'commons-lang:commons-lang:jar:2.5']

LIQUIBASE = 'org.liquibase:liquibase-core:jar:3.1.0'

GETTEXT_COMMONS = 'org.xnap.commons:gettext-commons:jar:0.9.6'

BOUNCYCASTLE = 'org.bouncycastle:bcprov-jdk16:jar:1.46'

SERVLET = 'javax.servlet:servlet-api:jar:2.5'

GUICE =  [group('guice-assistedinject', 'guice-multibindings',
                'guice-servlet', 'guice-throwingproviders', 'guice-persist',
                :under=>'com.google.inject.extensions',
                :version=>'3.0'),
           'com.google.inject:guice:jar:3.0',
           'aopalliance:aopalliance:jar:1.0',
           'javax.inject:javax.inject:jar:1']

COLLECTIONS = 'com.google.guava:guava:jar:13.0'

OAUTH= [group('oauth',
              'oauth-provider',
              :under => 'net.oauth.core',
              :version => '20100527')]

QUARTZ = 'org.quartz-scheduler:quartz:jar:2.1.5'

HORNETQ = [group('hornetq-server',
                 'hornetq-core-client',
                 'hornetq-commons',
                 'hornetq-journal',
                 # 'hornetq-resources', #Native libs for libaio
                 :under=>'org.hornetq',
                 :version=>'2.3.5.Final'),
            'org.jboss.netty:netty:jar:3.2.1.Final']

SCHEMASPY = 'net.sourceforge:schemaSpy:jar:4.1.1'

AMQP  = [group('qpid-common', 'qpid-client',
               :under => 'org.apache.qpid',
               :version => '0.22'),
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

# Make Util available in all projects.  See http://buildr.apache.org/extending.html#extensions
class Project
  include Candlepin::Util
end

### Project
desc "The Candlepin Project"
define "candlepin" do
  project.group = "org.candlepin"
  manifest["Copyright"] = "Red Hat, Inc. #{Date.today.strftime('%Y')}"

  compile.options.target = '1.6'
  compile.options.source = '1.6'

  # path_to() (and it's alias _()) simply provides the absolute path to
  # a directory relative to the project.
  # See http://buildr.apache.org/rdoc/Buildr/Project.html#method-i-path_to
  checkstyle_config_directory = path_to(:project_conf)
  checkstyle_eclipse_xml = path_to(:project_conf, 'eclipse-checkstyle.xml')
  rpmlint_conf = path_to("rpmlint.config")

  desc "Common Candlepin Code"
  define "common" do
    project.version = spec_version('candlepin-common.spec')

    eclipse.natures :java

    checkstyle.config_directory = checkstyle_config_directory
    checkstyle.eclipse_xml = checkstyle_eclipse_xml
    rpmlint.rpmlint_conf = rpmlint_conf

    compile_classpath = [COMMONS, LOGGING, GUICE, GETTEXT_COMMONS, COLLECTIONS, PROVIDED, RESTEASY, JACKSON]
    compile.with(compile_classpath)

    test.with(TESTING)
    test.using :java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ]

    package(:jar)
  end

  desc "The Gutterball Reporting Engine"
  define "gutterball" do
    spec_file = "gutterball.spec"
    project.version = spec_version(spec_file)
    release_number = spec_release(spec_file)

    checkstyle.config_directory = checkstyle_config_directory
    checkstyle.eclipse_xml = checkstyle_eclipse_xml
    rpmlint.rpmlint_conf = rpmlint_conf

    eclipse.natures :java

    unless use_pmd.nil?
      pmd.enabled = true
    end

    msgfmt.resource = "#{project.group}.gutterball.i18n.Messages"

    compile_classpath = [
      AMQP,
      COLLECTIONS,
      COMMONS,
      DB,
      GETTEXT_COMMONS,
      GUICE,
      HIBERNATE,
      JACKSON,
      LOGGING,
      PROVIDED,
      RESTEASY,
      RHINO,
      SUN_JAXB,
    ]
    compile.with(compile_classpath)
    compile.with(project('common'))

    resource_substitutions = {
      'version' => project.version,
      'release' => release_number,
    }
    resources.filter.using(resource_substitutions)
    test.resources.filter.using(resource_substitutions)

    test.with(TESTING, JUKITO, HSQLDB)
    test.using :java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ]

    package(:war, :id=>"gutterball").tap do |war|
      war.libs -= artifacts(PROVIDED)
    end
  end

  desc "The Candlepin Server"
  define "server" do
    spec_file = "candlepin.spec"
    project.version = spec_version(spec_file)
    release_number = spec_release(spec_file)

    checkstyle.config_directory = checkstyle_config_directory
    checkstyle.eclipse_xml = checkstyle_eclipse_xml
    rpmlint.rpmlint_conf = rpmlint_conf

    # eclipse settings
    # http://buildr.apache.org/more_stuff.html#eclipse
    eclipse.natures :java

    # Buildr tries to outsmart you and use classpath variables whenever possible.  If
    # we don't do the below, Buildr will add 'JAVA_HOMElib/tools.jar' to the .classpath
    # file, but Eclipse doesn't have JAVA_HOME set as one of its classpath variables by
    # default so the file isn't found.  We will cheat by setting the classpath variable to
    # be exactly the same as the file path.
    tools_location = File.basename(Java.tools_jar)
    eclipse.classpath_variables tools_location.to_sym => tools_location

    use_logdriver = ENV['logdriver']
    info "Compiling with logdriver" if use_logdriver

    # download the stuff we do not have in the repositories
    download artifact(SCHEMASPY) => 'http://downloads.sourceforge.net/project/schemaspy/schemaspy/SchemaSpy%204.1.1/schemaSpy_4.1.1.jar'
    download artifact(LOGDRIVER) => 'http://jmrodri.fedorapeople.org/ivy/candlepin/logdriver/logdriver/1.0/logdriver-1.0.jar' if use_logdriver

    resource_substitutions = {
      'version' => project.version,
      'release' => release_number,
    }
    resources.filter.using(resource_substitutions)
    test.resources.filter.using(resource_substitutions)

    unless use_pmd.nil?
      pmd.enabled = true
    end

    msgfmt.resource = "#{project.group}.i18n.Messages"

    ### Building
    compile_classpath = [
      AMQP,
      BOUNCYCASTLE,
      COLLECTIONS,
      COMMONS,
      GETTEXT_COMMONS,
      GUICE,
      HIBERNATE,
      HORNETQ,
      JACKSON,
      JAVA_TOOLS,
      LIQUIBASE,
      LOGGING,
      OAUTH,
      PROVIDED,
      QUARTZ,
      RESTEASY,
      RHINO,
      SUN_JAXB,
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
      filter(path_to(:src, :main, :resources, 'META-INF')).into(path_to(:target, :classes, 'META-INF')).run
    end

    # the other dependencies transfer from compile.classpath automagically
    test.with(HSQLDB, TESTING)
    test.with(LOGDRIVER, LOG4J_BRIDGE) if use_logdriver
    test.using(:java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ])

    ### Javadoc
    doc.using :tag => 'httpcode:m:HTTP Code:'

    ### Packaging
    # NOTE: changes here must also be made in build.xml!
    candlepin_path = "org/candlepin"
    compiled_cp_path = "#{compile.target}/#{candlepin_path}"

    # The apicrawl package is only used for generating documentation so there is no
    # need to ship it.  Ideally, we'd put apicrawl in its own buildr project but I
    # kept getting complaints about circular dependencies.
    api_jar = package(:jar, :id=>'candlepin-api').tap do |jar|
      jar.clean
      pkgs = %w{auth config exceptions jackson model paging pki resteasy service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
      p = jar.path(candlepin_path)
      p.include(pkgs).exclude("#{compiled_cp_path}/util/apicrawl")
    end
    pom.artifact = api_jar

    package(:jar, :id=>"candlepin-certgen").tap do |jar|
      jar.clean
      pkgs = %w{config exceptions jackson model pinsetter pki service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
      p = jar.path(candlepin_path)
      p.include(pkgs).exclude("#{compiled_cp_path}/util/apicrawl")
    end

    package(:war, :id=>"candlepin").tap do |war|
      war.libs += artifacts(HSQLDB)
      war.libs -= artifacts(PROVIDED)
      war.libs -= artifacts(JAVA_TOOLS)
      war.classes.clear
      war.classes = [msgfmt.destination, resources.target]
      web_inf = war.path('WEB-INF/classes')
      web_inf.include("#{compile.target}/net")
      web_inf.path(candlepin_path).include("#{compiled_cp_path}/**").exclude("#{compiled_cp_path}/util/apicrawl")
    end

    desc 'Crawl the REST API and print a summary.'
    task :apicrawl  do
      options.test = 'no'

      # Join compile classpath with the package jar.
      cp = [path_to(:src, :main, :resources)] | [project.package(:jar)] | compile_classpath
      Java::Commands.java('org.candlepin.util.apicrawl.ApiCrawler', :classpath => cp)

      classes = artifacts(cp).collect do |a|
        task(a.to_s).invoke
        File.expand_path a.to_s
      end

      # Just run the doclet on the *Resource files
      sources = project.compile.sources.collect do |dir|
        Dir["#{dir}/**/*Resource.java"]
      end.flatten

      # Add in the options as the last arg
      sources << {:name => 'Candlepin API',
                  :classpath => classes,
                  :doclet => 'org.candlepin.util.apicrawl.ApiDoclet',
                  :docletpath => [path_to(:target, :classes), classes].flatten.join(File::PATH_SEPARATOR),
                  :output => path_to(:target)}

      Java::Commands.javadoc(*sources)

      api_file = path_to(:target, "candlepin_api.json")
      comments_file = path_to(:target, "candlepin_comments.json")
      api = JSON.parse(File.read(api_file))
      comments = JSON.parse(File.read(comments_file))

      combined = Hash[api.collect { |a| [a['method'], a] }]
      comments.each do |c|
        if combined.has_key? c['method']
          combined[c['method']].merge!(c)
        else
          combined[c['method']] = c
        end
      end

      final = JSON.dump(combined.values.sort_by { |v| v['method'] })
      final_file = path_to(:target, "candlepin_methods.json")
      File.open(final_file, 'w') { |f| f.write final }

      # Cleanup
      rm api_file
      rm comments_file
      info "Wrote Candlepin API to: #{final_file}"
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

desc 'Make sure eventhing is working as it should'
task :check_all => [:clean, :checkstyle, :rpmlint, :test]
