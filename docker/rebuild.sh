#!/bin/sh
#
# Rebuilds all docker containers from base on down, and pushes each to the internal registry.

cd base
docker build -t candlepin-base .
docker tag candlepin-base docker.usersys.redhat.com/candlepin/candlepin-base
docker push docker.usersys.redhat.com/candlepin/candlepin-base

cd ../postgresql
docker build -t candlepin-postgresql .
docker tag candlepin-postgresql docker.usersys.redhat.com/candlepin/candlepin-postgresql
docker push docker.usersys.redhat.com/candlepin/candlepin-postgresql

cd ../oracle
docker build -t candlepin-oracle .
docker tag candlepin-oracle docker.usersys.redhat.com/candlepin/candlepin-oracle
docker push docker.usersys.redhat.com/candlepin/candlepin-oracle

cd ../mysql
docker build -t candlepin-mysql .
docker tag candlepin-mysql docker.usersys.redhat.com/candlepin/candlepin-mysql
docker push docker.usersys.redhat.com/candlepin/candlepin-mysql