#!/bin/sh
#
# Sets a system up for a candlepin development environment (minus a db,
# handled separately), and an initial clone of candlepin.

set -ve

source /root/dockerlib.sh

export JAVA_VERSION=1.8.0
export JAVA_HOME=/usr/lib/jvm/java-$JAVA_VERSION

# Install & configure dev environment
dnf install -y epel-release

PACKAGES=(
    createrepo_c
    expect
    gettext
    git
    hostname
    java-11-openjdk-devel
    java-$JAVA_VERSION-openjdk-devel
    jss
    mariadb
    openssl
    pki-servlet-engine
    postgresql
    procps
    python3-pip
    python3-pyyaml
    python3-requests
    rpm-build
    rpm-sign
    rsyslog
    wget
    which
    zlib
    zlib-devel
)

dnf module enable -y pki-core pki-deps

dnf install -y ${PACKAGES[@]}

sh -c 'alternatives --set python $(which python3)'

# Setup for autoconf:
mkdir /etc/candlepin
echo "# AUTOGENERATED" > /etc/candlepin/candlepin.conf

cat > /root/.bashrc <<BASHRC
if [ -f /etc/bashrc ]; then
  . /etc/bashrc
fi

export HOME=/root
export JAVA_HOME=/usr/lib/jvm/java-$JAVA_VERSION
BASHRC

git clone https://github.com/candlepin/candlepin.git /candlepin
cd /candlepin

# Setup and install rvm, ruby and pals
gpg2 --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB
# turning off verbose mode, rvm is nuts with this
set +v
curl -O https://raw.githubusercontent.com/rvm/rvm/master/binscripts/rvm-installer
curl -O https://raw.githubusercontent.com/rvm/rvm/master/binscripts/rvm-installer.asc
gpg2 --verify rvm-installer.asc && bash rvm-installer stable
source /etc/profile.d/rvm.sh || true

rvm install 2.5.3
rvm use --default 2.5.3
set -v

# Install all ruby deps
gem install bundler -v 1.16.1
bundle install --without=proton --retry=5 --verbose --full-index

# Installs all Java deps into the image, big time saver
./gradlew --no-daemon dependencies

# Fix issue with owner on mount volume
git config --global --add safe.directory /candlepin-dev

cd /
rm -rf /candlepin
cleanup_env