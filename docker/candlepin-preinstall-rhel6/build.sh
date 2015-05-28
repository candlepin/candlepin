docker build -t candlepin/candlepin-preinstall-rhel6:latest .
docker tag -f candlepin/candlepin-preinstall-rhel6:latest docker.usersys.redhat.com/candlepin/candlepin-preinstall-rhel6:latest
docker push docker.usersys.redhat.com/candlepin/candlepin-preinstall-rhel6:latest
