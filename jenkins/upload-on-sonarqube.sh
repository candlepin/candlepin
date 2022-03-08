#!/bin/bash -x

env | sort
echo

# The docker container test script will know to copy out
echo "Using workspace: $WORKSPACE"
mkdir -p $WORKSPACE/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
chcon -Rt svirt_sandbox_file_t $WORKSPACE//artifacts/

if [ -z "$BRANCH_UPLOAD" ];  then
  # Uploading PR to SonarQube server
  ./docker/test -q -c "cp-test -c ${CHANGE_BRANCH} -n ${SONAR_HOST_URL} ${BRANCH_NAME} ${CHANGE_ID} ${CHANGE_TARGET}" -n "${STAGE_NAME}-${BUILD_TAG}"
else
  # Uploading branch to SonarQube server
  ./docker/test -q -c "cp-test -c ${CHANGE_BRANCH} -n ${SONAR_HOST_URL} ${BRANCH_NAME}" -n "${STAGE_NAME}-${BUILD_TAG}"
fi

RETVAL=$?
sudo chown -R jenkins:jenkins $WORKSPACE/artifacts
exit $RETVAL
