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

SCRIPT_NAME=$( basename "$0" )
SCRIPT_HOME=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

usage() {
    cat <<HELP
Usage: $SCRIPT_NAME [options]

OPTIONS:
  -p          Push images to a repository or registry
  -d <repo>   Specify the destination repo to receive the images; implies -p;
              defaults to "candlepin-base docker.usersys.redhat.com/candlepin"
  -c          Use cached layers when building containers
  -v          Enable verbose/debug output
HELP
}

while getopts ":pd:cv" opt; do
    case $opt in
        p  ) PUSH="1";;
        d  ) PUSH="1"
             PUSH_DEST="${OPTARG}";;
        c  ) USE_CACHE="1";;
        v  ) VERBOSE="1"
             set -x;;
        ?  ) usage; exit;;
    esac
done

# Build argument string for our pass-through scripts
PTARGS=""

if [ "$PUSH" == "1" ]; then
    PTARGS="$PTARGS -p -d $PUSH_DEST"
fi

if [ "$USE_CACHE" == "1" ]; then
    PTARGS="$PTARGS -c"
fi

if [ "$VERBOSE" == "1" ]; then
    PTARGS="$PTARGS -v"
fi

./candlepin-base/build.sh $PTARGS

if [ "$?" != "0" ]; then
    echo "Unable to build candlepin-base; skipping dependant images"
    exit 1
fi

./candlepin-mysql/build.sh $PTARGS
./candlepin-oracle/build.sh $PTARGS
./candlepin-postgresql/build.sh $PTARGS
./candlepin-rhel6-base/build.sh $PTARGS
./candlepin-rhel6/build.sh $PTARGS
./candlepin-rhel7-base/build.sh $PTARGS
./candlepin-rhel7/build.sh $PTARGS
