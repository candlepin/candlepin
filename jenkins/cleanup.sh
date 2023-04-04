#!/bin/bash -x

DIR="$(git rev-parse --show-toplevel)/containers/"
cd $DIR

PROJ_NAME="${STAGE_NAME}-${BUILD_TAG}"

# Collect artifacts from container
sudo podman exec $PROJ_NAME-candlepin /candlepin-dev/jenkins/collect_artifacts.sh
mv ${WORKSPACE}/artifacts "${WORKSPACE}/${STAGE_NAME}-artifacts"
sudo chown -R jenkins:jenkins ${WORKSPACE}/${STAGE_NAME}-artifacts

# Run the cleanup
sudo podman pod rm -f $PROJ_NAME
sudo podman network rm network-$PROJ_NAME
RETVAL=$?

cd -

exit $RETVAL
