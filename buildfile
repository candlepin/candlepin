require 'buildr/java/emma'
require 'net/http'
require 'rspec/core/rake_task'
require 'json'

nocstyle = ENV['nocheckstyle']
if nocstyle.nil?
  require "./buildr/checkstyle"
end

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
            'org.apache.james:apache-mime4j:jar:0.6']

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
                 :version => JACKSON_VERSION)]

SUN_JAXB = 'com.sun.xml.bind:jaxb-impl:jar:2.1.12'

JUNIT = ['junit:junit:jar:4.5', 'org.mockito:mockito-all:jar:1.8.5']

LOGBACK = [group('logback-core', 'logback-classic',
                 :under => 'ch.qos.logback',
                 :version => '1.0.13')]

# Artifacts that bridge other logging frameworks to slf4j. Mime4j uses
# JCL for example.
SLF4J_BRIDGES = 'org.slf4j:jcl-over-slf4j:jar:1.7.5'
LOGGING = [LOGBACK, SLF4J_BRIDGES]

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
             'org.slf4j:slf4j-api:jar:1.7.5',
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
#
# We need to mark JAVA_HOME/lib/tools.jar as a dependency in order for
# Buildr to include it in the Eclipse .classpath file.
PROVIDED = [SERVLET, file(Java.tools_jar)]

### Repositories
repositories.remote << "http://jmrodri.fedorapeople.org/ivy/candlepin/"
repositories.remote << "http://repository.jboss.org/nexus/content/groups/public/"
repositories.remote << "http://gettext-commons.googlecode.com/svn/maven-repository/"
repositories.remote << "http://oauth.googlecode.com/svn/code/maven/"
repositories.remote << "http://central.maven.org/maven2/"

### Project
GROUP = "candlepin"
COPYRIGHT = ""

desc "The Proxy project"
define "candlepin" do
  release_number = ""
  File.new('candlepin.spec').each_line do |line|
    if line =~ /\s*Version:\s*(.*?)\s*$/
      project.version = $1
    end

    if line =~ /\s*Release:\s*(.*?)\s*$/
      release_number = $1.chomp("%{?dist}")
    end
  end

  project.group = GROUP
  manifest["Implementation-Vendor"] = COPYRIGHT

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

  if not use_pmd.nil?
    pmd.enabled = true
  end

  # Hook in gettext bundle generation to compile
  nopo = ENV['nopo']
  sources = FileList[_("po/*.po")]
  generate = file(_("target/generated-source") => sources) do |dir|
    mkdir_p dir.to_s
    sources.each do |source|
      locale = source.match("\/([^/]*)?\.po$")[1]
      #we do this inside the loop, in order to create a stub "generate" var
      if nopo.nil? || nopo.split(/,\s*/).include?(locale)
        sh "msgfmt --java -r org.candlepin.i18n.Messages -d #{dir} -l #{locale} #{source}"
      end
    end
  end
  compile.from generate

  ### Building
  compile.options.target = '1.6'
  compile.options.source = '1.6'
  compile_classpath = [COMMONS, RESTEASY, LOGGING, HIBERNATE, BOUNCYCASTLE,
    GUICE, JACKSON, QUARTZ, GETTEXT_COMMONS, HORNETQ, SUN_JAXB, OAUTH, RHINO, COLLECTIONS,
    PROVIDED, AMQP, LIQUIBASE]
  compile.with compile_classpath
  compile.with LOGDRIVER, LOG4J_BRIDGE if use_logdriver

  if Buildr.environment == 'oracle'
    compile.with ORACLE
  else
    compile.with DB
  end

  ### Testing
  test.setup do |task|
    filter('src/main/resources/META-INF').into('target/classes/META-INF').run
  end

  # the other dependencies transfer from compile.classpath automagically
  test.with HSQLDB, JUNIT, generate
  test.with LOGDRIVER, LOG4J_BRIDGE if use_logdriver
  test.using :java_args => [ '-Xmx2g', '-XX:+HeapDumpOnOutOfMemoryError' ]

  ### Javadoc
  doc.using :tag => 'httpcode:m:HTTP Code:'

  ### Packaging
  # NOTE: changes here must also be made in build.xml!
  candlepin_path = "org/candlepin"
  compiled_cp_path = "#{compile.target}/#{candlepin_path}"

  # The apicrawl package is only used for generating documentation so there is no
  # need to ship it.  Ideally, we'd put apicrawl in its own buildr project but I
  # kept getting complaints about circular dependencies.
  package(:jar, :id=>'candlepin-api').tap do |jar|
    jar.clean
    pkgs = %w{auth config exceptions jackson model paging pki resteasy service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
    p = jar.path(candlepin_path)
    p.include(pkgs).exclude("#{compiled_cp_path}/util/apicrawl")
  end

  package(:jar, :id=>"candlepin-certgen").tap do |jar|
    jar.clean
    pkgs = %w{config exceptions jackson model pinsetter pki service util}.map { |pkg| "#{compiled_cp_path}/#{pkg}" }
    p = jar.path(candlepin_path)
    p.include(pkgs).exclude("#{compiled_cp_path}/util/apicrawl")
  end

  package(:war, :id=>"candlepin").tap do |war|
    war.libs += artifacts(HSQLDB)
    war.libs -= artifacts(PROVIDED)
    war.classes.clear
    war.classes = [generate, resources.target]
    web_inf = war.path('WEB-INF/classes')
    web_inf.include("#{compile.target}/net")
    web_inf.path(candlepin_path).include("#{compiled_cp_path}/**").exclude("#{compiled_cp_path}/util/apicrawl")
  end

  desc "generate a .syntastic_class_path for vim/syntastic"
  task :list_classpath do
    # see https://github.com/scrooloose/syntastic/blob/master/syntax_checkers/java/javac.vim
    # this generates a .syntastic_class_path so the syntastic javac checker will
    # work properly
    syntastic_class_path = File.new(".syntastic_class_path", "w")
    syn_class_path_buf = ""
    compile.dependencies.inject("") { |a,c| syn_class_path_buf << "#{c}\n"}
    syn_class_path_buf << "#{Java.tools_jar}\n"
    # I'm sure there is a better way to figure out local target
    syn_class_path_buf << "target/classes\n"

    syntastic_class_path.write(syn_class_path_buf)
    syntastic_class_path.close()
  end

  desc 'Crawl the REST API and print a summary.'
  task :apicrawl  do
    options.test = 'no'

    # Join compile classpath with the package jar. Add the test log4j
    # to the front of the classpath:
    cp = ['src/test/resources'] | [project('candlepin').package(:jar)] | compile_classpath
    Java::Commands.java('org.candlepin.util.apicrawl.ApiCrawler',
                        {:classpath => cp})

    classes = artifacts(cp).collect do |a|
      task(a.to_s).invoke
      File.expand_path a.to_s
    end

    # Just run the doclet on the *Resource files
    sources = project('candlepin').compile.sources.collect do |dir|
      Dir["#{dir}/**/*Resource.java"]
    end.flatten

    # Add in the options as the last arg
    sources << {:name => 'Candlepin API',
                :classpath => classes,
                :doclet => 'org.candlepin.util.apicrawl.ApiDoclet',
                :docletpath => ['target/classes', classes].flatten.join(File::PATH_SEPARATOR),
                :output => 'target'}

    Java::Commands.javadoc(*sources)

    api_file = 'target/candlepin_api.json'
    comments_file = 'target/candlepin_comments.json'
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
    final_file = 'target/candlepin_methods.json'
    File.open(final_file, 'w') { |f| f.write final }

    # Cleanup
    rm api_file
    rm comments_file
    info "Wrote Candlepin API to: #{final_file}"
  end

  desc 'run rpmlint on the spec file'
  task :rpmlint do
    sh('rpmlint -f rpmlint.config candlepin.spec')
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

namespace "gettext" do
  task :extract do
    sh 'xgettext -ktrc:1c,2 -k -ktrnc:1c,2,3 -ktr -kmarktr -ktrn:1,2 -o po/keys.pot $(find src/main/java -name "*.java")'
  end
  task :merge do
    FileList["po/*.po"].each do |source|
      sh "msgmerge -N --backup=none -U #{source} po/keys.pot"
    end
  end
end

desc 'Make sure eventhing is working as it should'
task :check_all => [:clean, :checkstyle, 'candlepin:rpmlint', :test, :deploy, :spec]

#==========================================================================
# Tomcat deployment
#==========================================================================
desc 'Build and deploy candlepin to a local Tomcat instance'
task :deploy do
  sh 'buildconf/scripts/deploy'
end

task :deploy_check do
  begin
    http = Net::HTTP.new('localhost', 8443)
    http.use_ssl = true
    http.verify_mode = OpenSSL::SSL::VERIFY_NONE
    http.start do |conn|
      response = conn.request Net::HTTP::Get.new "/candlepin/admin/init"
      Rake::Task[:deploy].invoke if response.code != '200'
    end
  rescue
    # Http request failed
    Rake::Task[:deploy].invoke
  end
end

#==========================================================================
# RSpec functional tests
#==========================================================================
RSpec::Core::RakeTask.new do |task|

  # Support optional features env variable, specify the spec files to run
  # without the trailing '_spec.rb'. Specify multiple by separating with ':'.
  # i.e. build spec features=flex_expiry:authorization
  features = ENV['features']
  if not features.nil?
    feature_files = Array.new
    features.split(":").each do |part|
      feature_files << "spec/#{part}_spec.rb"
    end
    task.pattern = feature_files
  end

  task.rspec_opts = ["-I#{File.expand_path 'client/ruby/'}"]
  task.rspec_opts << '-c'
  skipbundler = ENV['skipbundler']
  if not skipbundler.nil?
    task.skip_bundler = true
  end

  # Allow specify only="should do something" to run only a specific
  # test. The text must completely match the contents of your "it" string.
  only_run = ENV['only']
  if not only_run.nil?
    task.rspec_opts << "-e '#{only_run}'"
  end

  fail_fast = ENV['fail_fast']
  if not fail_fast.nil?
    task.rspec_opts << "--fail-fast"
  end

  dots = ENV['dots']
  if not dots.nil?
    task.rspec_opts << "-fp"
  else
    task.rspec_opts << "-fd"
  end
end
#task :spec => :deploy_check

# fix the coverage reports generated by emma.
# we're adding to the existing emma:html task here
# This is AWESOME!
namespace :emma do
 task :html do
  info "Fixing emma reports"
  fixemmareports("reports/emma/coverage.html")

  dir = "reports/emma/_files"
  Dir.foreach(dir) do |filename|
    fixemmareports("#{dir}/#{filename}") unless filename == "." || filename == ".."
  end
 end
end

# fixes the html produced by emma
def fixemmareports(filetofix)
  text = File.read(filetofix)
  newstr = ''
  text.each_byte do |c|
    if c != 160 then
      newstr.concat(c)
    else
      newstr.concat('&nbsp;')
    end
  end
  tmp = File.new("tmpreport", "w")
  tmp.write(newstr)
  tmp.close()
  FileUtils.copy("tmpreport", filetofix)
  File.delete("tmpreport")
end
