#!/bin/bash

# This script will configure a qpid installed server to work with SSL.

# Get the directory this script is in. See http://mywiki.wooledge.org/BashFAQ/028
LOCATION="${BASH_SOURCE%/*}"

SUBJ="/C=US/O=Candlepin"
CA_NAME="Qpid CA"
CA_PASS="password"

CN_NAME="localhost"
CLIENT_PASS="password"

JAVA_TRUSTPASS="password"
JAVA_KEYPASS="password"

CERT_LOC=$LOCATION/keys
CA_PASS_FILE="$CERT_LOC/ca_password.txt"
CA_DB=$CERT_LOC/CA_db
LOG=$CERT_LOC/keys.log

create_ca_cert() {
    # prep for creating certificates
    rm -rf $CA_DB
    mkdir -p $CA_DB

    openssl genrsa -out $CERT_LOC/qpid_ca.key 2048 &>> $LOG
    openssl req -new -x509 -days 3650 -out $CERT_LOC/qpid_ca.crt -key $CERT_LOC/qpid_ca.key -subj "$SUBJ/CN=$CA_NAME" -extensions v3_ca -passin file:$CA_PASS_FILE &>> $LOG
    openssl pkcs12 -export -in $CERT_LOC/qpid_ca.crt -inkey $CERT_LOC/qpid_ca.key -out $CERT_LOC/qpid_ca.p12 -name "$CA_NAME" -password file:$CA_PASS_FILE &>> $LOG

    convert_to_nssdb
}

convert_to_nssdb() {
    rm -rf $CA_DB
    mkdir $CA_DB
    # The only way to import a private key to an NSS DB is by converting from a PKCS12
    pk12util -i $CERT_LOC/qpid_ca.p12 -d $CA_DB -w $CA_PASS_FILE -k $CA_PASS_FILE &>> $LOG
    certutil -M -d $CA_DB -f $CA_PASS_FILE -n "$CA_NAME" -t 'CT,CT,CT' &>> $LOG
}

create_client_certs() {
    clients=("candlepin" "gutterball")
    for client in "${clients[@]}"; do
        DEST="$CERT_LOC/$client"
        # Generate the key and certificate signing request
        openssl req -nodes -new -newkey rsa:2048 -out $DEST.csr -keyout $DEST.key -subj "$SUBJ/OU=$client/CN=$CN_NAME" -passin pass:$CLIENT_PASS &>> $LOG

        # Sign the CSR with the Qpid CA
        openssl x509 -days 3650 -req -CA $CERT_LOC/qpid_ca.crt -CAkey $CERT_LOC/qpid_ca.key -CAcreateserial -in $DEST.csr -out $DEST.crt &>> $LOG

        # Remove serial file that openssl creates.  Keep going if the file isn't there.
        rm $LOCATION/.srl || true

        # Import the signed cert into the NSS DB
        certutil -A -d $CA_DB -t ',,' -f $CA_PASS_FILE -a -n $client -i $DEST.crt &>> $LOG

        # Stupid keytool doesn't allow you to import a keypair. You can only
        # import a cert. Hence, we have to create the store as an PKCS12 and
        # convert to JKS. See http://stackoverflow.com/a/8224863
        openssl pkcs12 -export -name $client -in $DEST.crt -inkey $DEST.key -out $DEST.p12 -passout pass:$CLIENT_PASS &>> $LOG
        keytool -importkeystore -destkeystore $DEST.jks -srckeystore $DEST.p12 -srcstoretype pkcs12 -alias $client -storepass $JAVA_KEYPASS -srcstorepass $CA_PASS -noprompt &>> $LOG
        if [ -f $DEST.truststore ]; then
            keytool -delete -alias "$CA_NAME" -keystore $DEST.truststore -storepass $JAVA_TRUSTPASS
        fi
        keytool -import -v -keystore $DEST.truststore -storepass $JAVA_TRUSTPASS -alias "$CA_NAME" -file $CERT_LOC/qpid_ca.crt -noprompt &>> $LOG
    done
}

copy_client_cert () {
    PROJECT=$1
    sudo mkdir -p /etc/$PROJECT/certs/amqp/
    sudo cp $CERT_LOC/$PROJECT.jks /etc/$PROJECT/certs/amqp/
    sudo cp $CERT_LOC/$PROJECT.truststore /etc/$PROJECT/certs/amqp/
    sudo chown -R tomcat:tomcat /etc/$PROJECT/certs/amqp/
}

is_rpm_installed () {
    rpm -q "$@" > /dev/null
    return $?
}

make_cert_db () {
    sudo mkdir -p /etc/qpid/brokerdb
    sudo cp -R $CA_DB/* /etc/qpid/brokerdb/
    sudo cp $CA_PASS_FILE /etc/qpid/brokerdb/qpid_ca.password
    sudo chown -R qpidd:qpidd /etc/qpid/brokerdb
}

setup_qpidd_config () {
    # Older Qpids need to point to ssl.so which is under %{_libdir}
    local libdir=$(rpm --eval "%{_libdir}")
    sudo -E bash -c "cat << CONF > /etc/qpid/qpidd.conf
auth=no
#load-module=${libdir}/qpid/daemon/ssl.so
require-encryption=yes
# SSL
ssl-require-client-authentication=yes
ssl-cert-db=/etc/qpid/brokerdb
ssl-cert-password-file=/etc/qpid/brokerdb/qpid_ca.password
ssl-cert-name=Qpid CA
ssl-port=5671
log-to-syslog=yes
log-enable=info+
CONF
"
}

create_exchange() {
    exchange_name="$1"
    config_args="--ssl-certificate $CERT_LOC/qpid_ca.crt --ssl-key $CERT_LOC/qpid_ca.key -b amqps://localhost:5671"
    # Only create the exchange if it does not exist
    qpid-config $config_args exchanges event &>> $LOG || qpid-config $config_args add exchange topic event --durable &>> $LOG
}

##############
# main
##############

# Stop on errors
set -e

while getopts ":c" opt; do
    case $opt in
        c  ) CLEAN="1";;
    esac
done

if ! is_rpm_installed qpid-cpp-server qpid-tools qpid-cpp-server; then
    echo "installing Qpid"
    sudo yum -y install qpid-cpp-server qpid-tools qpid-cpp-server
fi

# create working directory
mkdir -p $CERT_LOC
echo -n $CA_PASS > $CA_PASS_FILE

create_ca_cert
create_client_certs
copy_client_cert "candlepin"
copy_client_cert "gutterball"

make_cert_db
setup_qpidd_config

sudo service qpidd restart
echo "Qpid configuration complete"

echo "Creating event exchange"
sleep 7
create_exchange "event"
