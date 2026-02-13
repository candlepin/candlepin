#!/bin/bash
# Configure BouncyCastle providers for local Tomcat installation
# Required for ML-DSA KeyFactory support in Tomcat 9.0.110+

set -e

TOMCAT_HOME="/opt/tomcat"
BC_LIB_DIR="${TOMCAT_HOME}/lib"
PROJECT_DIR="$(git rev-parse --show-toplevel)"

echo "=========================================="
echo "Configuring BouncyCastle for Tomcat"
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

# Create setenv.sh to register BC providers
echo ""
echo "[3/3] Creating setenv.sh to register BouncyCastle providers..."

# Find all BC jars
BC_JARS_LIST=$(find "${BC_LIB_DIR}" -name "bc*.jar" | tr '\n' ':' | sed 's/:$//')

sudo tee "${TOMCAT_HOME}/bin/setenv.sh" > /dev/null <<EOF
#!/bin/bash
# BouncyCastle security provider configuration for ML-DSA support

# Add BC JARs to bootclasspath so they're available during security provider initialization
export JAVA_OPTS="\$JAVA_OPTS -Xbootclasspath/a:${BC_JARS_LIST}"

# Register BouncyCastle providers at positions 3-4
# Early enough to be found for ML-DSA, but after core Sun providers (SUN, SunRsaSign)
export JAVA_OPTS="\$JAVA_OPTS -Djava.security.provider.3=org.bouncycastle.jce.provider.BouncyCastleProvider"
export JAVA_OPTS="\$JAVA_OPTS -Djava.security.provider.4=org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider"

echo "BouncyCastle providers configured for ML-DSA support"
EOF

sudo chmod +x "${TOMCAT_HOME}/bin/setenv.sh"

# Set ownership and permissions
sudo chown tomcat:tomcat "${TOMCAT_HOME}/bin/setenv.sh"

# Set ownership on BC jars if they exist and make them readable
if ls "${BC_LIB_DIR}"/bc*.jar 1> /dev/null 2>&1; then
    sudo chown tomcat:tomcat "${BC_LIB_DIR}"/bc*.jar
    sudo chmod 644 "${BC_LIB_DIR}"/bc*.jar
fi

# Ensure lib directory is accessible
sudo chmod 755 "${BC_LIB_DIR}"

echo ""
echo "=========================================="
echo "Configuration Complete!"
echo "=========================================="
echo ""
echo "BouncyCastle JARs added to: ${BC_LIB_DIR}"
echo ""
echo "Providers registered in setenv.sh:"
echo "  Position 3: org.bouncycastle.jce.provider.BouncyCastleProvider"
echo "  Position 4: org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider"
echo ""
echo "Positioned after core Sun providers but early enough for ML-DSA KeyFactory lookup"
echo ""
