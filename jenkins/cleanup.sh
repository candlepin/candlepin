#!/bin/bash -x

echo "Cleaning up workspace: ${WORKSPACE}"

DIR="$(git rev-parse --show-toplevel)/podman/"
cd $DIR

# Run the cleanup
PROJ_NAME="${STAGE_NAME}-${BUILD_TAG}"
podman-compose -p $PROJ_NAME down
RETVAL=$?

cd -

exit $RETVAL
