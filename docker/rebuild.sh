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

SCRIPT_NAME=`basename "$0"`

usage() {
    cat <<HELP
Usage: $SCRIPT_NAME [options]

OPTIONS:
  -p         Push images to a repository or registry
  -d <repo>  Specify the destination repo to receive the images; implies -p;
             defaults to "candlepin-base docker.usersys.redhat.com/candlepin"
  -v         Enable verbose/debug output
HELP
}

while getopts ":pd:" opt; do
    case $opt in
        p  ) PUSH="1";;
        d  ) PUSH="1"
             PUSH_DEST="${OPTARG}";;
        ?  ) usage; exit;;
    esac
done

if [ "$PUSH_DEST" == "" ]; then
    PUSH_DEST="docker.usersys.redhat.com/candlepin"
fi


# Base
cd base
docker build -t candlepin-base .

if [ "$PUSH" == "1" ]; then
    docker tag -f candlepin-base $PUSH_DEST/candlepin-base
    docker push $PUSH_DEST/candlepin-base
fi

# Postgresql
cd ../postgresql
docker build -t candlepin-postgresql .

if [ "$PUSH" == "1" ]; then
    docker tag -f candlepin-postgresql $PUSH_DEST/candlepin-postgresql
    docker push $PUSH_DEST/candlepin-postgresql
fi

# Oracle
cd ../oracle
docker build -t candlepin-oracle .

if [ "$PUSH" == "1" ]; then
    docker tag -f candlepin-oracle $PUSH_DEST/candlepin-oracle
    docker push $PUSH_DEST/candlepin-oracle
fi

# MySQL
cd ../mysql
docker build -t candlepin-mysql .

if [ "$PUSH" == "1" ]; then
    docker tag -f candlepin-mysql $PUSH_DEST/candlepin-mysql
    docker push $PUSH_DEST/candlepin-mysql
fi


# Build argument string for our pass-through scripts
PTARGS=""

if [ "$PUSH" == "1" ]; then
    PTARGS="$PTARGS -p -d $PUSH_DEST"
fi

# RHEL 6
cd ../candlepin-rhel6-base
./build.sh $PTARGS

cd ../candlepin-rhel6
./build.sh $PTARGS

# RHEL 7
cd ../candlepin-rhel7-base
./build.sh $PTARGS

cd ../candlepin-rhel7
./build.sh $PTARGS
