#!/bin/bash

# Script heavily modified from
# https://github.com/candlepin/candlepin/blob/candlepin-4.2.8-1/bin/deployment/liquibase.sh
# By default, the script will prompt the user for the DB username, password, and url.
# To skip this, create a properties file with url, username, and password entries, and point the
# PROPFILE env var at the file.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

# Source system prefs
if [ -f /etc/java/liquibase.conf ]
then
    source /etc/java/liquibase.conf
fi

# Source user prefs
if [ -f $HOME/.liquibaserc ]
then
    source $HOME/.liquibaserc
fi

# List of directories to look for WEB-INF
# (Needed in case running locally or from extracted war)
search_webinf_dirs=(
    /opt/tomcat/webapps/candlepin/WEB-INF
    "$SCRIPT_DIR/WEB-INF"
    $(realpath -mq "$SCRIPT_DIR/../../../../target/hosted-candlepin-"*"/WEB-INF")
)

find_webinf() {
    for dir in "${search_webinf_dirs[@]}"
    do
        if [ -d "$dir" ]
        then
            echo "$dir"
            echo "Using Candlepin WEB-INF at $dir" >&2
            return 0
        fi
    done

    echo "Could not find Candlepin WEB-INF in any of these locations: ${search_webinf_dirs[@]}" >&2
    return 1
}

TARGET_WAR_DIR="$(find_webinf)"

# If the liquibase properties PROPFILE env variable exists, use that, otherwise interactively create a temporary one
if [ -z ${PROPFILE+x} ]
then
    default_db_user=candlepin
    default_db_pw=""
    default_db_url="jdbc:mysql://localhost:3306/candlepin"

    echo "Interactively creating liquibase properties file since \$PROPFILE env variable is not set."
    read -p "Enter the database username ($default_db_user): " prompt_db_user
    read -sp "Enter the database password ($default_db_pw): " prompt_db_pw
    echo
    read -p "Enter the database url (${default_db_url}): " prompt_db_url

    # Create a temporary file to hold the username and password, which will be
    # deleted upon conclusion of the script.
    PROPFILE="$(mktemp -q --tmpdir "liquibase.XXXXXX.properties")"
    trap 'rm -fv -- "$PROPFILE"' EXIT
    chmod 0600 "$PROPFILE"

    cat >"$PROPFILE" <<EOF
username: ${prompt_db_user:-$default_db_user}
password: ${prompt_db_pw:-$default_db_pw}
url: ${prompt_db_url:-$default_db_url}
EOF
fi

# Let's start
MAIN_CLASS=liquibase.integration.commandline.LiquibaseCommandLine
CLASSPATH=$(JARS=("$TARGET_WAR_DIR/lib"/*.jar); IFS=:; echo "${JARS[*]}"):"$TARGET_WAR_DIR/classes"
java -classpath "$CLASSPATH" $OPTIONS "$MAIN_CLASS" \
  --defaults-file="${PROPFILE}" \
  --headless=true \
  --log-level=debug \
  --log-file=liquibase.log \
  --hub-mode=off \
  --changelog-file=db/changelog/changelog-create.xml \
  --search-path=lib/classes,$TARGET_WAR_DIR/classes \
  update

