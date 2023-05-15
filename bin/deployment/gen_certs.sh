#!/bin/bash

SCRIPT_NAME=$(basename "$0")
SCRIPT_HOME=$(dirname "$0")

CA_TRUST_ANCHORS="/etc/pki/ca-trust/source/anchors"
CA_CHAIN_BUNDLE="${CA_TRUST_ANCHORS}/cp-ca-chain.pem"

CP_CERT_HOME="/etc/candlepin/certs"
TMP_CSR_FILE="cp_tmp-${RANDOM}.csr"

# Script defaults
CA_CERT=
CA_KEY=

CERT_OUT="${CP_CERT_HOME}/candlepin-ca.crt"
KEY_OUT="${CP_CERT_HOME}/candlepin-ca.key"
CERT_DAYS=1095
KEY_BITS=4096
SAN=("IP:127.0.0.1" "DNS:localhost")

FORCE_REGEN=0
TRUST=0

USAGE_TEXT="Candlepin development server certificate generator
usage: ${SCRIPT_NAME} [options]

OPTIONS:
  --ca_cert <file>  The CA cert to use to sign the generated server cert. If not
                    provided, the generated certificate will be self-signed.
  --ca_key <file>   The key of the CA cert to use to sign the generated server
                    certificate. If not provided, the generated certificate will
                    be self-signed.
  --cert_out <file> The output filename for the generated CA key; defaults to
                    \"${CERT_OUT}\"
  --key_out <file>  The output filename for the generated CA key; defaults to
                    \"${KEY_OUT}\"
  --days <days>     The number of days for which the generated cert will be
                    valid; defaults to ${CERT_DAYS}
  --bits <bits>     The size of the generated key in bits; defaults to ${KEY_BITS}
  --hostname <host> An additional hostname to include in the generated cert's
                    subjectAltName; may be specified multiple times
  --hostaddr <addr> An additional IP address to include in the generated cert's
                    subjectAltName; may be specified multiple times
  -f | --force      Force regeneration and overwrite existing files when the
                    cert or key already exists; defaults to false
  -t | --trust      Add the generated cert to the local CA trust; defaults to
                    false, requires root
  -h | --help       Display command usage information
"

while true; do
    case "$1" in
        --ca_cert )
            CA_CERT=$2
            shift 2 ;;

        --ca_key )
            CA_KEY=$2
            shift 2 ;;

        --cert_out )
            CERT_OUT=$2
            shift 2 ;;

        --key_out )
            KEY_OUT=$2
            shift 2 ;;

        --days )
            CERT_DAYS=$2
            shift 2 ;;

        --bits )
            KEY_BITS=$2
            shift 2 ;;

        --hostname )
            SAN+=("DNS:$2")
            shift 2 ;;

        --hostaddr )
            SAN+=("IP:$2")
            shift 2 ;;

        -f | --force )
            FORCE_REGEN=1
            shift ;;

        -t | --trust )
            TRUST=1
            shift ;;

        -h | --help )
            echo "${USAGE_TEXT}"
            exit 0 ;;

        -- ) shift; break ;;
        * ) if [ ! -z $1 ]; then
                echo "Invalid option: $1"
                echo "${USAGE_TEXT}"
                exit 1
            fi
            break ;;
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

# If the user has specified that the resultant cert should be trusted, they must also invoke this
# script as root
if [ $EUID -ne 0 ] && [ $TRUST -eq 1 ]; then
    fail "Error: CA trust update must be performed as root"
fi

# Make sure we're not overwriting existing files by accident
if [ -f $CERT_OUT ] && [ $FORCE_REGEN -ne 1 ]; then
    >&2 echo "Warning: Certificate file \"${CERT_OUT}\" already exists; use --force to regenerate"

    exit 0
fi

# Ensure we can write to the output director[y/ies]
mkdir -p "$(dirname $CERT_OUT)" "$(dirname $KEY_OUT)"

# Compile openssl config...
V3_EXT_CONF="""
    [ v3_ext ]
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always, issuer:always
    basicConstraints = critical, CA:true
    keyUsage = critical, keyCertSign, digitalSignature, cRLSign
    subjectAltName=$(IFS=, ; echo "${SAN[*]}")
    """

OPENSSL_CONF="""
    [ req ]
    string_mask = utf8only
    distinguished_name = req_dn
    prompt = no
    x509_extensions = v3_ext

    [ req_dn ]
    CN=Candlepin Server CA
    OU=Candlepin
    O=Red Hat

    ${V3_EXT_CONF}
    """

# Generate certs
if [ -z $CA_CERT ]; then
    # No CA cert specified, generate a self-signed cert
    echo "Generating self-signed certificate: ${CERT_OUT}"

    openssl req -new -x509 -days $CERT_DAYS -out $CERT_OUT \
        -newkey rsa:$KEY_BITS -nodes -keyout $KEY_OUT \
        -config <(echo "${OPENSSL_CONF}")

    # Ensure the key and cert are readable
    chmod +r "${KEY_OUT}" "${CERT_OUT}"

    # Add new cert to CA trust
    if [ $TRUST -eq 1 ]; then
        echo "Adding cert to CA trust bundle..."

        # Build the chain from the server CA and root CA
        cat $CERT_OUT > $CA_CHAIN_BUNDLE

        # Invoke the ca trust update
        update-ca-trust
    fi
else
    if [ -z $CA_KEY ]; then
        fail "Error: CA cert provided without a CA key"
    fi

    echo "Generating server certificate: ${CERT_OUT}"

    # Generate the CSR for the server cert
    openssl req -new -sha256 -out $TMP_CSR_FILE \
        -newkey rsa:$KEY_BITS -nodes -keyout $KEY_OUT \
        -config <(echo "${OPENSSL_CONF}")

    # Ensure we cleanup the CSR on exit
    trap "rm -f ${TMP_CSR_FILE}" EXIT

    # Create cert from CSR
    # Impl note: at the time of writing, some of our platforms don't support the -copy_extensions
    # CLI option, so we have to do it ourselves. :(
    CERT_SERIAL="${RANDOM}${RANDOM}"
    openssl x509 -req -in $TMP_CSR_FILE -CA $CA_CERT -CAkey $CA_KEY -set_serial $CERT_SERIAL \
        -days $CERT_DAYS -out $CERT_OUT \
        -extensions v3_ext -extfile <(echo "${V3_EXT_CONF}")

    # Ensure the key and cert are readable
    chmod +r "${KEY_OUT}" "${CERT_OUT}"

    # Add new cert to CA trust
    if [ $TRUST -eq 1 ]; then
        echo "Adding cert chain to CA trust bundle..."

        # Build the chain from the server CA and root CA
        cat $CERT_OUT > $CA_CHAIN_BUNDLE
        cat $CA_CERT >> $CA_CHAIN_BUNDLE

        # Invoke the ca trust update
        update-ca-trust
    fi
fi

echo "Done!"
