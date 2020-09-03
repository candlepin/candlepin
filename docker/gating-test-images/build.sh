#!/bin/bash
#
# Script that builds and pushes to the internal docker registry a postgresql image with a test data sql dump,
# and a candlepin image with an installed candlepin based on the current version of the stage environment.
# - Intermediate, temporary images are built for efficiency (temp-base-cp)
#   and loading/dumping of test data (temp-cp).
# - All local images are removed after the push.

retry() {
    local -r -i max_attempts="$1"; shift
    local -r name="$1"; shift
    local -r cmd="$@"
    local -i attempt_num=1
    echo -n "Waiting for $name to be dumped..."
    until ${cmd}
    do
        if (( attempt_num == max_attempts ))
        then
            echo "Attempt $attempt_num failed and there are no more attempts left!"
            return 1
        else
            echo -n '.'
            sleep $(( attempt_num++ ))
        fi
    done
    echo
}

evalrc() {
    if [ "$1" -ne "0" ]; then
        echo "$2"
        exit $1
    fi
}

mkdir -p /tmp/cp-docker/
# clean up leftover files from potential previous runs
rm -f /tmp/cp-docker/dump.sql
rm -f postgres/dump.sql

echo "============ Building temporary base candlepin image ============ "
# builds base centos image which installs candlepin dependencies & environment
docker build --no-cache --tag=temp_base_candlepin temp-base-cp/
evalrc $? "temp_base_candlepin image build was not successful."

echo "============ Building temporary candlepin image ============ "
# builds image which deploys a temporary candlepin, loads test data, and then dumps them
docker build --no-cache --tag=temp_candlepin temp-cp/
evalrc $? "temp_candlepin image build was not successful."

echo "============ Deploying temporary candlepin and loading test data ============ "
# need to be in a swarm in order to use the stack command
docker swarm init

# runs the previously build image against a postgres database in a temporary stack,
# to load and then dump the test data in dump.sql
docker stack deploy -c temp-cp/docker-compose.yml load_and_dump_stack
evalrc $? "load_and_dump_stack stack deploy was not successful."

echo "============ Waiting for data to be dumped... ============ "
retry 35 "dump.sql" test -f /tmp/cp-docker/dump.sql

if [ "$?" -ne "0" ]; then
  docker stack rm load_and_dump_stack
  docker swarm leave --force
  echo "dump.sql did not get dumped in time. Exiting..."
  exit $1
fi

echo "============ Removing temporary stack ============ "
docker stack rm load_and_dump_stack

docker swarm leave --force

echo "============ Building postgres image which will automatically load the dump.sql we generated before ============ "
mv /tmp/cp-docker/dump.sql postgres/
docker build --tag=cp_postgres postgres/
evalrc $? "postgres image build was not successful."
rm postgres/dump.sql
rm -rf /tmp/cp-docker/

echo "============ Building candlepin image with the version currently used in stage ============ "
docker build --no-cache --tag=cp_latest_stage cp-latest-stage/
evalrc $? "cp_latest_stage image build was not successful."

echo "============ Pushing images to registry... ============ "
REGISTRY=docker-registry.upshift.redhat.com/chainsaw

# Find out the candlepin version used in stage
curl -k -u admin:admin https://subscription.rhsm.stage.redhat.com/subscription/status > stage_status.json
CP_VERSION=$(python -c 'import json; fp = open("stage_status.json", "r"); obj = json.load(fp); fp.close(); print(obj["version"])');
rm stage_status.json

echo "Pushing candlepin image with stage version: ${CP_VERSION}."
docker tag cp_latest_stage $REGISTRY/cp_latest_stage:$CP_VERSION
docker tag cp_latest_stage $REGISTRY/cp_latest_stage:latest
docker push $REGISTRY/cp_latest_stage
evalrc $? "cp_latest_stage image push was not successful."

echo "Pushing postgres image."
docker tag cp_postgres $REGISTRY/cp_postgres:latest
docker push $REGISTRY/cp_postgres
evalrc $? "postgres image push was not successful."

echo "Build & push finished successfully."

echo "============ Removing images... ============ "
# Removed to make sure that every time a jenkins node runs this script,
# the images are built from scratch, not from the cache.
docker image rm cp_postgres cp_latest_stage temp_candlepin temp_base_candlepin
evalrc $? "Removal of docker images failed."
