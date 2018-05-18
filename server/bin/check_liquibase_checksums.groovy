#!/usr/bin/env groovy
import liquibase.database.DatabaseFactory
import liquibase.parser.ChangeLogParserFactory
import liquibase.resource.FileSystemResourceAccessor
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.changelog.ChangeLogParameters
import liquibase.database.core.MySQLDatabase
import liquibase.database.core.PostgresDatabase
import liquibase.database.core.OracleDatabase

project_dir = 'buildr -s checkout_root'.execute().text.trim()
accessor = new ClassLoaderResourceAccessor()

def parser = ChangeLogParserFactory.instance.getParser(args[0].split('resources/')[-1], accessor)
def bad = [:]

[new MySQLDatabase(), new PostgresDatabase(), new OracleDatabase()].each { db ->
  def params = new ChangeLogParameters(db)

  def changelog = parser.parse(args[0].split('resources/')[-1], params, accessor)

  for (def changeset : changelog.changeSets) {
    def validChecksums = changeset.validCheckSums
    if (validChecksums.size() > 0) {
      checksum = changeset.generateCheckSum()
      if (!validChecksums.contains(checksum)) {
        if (!bad.containsKey(changeset as String)) {
          bad[changeset as String] = new HashSet()
        }
        bad[changeset as String].add(checksum)
      }
    }
  }
}

for (def changeset : bad.keySet()) {
  println changeset
  for (def checksum : bad[changeset]) {
    println "  Missing $checksum"
  }
}

if (bad.size() > 0) {
  System.exit(1)
}
