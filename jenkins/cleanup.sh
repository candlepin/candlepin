#!/bin/bash -x

echo "Cleaning up workspace: ${WORKSPACE}"

DIR="$(git rev-parse --show-toplevel)/containers/"
cd $DIR

# Run the cleanup
PROJ_NAME="${STAGE_NAME}-${BUILD_TAG}"
    podman pod rm -f $PROJ_NAME
RETVAL=$?

cd -

exit $RETVAL
