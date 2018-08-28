#!/usr/bin/env groovy
import groovy.json.JsonOutput
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
def all_checksums = [:]
def without_validCheckSum = new HashSet()

[new MySQLDatabase(), new PostgresDatabase(), new OracleDatabase()].each { db ->
  def params = new ChangeLogParameters(db)

  def changelog = parser.parse(args[0].split('resources/')[-1], params, accessor)

  for (def changeset : changelog.changeSets) {
    checksum = changeset.generateCheckSum()
    if (!all_checksums.containsKey(changeset as String)) {
      all_checksums[changeset as String] = new HashSet()
    }
    all_checksums[changeset as String].add(checksum)
    def validChecksums = changeset.validCheckSums
    if (validChecksums.size() > 0) {
      if (!validChecksums.contains(checksum)) {
        if (!bad.containsKey(changeset as String)) {
          bad[changeset as String] = new HashSet()
        }
        bad[changeset as String].add(checksum)
      }
    }
    else {
      without_validCheckSum.add(changeset as String)
    }
  }
}

for (def changeset : bad.keySet()) {
  println changeset
  for (def checksum : bad[changeset]) {
    println "  Missing $checksum"
  }
}

if (args.length > 1) {
  println "Storing checksums in ${args[1]}."
  def checksumFile = new File(args[1])
  if (checksumFile.exists()) {
    checksumFile.delete()
  }

  checksumFile << JsonOutput.toJson(
    [
      checksums: all_checksums.collectEntries {
        changeset, checksums -> [changeset, checksums.collect { checksum -> checksum.toString() } ]
      },
      bad: bad.keySet(),
      without_validCheckSum: without_validCheckSum,
    ]
  )
}

if (bad.size() > 0) {
  System.exit(1)
}
