#!/usr/bin/python
#
# Python script to generate a liquibase database update and include it
# in appropriate places.

import os
import sys
import fileinput

from datetime import datetime

CHANGESET_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>

<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-2.0.xsd">


    <changeSet id="%s" author="%s">
        <comment>Check out my awesome change.</comment>
        <!-- See http://www.liquibase.org/manual/refactoring_commands -->
    </changeSet>

</databaseChangeLog>
"""

# Relative to the location of this script in a git checkout:
CHANGELOG_DIR = "../../src/main/resources/db/changelog/"


def include_changeset(changelog_filepath, changeset_filename):
    f = open(changelog_filepath, 'r')
    lines = f.readlines()
    f.close()
    f = open(changelog_filepath, 'w')
    for line in lines:
        if last_line in line:
            f.write("    <include file=\"db/changelog/%s\" />\n" %
                changeset_filename)
        f.write(line)
    f.close()


if __name__ == "__main__":

    if len(sys.argv) != 2:
      print "Please specify short filename description. i.e. add-quantity-column"
      sys.exit(1)

    filename_desc = sys.argv[1]

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

    filename = "%s-%s" % (timestamp, filename_desc)
    # Add .xml if not specified by the user.
    if not filename.endswith(".xml"):
        filename = "%s.xml" % filename

    abs_changelog_dir = os.path.abspath(os.path.join(
      os.path.dirname(__file__), CHANGELOG_DIR))

    abs_changelog_filename = os.path.join(abs_changelog_dir, filename)
    print("Generating changeset template: %s" % abs_changelog_filename)

    changelog_file = open(abs_changelog_filename, "w")
    changelog_file.write(CHANGESET_TEMPLATE % (
        timestamp, os.getlogin()))
    changelog_file.close()

    # Append an include for our new changeset to changelog-create.xml
    # and changelog-update.xml:
    last_line = "</databaseChangeLog>"
    create_changelog_filename = os.path.join(abs_changelog_dir,
        "changelog-create.xml")
    update_changelog_filename = os.path.join(abs_changelog_dir,
        "changelog-update.xml")

    print("Adding changeset to changelog-create.xml")
    include_changeset(create_changelog_filename, filename)
    print("Adding changeset to changelog-update.xml")
    include_changeset(update_changelog_filename, filename)

    print
    print("DONE!")
    print
    print("Please edit %s to make your database changes." % abs_changelog_filename)
