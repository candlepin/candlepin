#!/bin/bash

SCRIPT_NAME=$(basename "$0")

# This works fine for our dev deployment -- especially within Vagrant images -- but may not work as
# expected on systems with multiple interfaces or multiple external IPs (i.e. IPv4 and IPv6). On
# such systems, this script should be run with explicit arguments for these values.
HOSTNAME=$(hostname)
HOST_ADDR=$(hostname -I | cut -d ' ' -f 1)

CERT_HOME='/etc/candlepin/certs'
CERT_NAME='candlepin-ca'

NOISE_FILE="${CERT_HOME}/noise.bin"
PASSWORD_FILE="${CERT_HOME}/cppw.txt"

CA_KEY="${CERT_HOME}/candlepin-ca.key"
CA_PUB_KEY="${CERT_HOME}/candlepin-ca-pub.key"
CA_CERT="${CERT_HOME}/candlepin-ca.crt"

CA_CERT_DAYS=365
CERT_BIT_LENGTH=4096

set -e

check_dependency() {
    if ! command -v $1 > /dev/null; then
        >&2 echo "Error: Certificate generation failed; $1 not detected"
        exit 1
    fi
}

generate_certs() {
    # Ensure we have all of our dependencies available
    check_dependency openssl

    # prep environment, noise and password files
    rm -rf $CERT_HOME
    mkdir -p $CERT_HOME
    openssl rand -out $NOISE_FILE $CERT_BIT_LENGTH
    openssl rand -base64 -out $PASSWORD_FILE 24

    # Generate keys
    echo "Generating keys..."
    openssl genpkey -out $CA_KEY -pass "file:${PASSWORD_FILE}" -algorithm rsa -pkeyopt rsa_keygen_bits:$CERT_BIT_LENGTH
    openssl pkey -pubout -in $CA_KEY -out $CA_PUB_KEY

    # Generate our cert, using the newer options if available
    echo "Generating certificates..."
    if openssl req --help 2>&1 | grep -q addext; then
        openssl req -new -x509 -days $CA_CERT_DAYS -key $CA_KEY -out $CA_CERT -subj "/CN=${HOSTNAME}/C=US/L=Raleigh/" \
            -addext "subjectAltName=IP:127.0.0.1, IP:${HOST_ADDR}, DNS:localhost, DNS:${HOSTNAME}"
    else
        openssl req -new -x509 -days $CA_CERT_DAYS -key $CA_KEY -out $CA_CERT \
            -subj "/CN=$(hostname)/C=US/L=Raleigh/" \
            -config <(
                echo "[req]"
                echo distinguished_name = req
                echo x509_extensions = v3_ext
                echo "[v3_ext]"
                echo subjectKeyIdentifier = hash
                echo authorityKeyIdentifier = keyid, issuer
                echo basicConstraints = critical, CA:true
                echo subjectAltName = @san
                echo "[san]"
                echo IP.1 = 127.0.0.1
                echo IP.2 = $HOST_ADDR
                echo DNS.1 = localhost
                echo DNS.2 = $HOSTNAME )
    fi

    chmod a+r ${CERT_HOME}/*

    # Update CA trust such that it includes our CA cert
    cp -f $CA_CERT "/etc/pki/ca-trust/source/anchors/${CERT_NAME}.crt"
    update-ca-trust

    echo "Done. New certificate generated: ${CA_CERT}"
}

generate_legacy_keystore() {
    echo "Generating legacy JKS keystore..."

    local KEYSTORE="${CERT_HOME}/tc_keystore.jks"
    local KEYSTORE_PASSWORD="password" # WARNING: This value is hardcoded in update-server-xml.py
    local KEYSTORE_PASSWORD_FILE="${CERT_HOME}/tc_keystore_pass.txt"

    echo -n $KEYSTORE_PASSWORD > $KEYSTORE_PASSWORD_FILE
    openssl pkcs12 -export -in $CA_CERT -inkey $CA_KEY -out $KEYSTORE -name tomcat -CAfile $CA_CERT -caname root -chain -password file:$KEYSTORE_PASSWORD_FILE
    keytool -keystore $KEYSTORE -noprompt -importcert -storepass:file $KEYSTORE_PASSWORD_FILE -alias candlepin_ca -file $CA_CERT

    echo "Done. Keystore generated: ${KEYSTORE}"
}

# Check that we're running as root
if [ "$EUID" -ne 0 ]; then
    >&2 echo "Error: Certificate generation must be performed as root"
    exit 1
fi

print_usage() {
    cat <<USAGE
usage: ${SCRIPT_NAME} [options]

OPTIONS:
  -a <addr>   The external address of the Candlepin host that will be using the
              generated certs; defaults to the first address returned by
              \"hostname -I\"
  -H <host>   The hostname of the Candlepin host that will be using the
              generated certs; defaults to the output of the "hostname" command
  -b <bits>   The length in bits of the generated keys to use to generate the
              certificates; defaults to 4096
  -d <days>   The number of days for which the generated certificates will be
              valid; defaults to 365
  -f          Force certificate regeneration, even if the certificates have
              already been generated
  -h          Display command usage information
USAGE
}

while getopts ":a:H:b:d:fh" opt; do
    case $opt in
        a  ) HOST_ADDR="${OPTARG}" ;;
        H  ) HOSTNAME="${OPTARG}" ;;
        b  ) CERT_BIT_LENGTH=$OPTARG ;;
        d  ) CA_CERT_DAYS=$OPTARG ;;
        f  ) FORCE_REGEN="1" ;;
        h  ) print_usage; exit 0;;
        ?  ) print_usage; exit 1;;
    esac
done

if [ -f $CA_KEY ] && [ -f $CA_CERT ] && [ "${FORCE_REGEN}" != "1" ]; then
    echo "Certificates already generated; using existing certs"
else
    generate_certs

    # Check if FIPS tooling is absent or FIPS mode is disabled
    if ! command -v fips-mode-setup > /dev/null || ! fips-mode-setup --check | grep -q enabled; then
        generate_legacy_keystore
    fi
fi
