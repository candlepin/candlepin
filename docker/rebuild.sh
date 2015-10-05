#!/bin/sh
#
# Rebuilds all docker containers from base on down, and pushes each to the internal registry.

# If you encounter:
#
#   v[12] ping attempt failed with error: Get https://*.usersys.redhat.com/v[12]/: dial tcp 10.3.10.70:443: connection refused
#
# ... then it's likely the register needs to be accessed insecurely. To do so, add:
#
#   INSECURE_REGISTRY='--insecure-registry *.usersys.redhat.com'
#
# to /etc/sysconfig/docker (F21+), where * is the subdomain being accessed, then restart docker and
# re-run the rebuild command

SCRIPT_NAME=$( basename "$0" )
SCRIPT_HOME=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $SCRIPT_HOME

usage() {
    cat <<HELP
Usage: $SCRIPT_NAME [options]

OPTIONS:
  -p          Push images to a repository or registry
  -d <repo>   Specify the destination repo to receive the images; implies -p;
              defaults to "docker-registry.usersys.redhat.com/candlepin"
  -c          Use cached layers when building containers; defaults to false
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

# Build argument string to be passed through to each individual build script
BUILD_ARGS=""

if [ "$PUSH" == "1" ]; then
    BUILD_ARGS="$BUILD_ARGS -p"

    if [ "$PUSH_DEST" != "" ]; then
        BUILD_ARGS="$BUILD_ARGS -d $PUSH_DEST"
    fi
fi

if [ "$USE_CACHE" == "1" ]; then
    BUILD_ARGS="$BUILD_ARGS -c"
fi

if [ "$VERBOSE" == "1" ]; then
    BUILD_ARGS="$BUILD_ARGS -v"
fi

MESSAGES=""
EXIT_CODE=0

build_image() {
    IMAGE_NAME=$1

    ./$IMAGE_NAME/build.sh $BUILD_ARGS
    RESULT=$?

    if [ "$RESULT" != "0" ]; then
        EXIT_CODE=1
        ERR_MSG="Unable to build image \"$IMAGE_NAME\""

        if [ "$MESSAGES" != "" ] && [ "$ERR_MSG" != "" ]; then
            MESSAGES="$MESSAGES\n"
        fi

        MESSAGES="$MESSAGES$ERR_MSG"
    fi

    return $RESULT
}


if build_image "candlepin-base"; then
    build_image "candlepin-mysql"
    build_image "candlepin-oracle"
    build_image "candlepin-postgresql"
else
    echo "Unable to build candlepin-base image; skipping dependant images..." >&2
fi

if build_image "candlepin-rhel6-base"; then
    build_image "candlepin-rhel6"
else
    echo "Unable to build candlepin-rhel6-base image; skipping rhel6 child images..." >&2
fi

if build_image "candlepin-rhel7-base"; then
    build_image "candlepin-rhel7"
else
    echo "Unable to build candlepin-rhel7-base image; skipping rhel7 child images..." >&2
fi

if [ "$MESSAGES" != "" ]; then
    echo -e $MESSAGES >&2
else
    echo "Images built successfully"
fi

exit $EXIT_CODE
