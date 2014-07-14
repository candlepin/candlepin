#!/bin/bash

# This script will configure a qpid installed server to work with SSL.


# certificate information
# based on http://rajith.2rlabs.com/2010/03/01/apache-qpid-securing-connections-with-ssl/
COMPANY="O=Candlepin,ST=North Carolina,C=US"
CA_NAME="CandlepinCA"
CA_PASS="password"

SERVER_NAME="localhost"
SERVER_PASS="password"

JAVA_TRUSTPASS="password"
JAVA_KEYPASS="password"
CERT_LOC=keys
KEYSTORE=$CERT_LOC/keystore
TRUSTSTORE=$CERT_LOC/truststore
CA_DB=$CERT_LOC/CA_db
SERVER_DB=$CERT_LOC/server_db

create_ca_cert () {
    # prep for creating certificates
    rm -rf $CA_DB
    mkdir -p $CA_DB

    echo "y" > ca_input
    echo "0" >> ca_input
    echo "n" >> ca_input

    echo $CA_PASS > $CA_DB/pfile
    certutil -N -d $CA_DB -f $CA_DB/pfile
    dd bs=256 count=1 if=/dev/urandom of=noise
    cat ca_input | certutil -S -d $CA_DB -n "$CA_NAME" -s "CN=$CA_NAME,$COMPANY" -t "CT,," -x -2 -f $CA_DB/pfile -z noise
    rm noise ca_input

    certutil -L -d $CA_DB -n "$CA_NAME" -a -o $CA_DB/rootca.crt -f $CA_DB/pfile
}

create_server_cert () {
    rm -rf $SERVER_DB
    mkdir $SERVER_DB
    echo $SERVER_PASS > $SERVER_DB/pfile
    certutil -N -d $SERVER_DB -f $SERVER_DB/pfile

    # import the CA certificate into the CA_DB
    certutil -A -d $SERVER_DB -n "$CA_NAME" -t "TC,," -a -i $CA_DB/rootca.crt -f $SERVER_DB/pfile
    dd bs=256 count=1 if=/dev/urandom of=noise
    certutil -R -d $SERVER_DB -s "CN=$SERVER_NAME,$COMPANY" -a -o $SERVER_DB/server.req -z noise -f $SERVER_DB/pfile
    rm noise

    echo "0" > sign_serv
    echo "9" >> sign_serv
    echo "n" >> sign_serv
    echo "n" >> sign_serv
    echo "-1" >> sign_serv
    echo "n" >> sign_serv

    echo "sleeping for entropy"
    sleep 2

    cat sign_serv | certutil -C -d $CA_DB -c "$CA_NAME" -a -i $SERVER_DB/server.req -o $SERVER_DB/server.crt -2 -6 -f $CA_DB/pfile
    certutil -A -d $SERVER_DB -n $SERVER_NAME -a -i $SERVER_DB/server.crt -t ",," -f $SERVER_DB/pfile

    rm -f $TRUSTSTORE $KEYSTORE sign_serv
}

import_ca_into_stores () {
    # import the CA certificate in to the trust store
    keytool -import -v -keystore $TRUSTSTORE -storepass $JAVA_TRUSTPASS -alias $CA_NAME -file $CA_DB/rootca.crt -noprompt

    # import the CA certificate into the keystore (for client authentication)
    keytool -import -v -keystore $KEYSTORE -storepass $JAVA_KEYPASS -alias $CA_NAME -file $CA_DB/rootca.crt -noprompt
}

create_client_certificate () {
    CLIENT=$1-client
    # generate keys for the client certificate
    keytool -genkey -alias $CLIENT -keyalg RSA -sigalg SHA256withRSA -validity 356 -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -dname "CN=$CLIENT,$COMPANY"
    # create certificate request
    keytool -certreq -alias $CLIENT -sigalg SHA256withRSA -keystore $KEYSTORE -storepass $JAVA_KEYPASS -keypass $JAVA_KEYPASS -v -file $CERT_LOC/$CLIENT.req

    # sign the certificate request using the CA certificate
    echo "1" > sign_client
    echo "9" >> sign_client
    echo "n" >> sign_client
    echo "n" >> sign_client
    echo "-1" >> sign_client
    echo "n" >> sign_client

    cat sign_client | certutil -C -d $CA_DB -c "$CA_NAME" -a -i $CERT_LOC/$CLIENT.req -o $CERT_LOC/$CLIENT.crt -2 -6 -f $CA_DB/pfile
    rm sign_client

    # import the certicate into the keystore
    keytool -import -v -alias $CLIENT -keystore $KEYSTORE -storepass $JAVA_KEYPASS -file $CERT_LOC/$CLIENT.crt
}

copy_client_cert () {
    PROJECT=$1
    sudo mkdir -p /etc/$PROJECT/certs/amqp/
    sudo cp $KEYSTORE /etc/$PROJECT/certs/amqp/
    sudo cp $TRUSTSTORE /etc/$PROJECT/certs/amqp/
    sudo chown -R tomcat:tomcat /etc/$PROJECT/certs/amqp/
}

is_rpm_installed () {
    rpm=$1
    rpm -q $1 > /dev/null
    return $?
}

make_cert_db () {
    echo "creating nss certificate db"
    sudo mkdir -p /etc/qpid/brokerdb
    sudo cp -R $SERVER_DB/* /etc/qpid/brokerdb/
    sudo chown -R qpidd:qpidd /etc/qpid/brokerdb
}

setup_qpidd_config () {
    # lib arch is required on older qpids to let it know where ssl.so lives
    LIBARCH="lib"
    if [ "x86_64" = `uname -p` ]
    then
        LIBARCH="lib64"
    fi
    sudo bash -c "sed 's/@LIBARCH@/$LIBARCH/' `dirname $0`/qpidd.conf.tmpl > /etc/qpid/qpidd.conf"
}

##############
# main
##############

# Abort on errors
set -e

while getopts ":c" opt; do
    case $opt in
        c  ) CLEAN="1";;
    esac
done

if ! is_rpm_installed "qpid-cpp-server"
then
    echo "installing qpid-cpp-server"
    sudo yum -y install qpid-cpp-server
fi

# create working directory
mkdir -p $CERT_LOC

create_ca_cert
create_server_cert
import_ca_into_stores
create_client_certificate "candlepin"
copy_client_cert "candlepin"
create_client_certificate "gutterball"
copy_client_cert "gutterball"

make_cert_db
setup_qpidd_config

sudo service qpidd restart
echo "Qpid configuration complete"
