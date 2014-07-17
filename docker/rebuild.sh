#!/bin/sh
#
# Rebuilds all docker containers from base on down, and pushes each to the internal registry.

cd base
docker build -t candlepin-base .
docker tag candlepin-base docker.usersys.redhat.com/dgoodwin/candlepin-base
docker push docker.usersys.redhat.com/dgoodwin/candlepin-base

cd ../postgresql
docker build -t candlepin-postgresql .
docker tag candlepin-postgresql docker.usersys.redhat.com/dgoodwin/candlepin-postgresql
docker push docker.usersys.redhat.com/dgoodwin/candlepin-postgresql
