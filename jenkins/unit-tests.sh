#!/bin/bash -x

env | sort
echo

# The docker container test script will know to copy out
echo "Using workspace: $WORKSPACE"
#mkdir -p $WORKSPACE/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
#chcon -Rt svirt_sandbox_file_t $WORKSPACE//artifacts/

# Run the Candlepin unit tests
#./docker/test -x -c "cp-test -u -c ${CHANGE_BRANCH}" -n "${STAGE_NAME}-${BUILD_TAG}"
#RETVAL=$?
#sudo chown -R jenkins:jenkins $WORKSPACE/artifacts
#mv $WORKSPACE/artifacts "${WORKSPACE}/${STAGE_NAME}-artifacts"
#exit $RETVAL

# The only thing we need to do is run gradle test using the local java as the unit tets don't need
# a running candlepin or any other services than java.
./gradlew test