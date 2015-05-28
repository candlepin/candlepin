#! /bin/bash

env

# Create a candlepin.repo file with the URL we were given via env var:
cat > /etc/yum.repos.d/candlepin.repo <<CANDLEPIN
[candlepin]
name=candlepin
baseurl=$YUM_REPO
gpgcheck=0
CANDLEPIN

cat /etc/yum.repos.d/candlepin.repo
yum install -y candlepin

# TODO: use env variables to check which database we're linked to, for now
# we'll just assume postgres.

/root/cpsetup -u postgres --dbhost $DB_PORT_5432_TCP_ADDR --dbport $DB_PORT_5432_TCP_PORT
#/usr/share/candlepin/cpsetup


service tomcat6 start
/usr/bin/supervisord -c /etc/supervisord.conf
