#!/bin/bash -x

env | sort
echo

# The docker container test script will know to copy out
echo "Using workspace: $WORKSPACE"
mkdir -p $WORKSPACE/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
chcon -Rt svirt_sandbox_file_t $WORKSPACE//artifacts/

# Run the Candlepin unit tests
./docker/test -p -c "cp-test -uuu -c ${GIT_COMMIT}" -n "${STAGE_NAME}-${BUILD_TAG}"
RETVAL=$?
sudo chown -R jenkins:jenkins $WORKSPACE/artifacts
exit $RETVAL
