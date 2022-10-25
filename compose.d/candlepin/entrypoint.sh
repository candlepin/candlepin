#!/bin/bash

echo "HEY!"

# Before starting candlepin, bring DB schema up-to-date with liquibase.
(
    prop_file="$(mktemp -q --tmpdir "liquibase.XXXXXX.properties")"
    trap 'rm -fv -- "$prop_file"' EXIT

    echo "username: candlepin
    password: candlepin
    url: jdbc:postgresql://db:5432/candlepin
    " >"$prop_file"

    PROPFILE="$prop_file" /tmp/liquibase.sh
)

# For dev purposes, it may be faster to pull in the war from the dev's machine
# rather than rebuilding the image each time.

if [ "$USE_LOCAL_BUILD" == 'true' ]
then
    # Replace the webapps/candlepin dir with the extracted war
    rm -rf /opt/tomcat/webapps/candlepin/*
    cd /opt/tomcat/webapps/candlepin
    jar xf /tmp/candlepin/build/libs/candlepin*.war
    mv META-INF/context_tomcat8.xml META-INF/context.xml
fi

/opt/tomcat/bin/catalina.sh start &

tail -fn+0 --retry \
    /var/log/candlepin/candlepin.log \
    /var/log/tomcat/catalina.out \
    /var/log/tomcat/catalina.$(date '+%Y-%m-%d').log \
    /var/log/tomcat/localhost.$(date '+%Y-%m-%d').log \
    /var/log/localhost_access_log.$(date '+%Y-%m-%d').txt

