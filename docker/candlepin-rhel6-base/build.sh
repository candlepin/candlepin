docker build -t candlepin/candlepin-rhel6-base:latest .
docker tag -f candlepin/candlepin-rhel6-base:latest docker.usersys.redhat.com/candlepin/candlepin-rhel6-base:latest
docker push docker.usersys.redhat.com/candlepin/candlepin-rhel6-base:latest
