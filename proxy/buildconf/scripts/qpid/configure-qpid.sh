#!/bin/bash

is_rpm_installed () {
    rpm=$1
    rpm -q $1 > /dev/null
    return $?
}

CERTUTIL="certutil -d /etc/qpid/brokerdb/ -f /etc/qpid/brokerdb/passwd"
make_cert_db () {
    echo "creating nss certificate db"
    `dirname $0`/make_keys.sh
    sudo mkdir -p /etc/qpid/brokerdb
    sudo cp -R keys/server_db/* /etc/qpid/brokerdb/

    sudo chown -R qpidd:qpidd /etc/qpid/brokerdb
}

setup_qpidd_config () {
    LIBARCH="lib"
    if [ "x86_64" = `uname -p` ]
    then
        LIBARCH="lib64"
    fi
    sudo bash -c "sed 's/@LIBARCH@/$LIBARCH/' `dirname $0`/qpidd.conf.tmpl > /etc/qpidd.conf"
}

if ! is_rpm_installed "qpid-cpp-server-ssl"
then
    echo "installing qpid-cpp-server"
    sudo yum -y install qpid-cpp-server-ssl
fi

make_cert_db
setup_qpidd_config

sudo service qpidd restart
echo "Qpid configuration complete"
