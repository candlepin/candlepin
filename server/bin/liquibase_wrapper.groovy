#!/usr/bin/env groovy
import groovy.grape.Grape

def root = 'buildr -s checkout_root'.execute().text.trim()
def candlepin_classes_path = "$root/server/target/classes"
if (!(new File(candlepin_classes_path).exists())) {
  println('Candlepin classes not found. Please do a build first.')
  System.exit(1)
}

classpath_entries = [
  "$root/server/src/main/resources",
  candlepin_classes_path,
  '/usr/share/java/postgresql-jdbc.jar',
  '/usr/share/java/mysql-connector-java.jar',
]

for (def path : classpath_entries) {
  getClass().classLoader.addClasspath(path)
}

script_args = []
if (args.length < 2) {
  println('Usage: liquibase_wrapper.groovy <liquibase_version> <path_to_groovy_script> [script_args...]')
  System.exit(1)
}
if (args.length > 2) {
  script_args += args[2..-1]
}

Grape.grab(group:'org.yaml', module:'snakeyaml', version:'1.20')
Grape.grab(group:'org.liquibase', module:'liquibase-core', version:args[0], transitive:true)

run(new File(args[1]), script_args as String[])
