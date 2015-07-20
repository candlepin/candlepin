#! /bin/bash

set -e
env

# TODO: use env variables to check which database we're linked to, for now
# we'll just assume postgres.

/usr/share/candlepin/cpsetup -u postgres --dbhost $DB_PORT_5432_TCP_ADDR --dbport $DB_PORT_5432_TCP_PORT --skip-service

/usr/bin/supervisord -c /etc/supervisord.conf
