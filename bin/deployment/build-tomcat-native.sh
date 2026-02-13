#!/bin/bash
# Build tomcat-native from source against OpenSSL 3.5+
# This ensures ML-DSA support through native OpenSSL

set -e

TOMCAT_NATIVE_VERSION="2.0.8"
BUILD_DIR="/tmp/tomcat-native-build"
INSTALL_PREFIX="/opt/tomcat-native"

echo "=========================================="
echo "Building Tomcat Native ${TOMCAT_NATIVE_VERSION}"
echo "=========================================="
echo ""

# Install build dependencies
echo "[1/5] Installing build dependencies..."
sudo dnf install -y gcc make apr-devel openssl-devel

# Verify OpenSSL version
echo ""
echo "[2/5] Verifying OpenSSL version..."
openssl version
OPENSSL_VERSION=$(openssl version | cut -d' ' -f2)
echo "OpenSSL version: ${OPENSSL_VERSION}"

if [[ ! "${OPENSSL_VERSION}" =~ ^3\.[5-9] ]]; then
    echo "WARNING: OpenSSL version is ${OPENSSL_VERSION}"
    echo "ML-DSA support requires OpenSSL 3.5 or higher"
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Download tomcat-native source
echo ""
echo "[3/5] Downloading Tomcat Native ${TOMCAT_NATIVE_VERSION} source..."
mkdir -p "${BUILD_DIR}"
cd "${BUILD_DIR}"

if [ ! -f "tomcat-native-${TOMCAT_NATIVE_VERSION}-src.tar.gz" ]; then
    wget "https://archive.apache.org/dist/tomcat/tomcat-connectors/native/${TOMCAT_NATIVE_VERSION}/source/tomcat-native-${TOMCAT_NATIVE_VERSION}-src.tar.gz"
fi

tar -xzf "tomcat-native-${TOMCAT_NATIVE_VERSION}-src.tar.gz"
cd "tomcat-native-${TOMCAT_NATIVE_VERSION}-src/native"

# Configure and build
echo ""
echo "[4/5] Configuring and building..."
./configure \
    --with-apr=/usr/bin/apr-1-config \
    --with-ssl=/usr \
    --prefix="${INSTALL_PREFIX}"

make

# Install
echo ""
echo "[5/5] Installing to ${INSTALL_PREFIX}..."
sudo make install

# Configure library path
echo ""
echo "Configuring library path..."
sudo sh -c "echo '${INSTALL_PREFIX}/lib' > /etc/ld.so.conf.d/tomcat-native.conf"
sudo ldconfig

# Verify installation
echo ""
echo "=========================================="
echo "Verifying installation..."
echo "=========================================="

# Find the installed library (could be libtcnative-1.so or libtcnative-2.so)
TCNATIVE_LIB=$(find "${INSTALL_PREFIX}/lib" -name "libtcnative-*.so" | head -n 1)

if [ -f "${TCNATIVE_LIB}" ]; then
    echo "Library found at: ${TCNATIVE_LIB}"
    strings "${TCNATIVE_LIB}" | grep -i openssl | head -5
else
    echo "ERROR: Library not found at expected location"
    exit 1
fi

echo ""
echo "=========================================="
echo "Build Complete!"
echo "=========================================="
echo ""
echo "Library installed at: ${TCNATIVE_LIB}"
echo ""
echo "Next steps:"
echo "1. Configure Tomcat to use this library by setting in /etc/sysconfig/tomcat:"
echo "   export LD_LIBRARY_PATH=${INSTALL_PREFIX}/lib:\$LD_LIBRARY_PATH"
echo ""
echo "2. Run deployment: ./bin/deployment/deploy -f -p"
echo ""
