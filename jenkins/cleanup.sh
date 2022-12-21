#!/bin/bash -x

echo "Cleaning up workspace: ${WORKSPACE}"

DIR="$(git rev-parse --show-toplevel)/docker/"
cd $DIR

# Run the cleanup
PROJ_NAME="${STAGE_NAME}-${BUILD_TAG}"
    podman pod rm -f $PROJ_NAME
RETVAL=$?

cd -

exit $RETVAL
