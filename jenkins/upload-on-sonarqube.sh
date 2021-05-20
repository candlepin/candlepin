#!/bin/bash -x

env | sort
echo

# The docker container test script will know to copy out
echo "Using workspace: $WORKSPACE"
mkdir -p $WORKSPACE/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
chcon -Rt svirt_sandbox_file_t $WORKSPACE//artifacts/

if [ ! -z "$BRANCH_UPLOAD" ];  then
  # Uploading PR on the SonarQube
  ./docker/test -c "cp-test -c ${CHANGE_BRANCH} -n ${SONAR_HOST_URL} ${CHANGE_ID} ${CHANGE_TARGET}" -n "${STAGE_NAME}-${BUILD_TAG}"
else
  # Uploading branch on the SonarQube
  ./docker/test -c "cp-test -c ${CHANGE_BRANCH} -n ${SONAR_HOST_URL}" -n "${STAGE_NAME}-${BUILD_TAG}"
fi

RETVAL=$?
sudo chown -R jenkins:jenkins $WORKSPACE/artifacts
exit $RETVAL
