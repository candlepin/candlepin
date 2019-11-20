#!/bin/sh
#
# Builds an environment with all dependencies needed to build candlepin as an rpm, then installs it.

set -e

PACKAGES=(
    tito
    java
    java-devel
    ant
    selinux-policy-doc
    selinux-policy-devel
    maven
)

yum install -y ${PACKAGES[@]}

# find stage candlepin version
curl -k -u admin:admin https://subscription.rhsm.stage.redhat.com/subscription/status > stage_status.json
stage_version=$(python -c 'import json; fp = open("stage_status.json", "r"); obj = json.load(fp); fp.close(); print obj["version"]');
rm stage_status.json

# build & install candlepin rpm
cd /candlepin
./gradlew --no-daemon clean war
cd /candlepin/server

echo "Building candlepin rpm with tito..."
tito build --test --rpm
yum install -y /tmp/tito/noarch/candlepin-${stage_version}-1.noarch.rpm /tmp/tito/noarch/candlepin-selinux-${stage_version}-1.noarch.rpm
