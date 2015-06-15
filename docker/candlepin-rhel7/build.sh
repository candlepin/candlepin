#!/bin/sh

SCRIPT_NAME=`basename "$0"`

usage() {
    cat <<HELP
Usage: $SCRIPT_NAME [options]

OPTIONS:
  -p         Push images to a repository or registry
  -d <repo>  Specify the destination repo to receive the images; implies -p;
             defaults to "candlepin-base docker.usersys.redhat.com/candlepin"
  -c         Use cached layers when building containers
HELP
}

while getopts ":pd:c" opt; do
    case $opt in
        p  ) PUSH="1";;
        d  ) PUSH="1"
             PUSH_DEST="${OPTARG}";;
        c  ) USE_CACHE="1";;
        ?  ) usage; exit;;
    esac
done


if [ "$PUSH_DEST" == "" ]; then
    PUSH_DEST="docker.usersys.redhat.com/candlepin"
fi

# Setup build arguments
BUILD_ARGS=""

if [ "$USE_CACHE" == "1" ]; then
    BUILD_ARGS="$BUILD_ARGS --no-cache=false"
else
    BUILD_ARGS="$BUILD_ARGS --no-cache=true"
fi

docker build $BUILD_ARGS -t candlepin/candlepin-rhel7:latest .
CP_VERSION=`docker run -ti candlepin/candlepin-rhel7:latest rpm -q --queryformat '%{VERSION}' candlepin`
echo "Built container for candlepin: $CP_VERSION"

if [ "$PUSH" == "1" ]; then
    docker tag -f candlepin/candlepin-rhel7:latest $PUSH_DEST/candlepin-rhel7:latest
    docker tag -f candlepin/candlepin-rhel7:latest $PUSH_DEST/candlepin-rhel7:$CP_VERSION

    docker push $PUSH_DEST/candlepin-rhel7:$CP_VERSION
fi
