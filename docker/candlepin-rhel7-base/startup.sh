#! /bin/bash

set -e
env

# TODO: use env variables to check which database we're linked to, for now
# we'll just assume postgres.
cat > /etc/yum.repos.d/candlepin.repo <<CANDLEPIN
[candlepin]
name=candlepin
baseurl=$YUM_REPO
gpgcheck=0
CANDLEPIN

cat /etc/yum.repos.d/candlepin.repo
yum install -y candlepin

# Fetch the latest cpsetup and cpdb scripts and overwrite whatever came in the RPM so we don't have to tag
# a build to get a change into the container. This should only be done in the base containers, not official
# candlepin build containers.
wget -O /usr/share/candlepin/cpsetup https://raw.githubusercontent.com/candlepin/candlepin/master/server/code/setup/cpsetup
wget -O /usr/share/candlepin/cpsetup https://raw.githubusercontent.com/candlepin/candlepin/master/server/code/setup/cpsetup

/usr/share/candlepin/cpsetup -u postgres --dbhost $DB_PORT_5432_TCP_ADDR --dbport $DB_PORT_5432_TCP_PORT --skip-service

/usr/bin/supervisord -c /etc/supervisord.conf
