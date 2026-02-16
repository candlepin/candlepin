#!/bin/bash
# Add BouncyCastle jars to tomcat lib for it to use the BC security providers
# Required for ML-DSA KeyFactory support in Tomcat 9.0.110+
# The jars will be loaded by the tomcat-local.service systemd file.

set -e

TOMCAT_HOME="/opt/tomcat"
BC_LIB_DIR="${TOMCAT_HOME}/lib"
PROJECT_DIR="$(git rev-parse --show-toplevel)"

echo "=========================================="
echo "Adding BouncyCastle libs for Tomcat"
echo "=========================================="
echo ""

# Check if Tomcat exists
if [ ! -d "${TOMCAT_HOME}" ]; then
    echo "ERROR: Tomcat not found at ${TOMCAT_HOME}"
    echo "Run ./bin/deployment/install-tomcat.sh first"
    exit 1
fi

# Find the candlepin WAR file
echo "[1/3] Finding BouncyCastle JARs..."
WAR_FILE=$(find "${PROJECT_DIR}/build/libs" -name "candlepin-*.war" | head -n 1)

if [ -z "${WAR_FILE}" ] || [ ! -f "${WAR_FILE}" ]; then
    echo "ERROR: Candlepin WAR file not found in ${PROJECT_DIR}/build/libs/"
    echo "Build the WAR first with: ./gradlew war"
    exit 1
fi

echo "Found WAR: ${WAR_FILE}"

# Extract BC jars from WAR
echo ""
echo "[2/3] Extracting BouncyCastle JARs to ${BC_LIB_DIR}..."

# List all BC jars in the WAR
BC_JARS=$(unzip -l "${WAR_FILE}" | grep "WEB-INF/lib/bc" | grep "\.jar" | awk '{print $NF}')

if [ -z "$BC_JARS" ]; then
    echo "ERROR: No BouncyCastle JARs found in WAR file"
    exit 1
fi

# Extract each BC jar
for jar_in_war in $BC_JARS; do
    jar_name=$(basename "$jar_in_war")

    # Check if already exists
    if [ -f "${BC_LIB_DIR}/${jar_name}" ]; then
        echo "  ✓ ${jar_name} already exists"
    else
        echo "  Extracting ${jar_name}..."
        sudo unzip -q -j "${WAR_FILE}" "${jar_in_war}" -d "${BC_LIB_DIR}"
    fi
done

# Find all BC jars
BC_JARS_LIST=$(find "${BC_LIB_DIR}" -name "bc*.jar" | tr '\n' ':' | sed 's/:$//')

# Set ownership on BC jars if they exist and make them readable
if ls "${BC_LIB_DIR}"/bc*.jar 1> /dev/null 2>&1; then
    sudo chown tomcat:tomcat "${BC_LIB_DIR}"/bc*.jar
    sudo chmod 644 "${BC_LIB_DIR}"/bc*.jar
fi

# Ensure lib directory is accessible
sudo chmod 755 "${BC_LIB_DIR}"

echo ""
echo "=========================================="
echo "BouncyCastle JARs added to: ${BC_LIB_DIR}!"
echo "=========================================="
echo ""
echo "You MUST manually register the providers in your JVM's java.security file, positioned after all other providers:"
echo ""
echo "  security.provider.13=org.bouncycastle.jce.provider.BouncyCastleProvider"
echo "  security.provider.14=org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider"
echo ""
