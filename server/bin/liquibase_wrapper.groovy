#!/usr/bin/env groovy
import groovy.grape.Grape

def root = 'buildr -s checkout_root'.execute().text.trim()
def candlepin_classes_path = "$root/server/target/classes"
if (!(new File(candlepin_classes_path).exists())) {
  println('Candlepin classes not found. Please do a build first.')
  System.exit(1)
}
// copy classes to a tempdir (without copying liquibase files) so we can reference src/main/resources without dupes
def tempdir = 'mktemp -d'.execute().text.trim()
"cp -R ${candlepin_classes_path}/org ${tempdir}".execute()
candlepin_classes_path = tempdir

cli = new CliBuilder(usage: "liquibase_wrapper.groovy [wrapper_args...] <liquibase_version> <path_to_groovy_script> [script_args...]")
cli.help('print this message')
cli.cp(args:1, 'add a path to the classpath')
options = cli.parse(args)

if (options.help || options.arguments().size() < 2) {
  cli.usage()
  System.exit(0)
}

candlepin_resources_path = "$root/server/src/main/resources"
if (options.cp) { // override the src/main/resources classpath
  candlepin_resources_path = options.cp
}

classpath_entries = [
  candlepin_resources_path,
  candlepin_classes_path,
  '/usr/share/java/postgresql-jdbc.jar',
  '/usr/share/java/mysql-connector-java.jar',
]

for (def path : classpath_entries) {
  getClass().classLoader.addClasspath(path)
}

script_args = []
if (options.arguments().size() > 2) {
  script_args += options.arguments()[2..-1]
}

Grape.grab(group:'org.yaml', module:'snakeyaml', version:'1.20')
Grape.grab(group:'org.liquibase', module:'liquibase-core', version:options.arguments()[0], transitive:false)
Grape.grab(group:'org.slf4j', module:'slf4j-nop', version: '1.7.22')

run(new File(options.arguments()[1]), script_args as String[])
"rm -rf ${tempdir}".execute()
