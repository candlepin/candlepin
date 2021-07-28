#!/bin/bash -x

env | sort
echo

# The docker container test script will know to copy out
echo "Using workspace: ${WORKSPACE}"
mkdir -p ${WORKSPACE}/artifacts/

# make selinux happy via http://stackoverflow.com/a/24334000
chcon -Rt svirt_sandbox_file_t $WORKSPACE//artifacts/

# PROJ_NAME should be set in a jenkins environment. It allows multiple
#  instances of the compose to run without clobbering each other.
DIR="$(git rev-parse --show-toplevel)/docker/"
cd $DIR

# Run the cleanup
docker-compose $PROJ_NAME down
RETVAL=$?

echo "return value: $RETVAL"
cd -

sudo chown -R jenkins:jenkins ${WORKSPACE}/artifacts
exit $RETVAL
