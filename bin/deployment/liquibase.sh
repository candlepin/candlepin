#!/usr/bin/env bash

# Source functions library
_prefer_jre="true"
. /usr/share/java-utils/java-functions

# Source system prefs
if [ -f /etc/java/liquibase.conf ] ; then
  . /etc/java/liquibase.conf
fi

# Source user prefs
if [ -f $HOME/.liquibaserc ] ; then
  . $HOME/.liquibaserc
fi

# Configuration
MAIN_CLASS=liquibase.integration.commandline.LiquibaseCommandLine
BASE_FLAGS=""
BASE_OPTIONS=""

CP_CLASSPATH=${CP_EXTRACT_CLASSPATH:-/var/lib/tomcat/webapps/candlepin/WEB-INF/lib}
CLASSPATH=$(JARS=("$CP_CLASSPATH"/*.jar); IFS=:; echo "${JARS[*]}")

# Set parameters
set_jvm
set_flags ${BASE_FLAGS}
set_options ${BASE_OPTIONS}

# Let's start
run "$@"

exit 0