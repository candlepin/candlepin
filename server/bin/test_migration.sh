#!/bin/bash

SCRIPT_NAME=$(basename $BASH_SOURCE)

BUILDR="buildr"
CP_DEPLOY="./bin/deploy"

CP_DIRECTORIES=(
    "/candlepin-dev"
    "/candlepin"
    "."
    $(dirname $0)
)

# Exit on any error
set -e

trapex() {
    target="$1"
    shift

    for sig in "$@"; do
        trap "$target $sig" "$sig"
        signals="$signals $sig"
    done
}

move_artifact() {
    if [ -f "$1" ] && [ -d "$2" ]; then
        cp -v "$1" "$2" || true
    fi
}

collect_artifacts() {
    # If the caller mounted a volume at /artifacts, copy server logs out:
    if [ -d "/artifacts" ]; then
        echo "Collecting artifacts..."

        CANDLEPIN_ARTIFACTS=(
            'access.log'
            'audit.log'
            'candlepin.log'
            'error.log'
            'lint.log'
            'buildr.log'
            'unit_tests.log'
            'rspec.log'
        )

        # It's entirely possible for these to not exist, so we'll copy them if we can, but if we
        # fail, we shouldn't abort
        for ARTIFACT in ${CANDLEPIN_ARTIFACTS[@]}
        do
            move_artifact "/var/log/candlepin/$ARTIFACT" '/artifacts/'
        done
    fi
}

# Be sure to run cleanup on any error, SIGINT, or SIGTERM
trapex cleanup SIGINT SIGTERM ERR EXIT
CLEANUP_RUN=0
CLEAN_CP=0

cleanup() {
    if [ "$CLEANUP_RUN" == "0" ]; then
        CLEANUP_RUN=1

        # Remove our traps so we don't keep trying to call this
        trap - $signals
        trap "" ERR EXIT

        # Collect artifacts up to this point. If we're about to drop into a shell, we'll leave any
        # additional artifact collection up to the user
        collect_artifacts

        # Run buildr clean in our target CP directory
        if [ "$CLEAN_CP" == "1" ]; then
            buildr clean
        fi

        # Remove our tee pipe, if it exists
        rm -f /tmp/teepipe

        # exit, propagating any signal we received
        kill -$1 $$
    fi
}

usage() {
    echo "usage: $SCRIPT_NAME [options]"

    cat <<HELP
OPTIONS:
  -c        Perform git clean before running the test and buildr clean afterward
  -d <ref>  The destination gitref to migrate to
  -s <ref>  The source gitref from which to migration
  -v        Enable verbose (debug) logging

HELP
}

# Configuration
CLEAN=0
SRC_GIT_REF=""
SRC_DEPLOY_FLAGS="-gt"
DEST_GIT_REF="master"
DEST_DEPLOY_FLAGS=""
VERBOSE=0
CP_HOST="localhost:8443"

ARGV=("$@")
while getopts ":cd:s:v" opt; do
    case $opt in
        c   ) CLEAN="1";;
        d   ) DEST_GIT_REF="${OPTARG}";;
        s   ) SRC_GIT_REF="${OPTARG}";;
        v   ) VERBOSE="1";;

        ?   ) usage
              exit 1
              ;;

        :   ) echo "Option -$OPTARG requires an argument" >&2
              usage
              exit 1
              ;;
    esac
done

shift $(($OPTIND - 1))

if [ -z "$SRC_GIT_REF" ]; then
    echo "A source ref must be provided"
    exit 1
fi

# Setup our tee pipe for forking output
mkfifo /tmp/teepipe

# Attempt to use a pre-defined Candlepin directory from one of the known defaults
# CP_HOME is set here
for CPDIR in ${CP_DIRECTORIES[@]}
do
    if [ -d "$CPDIR" ]; then
        # Check if our deploy script is where we expect it...
        if [ -x "$CPDIR/server/bin/deploy" ]; then
            CPDIR="$CPDIR/server"
        elif [ -x "$CPDIR/bin/deploy" ]; then
            : # do nothing
        elif [ -x "$CPDIR/deploy" ]; then
            CPDIR="$CPDIR/.."
        else
            # Didn't find it; we're probably in the wrong place
            continue
        fi

        # Clean up the path a bit...
        cd $CPDIR
        CP_HOME=$(pwd)

        echo "Using Candlepin instance found at: $CP_HOME"
        break
    fi
done

if [ -z "$CP_HOME" ]; then
    echo  "ERROR: Unable to locate Candlepin deploy script. Please run this script from the Candlepin server directory" >&2
    exit 1
fi


# Start doing actual work
if [ "$VERBOSE" == "1" ]; then
    SRC_DEPLOY_FLAGS="$SRC_DEPLOY_FLAGS -v"
    DEST_DEPLOY_FLAGS="$DEST_DEPLOY_FLAGS -v"
fi

if [ "$CLEAN" == "1" ]; then
    git fetch
    git reset --hard
    git clean -df
    git merge FETCH_HEAD
fi

echo "Checking out: $SRC_GIT_REF"
git checkout "$SRC_GIT_REF"

CLEAN_CP=1
CP_DEPLOY $DEPLOY_FLAGS

# We don't need an explicit wait here since the deploy script already does exactly that for us

echo "Checking out: $DEST_GIT_REF"
git checkout "$DEST_GIT_REF"

CP_DEPLOY $DEPLOY_FLAGS

# Artifact capturing will be performed by our exit hook
