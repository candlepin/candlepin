#!/bin/bash

# This script will configure a qpid installed server to work with SSL.

# Get the directory this script is in. See http://mywiki.wooledge.org/BashFAQ/028
LOCATION="${BASH_SOURCE%/*}"
# If LOCATION is unchanged, then the user is calling the script with just the bare
# file name such as with "bash -x configure-qpid.sh" for example.
if [ "$LOCATION" == "$BASH_SOURCE" ]; then
    LOCATION="$(pwd)"
fi
source "$LOCATION/../../../bin/bash_functions"

trap on_error ERR
on_error() {
    local status=${1:-$?}
    err_msg "Script failed!"
    exit $status
}

KATELLO_PKI="/etc/pki/katello"
IS_KATELLO="$(test -e $KATELLO_PKI; echo $?)"

SUBJ="/C=US/O=Candlepin"
CERT_LOC="$LOCATION/keys"
LOG="$CERT_LOC/keys.log"
CA_NAME="ca"
CN_NAME="localhost"

define_variables() {
    if [ $IS_KATELLO -eq 0 ]; then
        CA_DB="$KATELLO_PKI/nssdb"
        CA_PASS_FILE="${CA_DB}/nss_db_password-file"
        JAVA_PASS="$(sudo cat $KATELLO_PKI/keystore_password-file)"
    else
        CA_PASS_FILE="$CERT_LOC/ca_password.txt"
        CA_DB="$CERT_LOC/CA_db"
        JAVA_PASS="password"
        echo -n "$JAVA_PASS" > $CA_PASS_FILE
    fi
    CA_PASS="$(sudo cat "$CA_PASS_FILE")"
}

create_ca_cert() {
    # prep for creating certificates
    mkdir -p "$CA_DB"

    if sudo certutil -L -d /etc/qpid/brokerdb -n $CA_NAME &>> $LOG; then
        CA_DB="/etc/qpid/brokerdb"
    fi

    local existing_ca="$(fp_nss "$CA_DB" "$CA_NAME")"
    local generated_ca="$(fp_file "$CERT_LOC/qpid_ca.crt")"

    if [ -z "$existing_ca" ]; then
        if [ -z "$generated_ca" ]; then
            openssl genrsa -out $CERT_LOC/qpid_ca.key 2048 &>> $LOG
            openssl req -new -x509 -days 3650 -out "$CERT_LOC/qpid_ca.crt" -key "$CERT_LOC/qpid_ca.key" -subj "$SUBJ/CN=$CA_NAME" -extensions v3_ca -passin file:"$CA_PASS_FILE" &>> $LOG
            openssl pkcs12 -export -in "$CERT_LOC/qpid_ca.crt" -inkey "$CERT_LOC/qpid_ca.key" -out "$CERT_LOC/qpid_ca.p12" -name "$CA_NAME" -password file:"$CA_PASS_FILE" &>> $LOG
        fi
        convert_to_nssdb
    else
        warn_msg "Found an existing Qpid CA.  Using it."
    fi
}

convert_to_nssdb() {
    rm -rf "$CA_DB"
    mkdir -p "$CA_DB"
    # The only way to import a private key to an NSS DB is by converting from a PKCS12
    pk12util -i "$CERT_LOC/qpid_ca.p12" -d "$CA_DB" -w "$CA_PASS_FILE" -k "$CA_PASS_FILE" &>> $LOG
    sudo certutil -M -d "$CA_DB" -f "$CA_PASS_FILE" -n "$CA_NAME" -t 'CT,CT,CT' &>> $LOG
}

create_client_certs() {
    local existing_ca="$(fp_nss "$CA_DB" "$CA_NAME")"

    clients=("candlepin" "gutterball")
    for client in "${clients[@]}"; do
        sudo mkdir -p /etc/$client/certs/amqp/
        local dest="$CERT_LOC/$client"
        local nss_nick="$client"
        if [ $IS_KATELLO -eq 0 -a "$client" == "candlepin" ]; then
            local nss_nick="amqp-client"
        fi


        local existing_nss="$(fp_nss "$CA_DB" "$nss_nick")"
        # This will be an empty string if the script hasn't run before
        local existing_generated="$(fp_file "$dest.crt")"
        local existing_jks="$(fp_jks "/etc/$client/certs/amqp/$client.jks" "$nss_nick")"

        # The NSS DB can have multiple certs with the same nickname.
        local occurrences=$(sudo certutil -L -d "$CA_DB" | grep "$nss_nick" | wc -l)

        # Skip if everything is equal and there are no duplicates
        if [ "$existing_nss" == "$existing_generated" -a "$existing_jks" == "$existing_generated" -a $occurrences -eq 1 ]; then
            warn_msg "Generated cert matches NSS DB and $client keystore.  Not creating a new cert."
            write_trust_store $client
            continue
        fi

        # We need an existing cert and key that match what's in the NSS DB.  If we don't have the cert and key
        # we must create one.  But, first we need to remove any cruft.
        if [ -z "$existing_generated" -a $occurrences -gt 0 -o "$existing_nss" != "$existing_generated" -o $occurrences -gt 1 ]; then
            warn_msg "Cert mismatch in NSS DB for $client.  Removing unknown cert if neccessary."
            # Certutil can have multiple certs with the same alias.  Kill them all.
            while sudo certutil -L -d "$CA_DB" -n "$nss_nick" &> /dev/null; do
                sudo certutil -D -d "$CA_DB" -n "$nss_nick" &>> $LOG
            done
        fi

        if [ ! -e "$dest.crt" -a ! -e "$dest.key" ]; then
            # Generate the key and certificate signing request
            openssl req -nodes -new -newkey rsa:2048 -out "$dest.csr" -keyout "$dest.key" -subj "$SUBJ/OU=$client/CN=$CN_NAME" -passin pass:$JAVA_PASS &>> $LOG

            if [ "$IS_KATELLO" == 0 ]; then
                # Katello doesn't place the CA private key in the NSS DB
                sudo openssl x509 -days 3650 -req -CA $KATELLO_PKI/certs/katello-default-ca.crt -CAkey $KATELLO_PKI/private/katello-default-ca.key -CAcreateserial -in "$dest.csr" > "$dest.crt" 2>> $LOG
            else
                # certutil requires the CSR in DER for some stupid reason
                openssl req -in "$dest.csr" -inform PEM -out "$dest.der.csr" -outform DER
                # Sign the CSR with the Qpid CA from the NSS DB.  Don't use the -o option to give an output file name because
                # with sudo the file will get the wrong permissions.
                sudo certutil -C -c "$CA_NAME" -i "$dest.der.csr" -v 120 -f "$CA_PASS_FILE" -d "$CA_DB" > "$dest.der.crt"  2>> $LOG
                # Converting to PEM isn't strictly necessary but it's nice for future operations
                openssl x509 -in "$dest.der.crt" -inform DER -out "$dest.crt" -outform PEM &>> $LOG
            fi
        fi

        # Import the signed cert into the NSS DB
        sudo certutil -A -d "$CA_DB" -t ',,' -f "$CA_PASS_FILE" -a -n "$nss_nick" -i "$dest.crt" &>> $LOG

        # Stupid keytool doesn't allow you to import a keypair. You can only
        # import a cert. Hence, we have to create the store as an PKCS12 and
        # convert to JKS. See http://stackoverflow.com/a/8224863
        openssl pkcs12 -export -name "$nss_nick" -in "$dest.crt" -inkey "$dest.key" -out "$dest.p12" -passout pass:$JAVA_PASS &>> $LOG
        keytool -importkeystore -destkeystore "$dest.jks" -srckeystore "$dest.p12" -srcstoretype "pkcs12" -alias "$nss_nick" -storepass $JAVA_PASS -srcstorepass $JAVA_PASS -noprompt &>> $LOG
        sudo cp "$dest.jks" /etc/$client/certs/amqp/

        write_trust_store $client
    done
}

write_trust_store() {
    local client=$1
    local dest="$CERT_LOC/$client"
    local existing_truststore="$(fp_jks "/etc/$client/certs/amqp/$client.truststore" "$CA_NAME")"
    local existing_ca="$(fp_nss "$CA_DB" "$CA_NAME")"

    # Skip import into truststore if CA is already there
    if [ "$existing_truststore" == "$existing_ca" ]; then
        warn_msg "Existing $client truststore matches CA in NSS DB.  Not modifying truststore."
        return 0
    fi

    if [ -f $dest.truststore  ]; then
        keytool -delete -alias "$CA_NAME" -keystore "$dest.truststore" -storepass $JAVA_PASS &>> $LOG
    fi
    echo "$(cert_from_nss "$CA_DB" "$CA_NAME")" | keytool -import -v -keystore "$dest.truststore" -storepass $JAVA_PASS -alias "$CA_NAME" -noprompt &>> $LOG
    sudo cp "$dest.truststore" /etc/$client/certs/amqp/
}

set_cert_permissions() {
    local client="$1"
    sudo chown -R tomcat:tomcat "/etc/$client/certs/amqp/"
}

is_rpm_installed() {
    rpm -q "$@" > /dev/null
    return $?
}

make_cert_db() {
    if [ "$CA_DB" != "/etc/qpid/brokerdb" ]; then
        sudo mkdir -p /etc/qpid/brokerdb
        sudo cp "$CA_DB"/* /etc/qpid/brokerdb/
        sudo cp "$CA_PASS_FILE" /etc/qpid/brokerdb/qpid_ca.password
    fi
    sudo chown -R qpidd:qpidd /etc/qpid/brokerdb
}

setup_qpidd_config() {
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
ssl-cert-name=$CA_NAME
ssl-port=5671
log-to-syslog=yes
log-enable=info+
CONF
"
}

copy_in_existing_cp_certs() {
    echo "Copying in Candlepin certs from Katello."
    # We don't want copied files to remain owned by root
    sudo cat $KATELLO_PKI/certs/java-client.crt > "$CERT_LOC/candlepin.crt"
    sudo cat $KATELLO_PKI/private/java-client.key > "$CERT_LOC/candlepin.key"
}

create_exchange() {
    exchange_name="$1"
    config_args="-b amqps://localhost:5671 "
    if [ $IS_KATELLO -eq 0 ]; then
        config_args+="--ssl-certificate $KATELLO_PKI/certs/java-client.crt --ssl-key $KATELLO_PKI/private/java-client.key"
    else
        config_args+="--ssl-certificate $CERT_LOC/qpid_ca.crt --ssl-key $CERT_LOC/qpid_ca.key"
    fi

    # Only create the exchange if it does not exist
    if ! sudo qpid-config $config_args exchanges "$exchange_name" &>> $LOG; then
        echo "Creating $exchange_name exchange"
        sudo qpid-config $config_args add exchange topic "$exchange_name" --durable &>> $LOG
    fi
}

usage() {
    cat <<HELP
    usage: configure-qpid.sh [options]

    OPTIONS:
      -c  clean the existing qpid setup and exit (not available for Katello)
      -h  print this message
HELP
}

##############
# main
##############

while getopts ":c" opt; do
    case $opt in
        c  ) CLEAN="1";;
        ?  ) usage; exit;;
    esac
done

if ! is_rpm_installed qpid-cpp-server qpid-tools qpid-cpp-server qpid-cpp-server-store; then
    echo "installing Qpid"
    sudo yum -y install qpid-cpp-server qpid-tools qpid-cpp-server qpid-cpp-server-store
fi

# create working directory
mkdir -p "$CERT_LOC"

define_variables

if [ -n "$CLEAN" ]; then
    if [ "$IS_KATELLO" -eq 0 ]; then
        warn_msg "Clean is not supported on Katello."
        exit 0
    else
        sudo rm -rf "$CERT_LOC"
        sudo rm -f /etc/candlepin/certs/amqp/candlepin.*
        sudo rm -f /etc/gutterball/certs/amqp/gutterball.*
        sudo rm -f /etc/qpid/brokerdb/*
        success_msg "Cleaned all AMQP certs"
        exit 0
    fi
fi

create_ca_cert
if [ $IS_KATELLO -eq 0 ]; then
    copy_in_existing_cp_certs
fi

create_client_certs
set_cert_permissions "candlepin"
set_cert_permissions "gutterball"

if [ $IS_KATELLO -ne 0 ]; then
    make_cert_db
    setup_qpidd_config
fi

sudo service qpidd restart

sleep 7
create_exchange "event"
success_msg "Done!"
