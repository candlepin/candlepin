# Impl note:
# The Oracle container requires special attention, as XE demands 2g+ of shared memory and refuses to
# start without it. While normally not a problem, Docker currently has a hard-coded size of 64mb on
# /dev/shm, which cannot be dealt with in any capacity without running the container in privileged
# mode. Our workaround here does most of the setup in the dockerfile, then runs the container in
# privileged mode to get XE running, then commits the state after it's been run.
cleanup_docker() {
    if [ -e "oracle.cid" ]; then
        echo "Cleaning up Docker container..."
        CONTAINER_ID=$(cat oracle.cid)

        docker stop $CONTAINER_ID
        docker rm $CONTAINER_ID
        rm -f oracle.cid
    fi
}

echo "Running post-build setup tasks..."
trap cleanup_docker EXIT SIGHUP SIGINT SIGTERM
rm -f oracle.cid
docker run --privileged --cidfile=oracle.cid -Pi candlepin/$IMAGE_NAME:latest /bin/bash -xev /root/setup-oracle-runtime.sh

if [ "$?" != "0" ]; then
    echo "ERROR: Unable to run container post-build setup tasks" >&2
    exit 1
fi

CONTAINER_ID=$(cat oracle.cid)

# Commit container to get a snapshot of XE actually running
echo "Committing image..."
docker commit --change="CMD [\"/usr/bin/cp-test\", \"-t\", \"-u\", \"-r\"]" $CONTAINER_ID candlepin/$IMAGE_NAME:latest

if [ "$?" != "0" ]; then
    echo "ERROR: Unable to commit changes made by post-build setup tasks" >&2
    exit 1
fi

cleanup_docker
trap - EXIT SIGHUP SIGINT SIGTERM
# End custom Oracle build tasks
