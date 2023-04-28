#!/bin/bash

SCRIPT_NAME=$(basename "$0")
SCRIPT_HOME=$(dirname "$0")

CA_TRUST_ANCHORS="/etc/pki/ca-trust/source/anchors"
CA_CHAIN_BUNDLE="${CA_TRUST_ANCHORS}/cp-ca-chain.pem"
OPENSSL_CONF="${SCRIPT_HOME}/openssl.cnf"

CP_CERT_HOME="/etc/candlepin/certs"
TMP_CSR_FILE="cp_tmp-${RANDOM}.csr"

# Script defaults
CA_CERT="${SCRIPT_HOME}/cp-dev-ca.crt"
CA_KEY="${SCRIPT_HOME}/cp-dev-ca.key"

KEY_BITS=4096
KEY_OUT="${CP_CERT_HOME}/candlepin-ca.key"
CERT_DAYS=1095
CERT_OUT="${CP_CERT_HOME}/candlepin-ca.crt"
FORCE_REGEN=0
TRUST=0

USAGE_TEXT="Candlepin development server certificate generator
usage: ${SCRIPT_NAME} [options]

OPTIONS:
  -C <file>   The CA cert to use to sign the generated server cert; defaults to
              ${CA_CERT}
  -K <file>   The key of the CA cert to use to sign the generated server cert;
              defaults to "${CA_KEY}"
  -b <bits>   The length in bits of the generated key to use to generate the
              certificate; defaults to ${KEY_BITS}
  -k <file>   The output filename for the generated CA key; defaults to
              "${KEY_OUT}"
  -d <days>   The number of days for which the generated certificates will be
              valid; defaults to ${CERT_DAYS}
  -c <file>   The output filename for the generated CA key; defaults to
              "${CERT_OUT}"
  -f          Force regeneration and overwrite existing files when the cert or
              key already exists; defaults to false
  -t          Add the generated cert to the local CA trust; defaults to false,
              requires root
  -h          Display command usage information
"

while getopts ":K:C:b:k:d:c:fth" OPT; do
    case $OPT in
        K ) CA_KEY=$OPTARG ;;
        C ) CA_CERT=$OPTARG ;;
        b ) KEY_BITS=$OPTARG ;;
        d ) CERT_DAYS=$OPTARG ;;
        o ) CERT_DIR=$OPTARG ;;
        n ) CERT_NAME=$OPTARG ;;
        f ) FORCE_REGEN=1 ;;
        t ) TRUST=1 ;;
        h ) echo "${USAGE_TEXT}"
            exit 0
            ;;
        ? ) echo "${USAGE_TEXT}"
            exit 1
            ;;
    esac
done

fail() {
    if [ ! -z "$1" ]; then
        >&2 echo "$1"
    fi

    exit 1
}

check_dependency() {
    if ! command -v $1 > /dev/null; then
        fail "Required dependency $1 not detected; aborting..."
    fi
}

# Fail on any error
set -e

# Ensure we have all of our dependencies available
check_dependency openssl

# Ensure the specified input CA key and cert exist
if [ ! -f $CA_CERT ]; then
    fail "Error: CA cert \"${CA_CERT}\" does not exist or cannot be read"
fi

if [ ! -f $CA_KEY ]; then
    fail "Error: CA key \"${CA_KEY}\" does not exist or cannot be read"
fi

# If the user has specified that the resultant cert should be trusted, they must also invoke this
# script as root
if [ $EUID -ne 0 ] && [ $TRUST -eq 1 ]; then
    fail "Error: CA trust update must be performed as root"
fi

# Make sure we're not overwriting existing files by accident
if [ -f $CERT_OUT ] && [ $FORCE_REGEN -ne 1 ]; then
    >&2 echo "Warning: certificate file already exists; using existing. Use -f to forcefully regenerate."
    exit 0
fi

echo "Generating server certificate: ${CERT_OUT}"

# Generate the CSR for the server cert
openssl req -new -sha256 -subj "/CN=Candlepin Server CA/OU=Candlepin/" \
    -newkey "rsa:${KEY_BITS}" -nodes -keyout $KEY_OUT \
    -out $TMP_CSR_FILE

# Ensure we cleanup the CSR on exit
trap "rm -f ${TMP_CSR_FILE}" EXIT

# Create cert from CSR
openssl x509 -req -in $TMP_CSR_FILE -CA $CA_CERT -CAkey $CA_KEY -CAcreateserial \
    -days $CERT_DAYS -extfile $OPENSSL_CONF -extensions cp_server_ca \
    -out $CERT_OUT

# Ensure the key and cert are readable
chmod +r "${KEY_OUT}" "${CERT_OUT}"

# Add new cert to CA trust
if [ $TRUST -eq 1 ]; then
    echo "Adding cert chain to CA trust bundle..."

    # Build the chain from the server CA and root CA
    cat $CERT_OUT > $CA_CHAIN_BUNDLE
    cat $CA_CERT >> $CA_CHAIN_BUNDLE

    # Invoke the update, which should result in both certs ending up in the
    # keystore
    update-ca-trust
fi

echo "Done!"
