#!/bin/sh

unset CDPATH
SCRIPT_NAME=$( basename "$0" )
SCRIPT_HOME=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
IMAGE_NAME=$( basename "$SCRIPT_HOME" )

usage() {
    cat <<HELP
Usage: $SCRIPT_NAME [options] [image name]

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
        v  ) set -x;;
        ?  ) usage; exit;;
    esac
done

shift $(($OPTIND - 1))

if [ "$PUSH_DEST" == "" ]; then
    PUSH_DEST="docker-registry.usersys.redhat.com/candlepin"
fi

# Setup build arguments
BUILD_ARGS=""

# Impl note: At the time of writing, Docker defaults to using cache, which is the opposite of our
# desired behavior. To be certain we get Docker doing what we want, we specify the no-cache option
# in either case.
if [ "$USE_CACHE" == "1" ]; then
    BUILD_ARGS="$BUILD_ARGS --no-cache=false"
else
    BUILD_ARGS="$BUILD_ARGS --no-cache=true"
fi

# Determine image name
if [ "$1" != "" ]; then
    IMAGE_NAME=$1
fi

echo "Building image $IMAGE_NAME..."
cd $SCRIPT_HOME
docker build $BUILD_ARGS -t candlepin/$IMAGE_NAME:latest .

if [ "$?" != "0" ]; then
    exit 1
fi

if [ -f custom.sh ]; then
    source ./custom.sh
fi

if [ "$PUSH" == "1" ]; then
    docker tag candlepin/$IMAGE_NAME:latest $PUSH_DEST/$IMAGE_NAME:latest

    CP_VERSION="$(docker run -i --rm candlepin/$IMAGE_NAME:latest rpm -q --queryformat '%{VERSION}' candlepin)"
    if (! echo $CP_VERSION | grep -E -q --regex="^[0-9]+\.[0-9]+.*") then
        # We probably checked it out from git
        CP_VERSION="$(docker run -i --rm candlepin/$IMAGE_NAME:latest /bin/sh cd /candlepin && git describe | cut -d- -f 2)"
    fi

    if (echo $CP_VERSION | grep -E -q --regex="^[0-9]+\.[0-9]+.*") then
        docker tag candlepin/$IMAGE_NAME:latest $PUSH_DEST/$IMAGE_NAME:$CP_VERSION
    else
        echo "WARNING: Unable to determine Candlepin version for tagging image $IMAGE_NAME" >&2
    fi

    docker push $PUSH_DEST/$IMAGE_NAME
fi

