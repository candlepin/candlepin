docker build -t candlepin/candlepin-rhel6:latest .
CP_VERSION=`docker run -ti candlepin/candlepin-rhel6:latest rpm -q --queryformat '%{VERSION}' candlepin`
echo "Built container for candlepin: $CP_VERSION"

docker tag -f candlepin/candlepin-rhel6:latest docker.usersys.redhat.com/candlepin/candlepin-rhel6:latest

docker tag -f candlepin/candlepin-rhel6:latest docker.usersys.redhat.com/candlepin/candlepin-rhel6:$CP_VERSION

docker push docker.usersys.redhat.com/candlepin/candlepin-rhel6:$CP_VERSION

