#!/bin/bash -x

env | sort
echo

# The container test script will know to copy out
echo "Using workspace: ${WORKSPACE}"
mkdir -p ${WORKSPACE}/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
chcon -Rt svirt_sandbox_file_t ${WORKSPACE}//artifacts/

# Run the spec tests
TEST_DB=""
case $CANDLEPIN_DATABASE in
  mysql) TEST_DB="-m";;
  postgresql) TEST_DB="-p";;
esac

IMAGE=""
case $OS_IMAGE in
  cs9) IMAGE="-o cs9";;
  cs8) IMAGE="-o cs8";;
esac

sudo ./containers/test $TEST_DB $IMAGE -c "cp-test $CP_TEST_ARGS -c ${CHANGE_BRANCH} -j 17" -n "${STAGE_NAME}-${BUILD_TAG}"
RETVAL=$?
sudo chown -R jenkins:jenkins $WORKSPACE
exit $RETVAL
