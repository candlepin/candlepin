docker build -t candlepin/candlepin-rhel7:latest .
CP_VERSION=`docker run -ti candlepin/candlepin-rhel7:latest rpm -q --queryformat '%{VERSION}' candlepin`
echo "Built container for candlepin: $CP_VERSION"

docker tag -f candlepin/candlepin-rhel7:latest docker.usersys.redhat.com/candlepin/candlepin-rhel7:latest
docker tag -f candlepin/candlepin-rhel7:latest docker.usersys.redhat.com/candlepin/candlepin-rhel7:$CP_VERSION
docker push docker.usersys.redhat.com/candlepin/candlepin-rhel7:$CP_VERSION

