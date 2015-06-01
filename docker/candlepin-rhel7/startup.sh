#! /bin/bash

env

# TODO: use env variables to check which database we're linked to, for now
# we'll just assume postgres.

/root/cpsetup -u postgres --dbhost $DB_PORT_5432_TCP_ADDR --dbport $DB_PORT_5432_TCP_PORT
#/usr/share/candlepin/cpsetup

/usr/bin/supervisord -c /etc/supervisord.conf
