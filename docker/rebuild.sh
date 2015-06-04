#!/bin/sh
#
# Rebuilds all docker containers from base on down, and pushes each to the internal registry.

# If you encounter:
#
# FATA[0000] Error: v1 ping attempt failed with error: Get https://docker.usersys.redhat.com/v1/_ping: dial tcp 10.13.137.33:443: connection refused. If this private registry supports only HTTP or HTTPS with an unknown CA certificate, please add `--insecure-registry docker.usersys.redhat.com` to the daemon's arguments. In the case of HTTPS, if you have access to the registry's CA certificate, no need for the flag; simply place the CA certificate at /etc/docker/certs.d/docker.usersys.redhat.com/ca.crt
#
# On Fedora 21:
# Add: INSECURE_REGISTRY='--insecure-registry docker.usersys.redhat.com'
# To: /etc/sysconfig/docker

cd base
docker build -t candlepin-base .
docker tag -f candlepin-base docker.usersys.redhat.com/candlepin/candlepin-base
docker push docker.usersys.redhat.com/candlepin/candlepin-base

cd ../postgresql
docker build -t candlepin-postgresql .
docker tag -f candlepin-postgresql docker.usersys.redhat.com/candlepin/candlepin-postgresql
docker push docker.usersys.redhat.com/candlepin/candlepin-postgresql

cd ../oracle
docker build -t candlepin-oracle .
docker tag -f candlepin-oracle docker.usersys.redhat.com/candlepin/candlepin-oracle
docker push docker.usersys.redhat.com/candlepin/candlepin-oracle

cd ../mysql
docker build -t candlepin-mysql .
docker tag -f candlepin-mysql docker.usersys.redhat.com/candlepin/candlepin-mysql
docker push docker.usersys.redhat.com/candlepin/candlepin-mysql

cd ../candlepin-rhel6-base
./build.sh

cd ../candlepin-rhel6
./build.sh

cd ../candlepin-rhel7-base
./build.sh

cd ../candlepin-rhel7
./build.sh
