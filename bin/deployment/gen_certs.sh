#!/bin/bash

SCRIPT_NAME=$(basename "$0")
SCRIPT_HOME=$(dirname "$0")

CA_TRUST_ANCHORS="/etc/pki/ca-trust/source/anchors"
CA_CHAIN_BUNDLE="${CA_TRUST_ANCHORS}/cp-ca-chain.pem"

CP_CERT_HOME="/etc/candlepin/certs"

# Script defaults
CA_CERT=
CA_KEY=

CERT_DAYS=1095
KEY_BITS=4096
SAN=("IP:127.0.0.1" "DNS:localhost")

FORCE_REGEN=0
TRUST=0
GEN_MLDSA=0

USAGE_TEXT="Candlepin development server certificate generator
usage: ${SCRIPT_NAME} [options]

OPTIONS:
  --ca_cert <file>  The CA cert to use to sign the generated server cert. If not
                    provided, the generated certificate will be self-signed.
  --ca_key <file>   The key of the CA cert to use to sign the generated server
                    certificate. If not provided, the generated certificate will
                    be self-signed.
  --cert_dir <dir>  The output directory for all generated certs, keys, and
                    bundles; defaults to \"${CP_CERT_HOME}\"
  --days <days>     The number of days for which the generated cert will be
                    valid; defaults to ${CERT_DAYS}
  --bits <bits>     The size of the generated key in bits; defaults to ${KEY_BITS}
  --hostname <host> An additional hostname to include in the generated cert's
                    subjectAltName; may be specified multiple times
  --hostaddr <addr> An additional IP address to include in the generated cert's
                    subjectAltName; may be specified multiple times
  --pq              Also generate ML-DSA certificates for post-quantum support;
                    defaults to false (RSA only)
  -f | --force      Force regeneration and overwrite existing files when the
                    cert or key already exists; defaults to false
  -t | --trust      Add the generated cert to the local CA trust; defaults to
                    false, requires root
  -h | --help       Display command usage information
"

while true; do
    case "$1" in
        --ca_cert )
            CA_CERT="$2"
            shift 2 ;;

        --ca_key )
            CA_KEY="$2"
            shift 2 ;;

        --cert_dir )
            CP_CERT_HOME="$2"
            shift 2 ;;

        --days )
            CERT_DAYS="$2"
            shift 2 ;;

        --bits )
            KEY_BITS="$2"
            shift 2 ;;

        --hostname )
            SAN+=("DNS:$2")
            shift 2 ;;

        --hostaddr )
            SAN+=("IP:$2")
            shift 2 ;;

        --pq )
            GEN_MLDSA=1
            shift ;;

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
        * ) if [ ! -z "$1" ]; then
                echo "Invalid option: $1"
                echo "${USAGE_TEXT}"
                exit 1
            fi
            break ;;
    esac
done

# Derive output paths from CP_CERT_HOME (may have been overridden by --cert_dir)
CERT_OUT_RSA="${CP_CERT_HOME}/candlepin-rsa-ca.crt"
KEY_OUT_RSA="${CP_CERT_HOME}/candlepin-rsa-ca.key"
CERT_OUT_MLDSA="${CP_CERT_HOME}/candlepin-mldsa-65-ca.crt"
KEY_OUT_MLDSA="${CP_CERT_HOME}/candlepin-mldsa-65-ca.key"
CERT_OUT_BUNDLE="${CP_CERT_HOME}/candlepin-ca-bundle.crt"
CERT_OUT="${CP_CERT_HOME}/candlepin-ca.crt"
KEY_OUT="${CP_CERT_HOME}/candlepin-ca.key"

fail() {
    if [ ! -z "$1" ]; then
        >&2 echo "$1"
    fi

    exit 1
}

check_dependency() {
    if ! command -v "$1" > /dev/null; then
        fail "Required dependency $1 not detected; aborting..."
    fi
}

# Fail on any error
set -e

# Ensure we have all of our dependencies available
check_dependency openssl

# If the user has specified that the resultant cert should be trusted, they must also invoke this
# script as root
if [ "$EUID" -ne 0 ] && [ "$TRUST" -eq 1 ]; then
    fail "Error: CA trust update must be performed as root"
fi

# Make sure we're not overwriting existing files by accident
if [ -f "$CERT_OUT_RSA" ] && [ "$FORCE_REGEN" -ne 1 ]; then
    >&2 echo "Warning: Certificate files already exist; use --force to regenerate"

    exit 0
fi

# Ensure we can write to the output director[y/ies]
mkdir -p "$(dirname "$CERT_OUT_RSA")" "$(dirname "$KEY_OUT_RSA")"
if [ "$GEN_MLDSA" -eq 1 ]; then
    mkdir -p "$(dirname "$CERT_OUT_MLDSA")" "$(dirname "$KEY_OUT_MLDSA")"
fi

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
if [ -z "$CA_CERT" ]; then
    # No CA cert specified, generate self-signed certificates

    # Generate RSA certificate for legacy clients
    echo "Generating self-signed RSA certificate: ${CERT_OUT_RSA}"
    openssl req -new -x509 -days "$CERT_DAYS" -out "$CERT_OUT_RSA" \
        -newkey "rsa:$KEY_BITS" -nodes -keyout "$KEY_OUT_RSA" \
        -config <(echo "${OPENSSL_CONF}")

    # Ensure the key and cert are readable
    chmod +r "${KEY_OUT_RSA}" "${CERT_OUT_RSA}"

    echo "RSA PEM files generated:"
    echo "  Certificate: ${CERT_OUT_RSA}"
    echo "  Private Key: ${KEY_OUT_RSA}"

    # create legacy symlinks pointing to RSA
    echo ""
    echo "Creating legacy symlinks (pointing to RSA certificates)..."
    ln -sf "$(basename "$CERT_OUT_RSA")" "$CERT_OUT"
    ln -sf "$(basename "$KEY_OUT_RSA")" "$KEY_OUT"

    if [ "$GEN_MLDSA" -eq 1 ]; then
        # Generate ML-DSA certificate for PQC-capable clients
        echo ""
        echo "Generating self-signed ML-DSA certificate: ${CERT_OUT_MLDSA}"
        openssl req -new -x509 -days "$CERT_DAYS" -out "$CERT_OUT_MLDSA" \
            -newkey mldsa65 -nodes -keyout "$KEY_OUT_MLDSA" \
            -config <(echo "${OPENSSL_CONF}")

        # Ensure the key and cert are readable
        chmod +r "${KEY_OUT_MLDSA}" "${CERT_OUT_MLDSA}"

        echo "ML-DSA PEM files generated:"
        echo "  Certificate: ${CERT_OUT_MLDSA}"
        echo "  Private Key: ${KEY_OUT_MLDSA}"

        # Create combined CA certificate bundle for client certificate verification
        echo ""
        echo "Creating combined CA certificate bundle..."
        cat "$CERT_OUT_RSA" > "$CERT_OUT_BUNDLE"
        cat "$CERT_OUT_MLDSA" >> "$CERT_OUT_BUNDLE"
        chmod +r "$CERT_OUT_BUNDLE"
        echo "  CA Bundle: ${CERT_OUT_BUNDLE}"
    fi

    # Add certs to CA trust
    if [ "$TRUST" -eq 1 ]; then
        echo ""
        cat "$CERT_OUT_RSA" > "$CA_CHAIN_BUNDLE"

        if [ "$GEN_MLDSA" -eq 1 ]; then
            echo "Adding both RSA and ML-DSA certs to CA trust bundle..."
            cat "$CERT_OUT_MLDSA" >> "$CA_CHAIN_BUNDLE"
        else
            echo "Adding RSA cert to CA trust bundle..."
        fi

        # Invoke the ca trust update
        update-ca-trust
    fi
else
    if [ -z "$CA_KEY" ]; then
        fail "Error: CA cert provided without a CA key"
    fi

    # Declare temp CSR file variables and set up a single cleanup trap
    TMP_CSR_FILE_RSA="cp_tmp_rsa-${RANDOM}.csr"
    TMP_CSR_FILE_MLDSA="cp_tmp_mldsa-${RANDOM}.csr"
    trap 'rm -f "$TMP_CSR_FILE_RSA" "$TMP_CSR_FILE_MLDSA"' EXIT

    # Generate RSA server certificate
    echo "Generating RSA server certificate: ${CERT_OUT_RSA}"

    # Generate the CSR for the RSA server cert
    openssl req -new -sha256 -out "$TMP_CSR_FILE_RSA" \
        -newkey "rsa:$KEY_BITS" -nodes -keyout "$KEY_OUT_RSA" \
        -config <(echo "${OPENSSL_CONF}")

    # Create cert from CSR
    # Impl note: at the time of writing, some of our platforms don't support the -copy_extensions
    # CLI option, so we have to do it ourselves. :(
    CERT_SERIAL="${RANDOM}${RANDOM}"
    openssl x509 -req -in "$TMP_CSR_FILE_RSA" -CA "$CA_CERT" -CAkey "$CA_KEY" -set_serial "$CERT_SERIAL" \
        -days "$CERT_DAYS" -out "$CERT_OUT_RSA" \
        -extensions v3_ext -extfile <(echo "${V3_EXT_CONF}")

    # Ensure the key and cert are readable
    chmod +r "${KEY_OUT_RSA}" "${CERT_OUT_RSA}"

    echo "RSA PEM files generated:"
    echo "  Certificate: ${CERT_OUT_RSA}"
    echo "  Private Key: ${KEY_OUT_RSA}"
    echo "  CA Certificate: ${CA_CERT}"

    # RSA only: create legacy symlinks pointing to RSA
    echo ""
    echo "Creating legacy symlinks (pointing to RSA certificates)..."
    ln -sf "$(basename "$CERT_OUT_RSA")" "$CERT_OUT"
    ln -sf "$(basename "$KEY_OUT_RSA")" "$KEY_OUT"

    if [ "$GEN_MLDSA" -eq 1 ]; then
        # Generate ML-DSA server certificate
        echo ""
        echo "Generating ML-DSA server certificate: ${CERT_OUT_MLDSA}"

        # Generate the CSR for the ML-DSA server cert
        openssl req -new -out "$TMP_CSR_FILE_MLDSA" \
            -newkey mldsa65 -nodes -keyout "$KEY_OUT_MLDSA" \
            -config <(echo "${OPENSSL_CONF}")

        # Create cert from CSR
        CERT_SERIAL="${RANDOM}${RANDOM}"
        openssl x509 -req -in "$TMP_CSR_FILE_MLDSA" -CA "$CA_CERT" -CAkey "$CA_KEY" -set_serial "$CERT_SERIAL" \
            -days "$CERT_DAYS" -out "$CERT_OUT_MLDSA" \
            -extensions v3_ext -extfile <(echo "${V3_EXT_CONF}")

        # Ensure the key and cert are readable
        chmod +r "${KEY_OUT_MLDSA}" "${CERT_OUT_MLDSA}"

        echo "ML-DSA PEM files generated:"
        echo "  Certificate: ${CERT_OUT_MLDSA}"
        echo "  Private Key: ${KEY_OUT_MLDSA}"
        echo "  CA Certificate: ${CA_CERT}"

        # Create combined CA certificate bundle for client certificate verification
        echo ""
        echo "Creating combined CA certificate bundle..."
        cat "$CERT_OUT_RSA" > "$CERT_OUT_BUNDLE"
        cat "$CERT_OUT_MLDSA" >> "$CERT_OUT_BUNDLE"
        chmod +r "$CERT_OUT_BUNDLE"
        echo "  CA Bundle: ${CERT_OUT_BUNDLE}"
    fi

    # Add certs to CA trust
    if [ "$TRUST" -eq 1 ]; then
        echo ""
        cat "$CERT_OUT_RSA" > "$CA_CHAIN_BUNDLE"
        cat "$CA_CERT" >> "$CA_CHAIN_BUNDLE"

        if [ "$GEN_MLDSA" -eq 1 ]; then
            echo "Adding both RSA and ML-DSA cert chains to CA trust bundle..."
            cat "$CERT_OUT_MLDSA" >> "$CA_CHAIN_BUNDLE"
        else
            echo "Adding RSA cert chain to CA trust bundle..."
        fi

        # Invoke the ca trust update
        update-ca-trust
    fi
fi

echo "Done!"
