#!/bin/bash

SCRIPT_NAME=$(basename "$0")
SCRIPT_HOME=$(dirname "$0")

OPENSSL_CONF="${SCRIPT_HOME}/openssl.cnf"

# Script defaults
KEY_BITS=4096
KEY_OUT="${SCRIPT_HOME}/cp-dev-ca.key"
CERT_DAYS=3650
CERT_OUT="${SCRIPT_HOME}/cp-dev-ca.crt"
FORCE_REGEN=0

USAGE_TEXT="Candlepin development root CA certificate generator
usage: ${SCRIPT_NAME} [options]

OPTIONS:
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
  -h          Display command usage information
"

while getopts ":b:k:d:c:fh" OPT; do
    case $OPT in
        b ) KEY_BITS=$OPTARG ;;
        d ) CERT_DAYS=$OPTARG ;;
        o ) CERT_DIR=$OPTARG ;;
        n ) CERT_NAME=$OPTARG ;;
        f ) FORCE_REGEN=1 ;;
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
        fail "Required dependency \"$1\" not detected; aborting..."
    fi
}

# Fail on any error
set -e

# Ensure we have all of our dependencies available
check_dependency openssl

# Build the root CA cert
if [ ! -f $CERT_OUT ] || [ $FORCE_REGEN -eq 1 ]; then
    echo "Generating root CA certificate: ${CERT_OUT}"

    openssl req -new -x509 -days 3650 -subj "/CN=Candlepin Dev Root CA/OU=Candlepin" -out $CERT_OUT \
        -newkey rsa:$KEY_BITS -nodes -keyout $KEY_OUT \
        -config $OPENSSL_CONF -extensions cp_root_ca

    # Ensure the key and cert are readable
    chmod +r "${KEY_OUT}" "${CERT_OUT}"
else
    >&2 echo "Warning: certificate file already exists, using existing"
fi

echo "Done!"
