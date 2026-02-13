#!/bin/bash
# Download and install Tomcat 9.0.110+ locally for ML-DSA support
# Installs to /opt/tomcat

set -e

TOMCAT_VERSION="9.0.110"
TOMCAT_INSTALL_DIR="/opt/tomcat"
TOMCAT_USER="tomcat"
TOMCAT_GROUP="tomcat"

echo "=========================================="
echo "Installing Tomcat ${TOMCAT_VERSION}"
echo "=========================================="
echo ""

# Install Java if not present
echo "[1/6] Checking for Java..."
if ! command -v java &> /dev/null; then
    echo "Installing Java..."
    sudo dnf install -y java-17-openjdk-devel
else
    java -version
fi

# Create tomcat user if it doesn't exist
echo ""
echo "[2/6] Creating tomcat user..."
if ! id "${TOMCAT_USER}" &>/dev/null; then
    sudo useradd -r -m -U -d /opt/tomcat -s /bin/false ${TOMCAT_USER}
    echo "Created user: ${TOMCAT_USER}"
else
    echo "User ${TOMCAT_USER} already exists"
fi

# Download Tomcat
echo ""
echo "[3/6] Downloading Tomcat ${TOMCAT_VERSION}..."
DOWNLOAD_DIR="/tmp/tomcat-download"
mkdir -p "${DOWNLOAD_DIR}"
cd "${DOWNLOAD_DIR}"

TOMCAT_TAR="apache-tomcat-${TOMCAT_VERSION}.tar.gz"
TOMCAT_URL="https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/${TOMCAT_TAR}"

if [ ! -f "${TOMCAT_TAR}" ]; then
    wget "${TOMCAT_URL}"
fi

# Extract and install
echo ""
echo "[4/6] Installing to ${TOMCAT_INSTALL_DIR}..."
sudo mkdir -p "${TOMCAT_INSTALL_DIR}"
sudo tar -xzf "${TOMCAT_TAR}" -C "${TOMCAT_INSTALL_DIR}" --strip-components=1

# Set permissions
echo ""
echo "[5/6] Setting permissions..."
sudo chown -R ${TOMCAT_USER}:${TOMCAT_GROUP} "${TOMCAT_INSTALL_DIR}"
sudo chmod +x "${TOMCAT_INSTALL_DIR}/bin"/*.sh
sudo chmod -R g+r,o+r "${TOMCAT_INSTALL_DIR}"

# Create systemd service
echo ""
echo "[6/6] Creating systemd service..."
PROJECT_DIR="$(git rev-parse --show-toplevel)"
sudo cp "${PROJECT_DIR}/bin/deployment/tomcat-local.service" /etc/systemd/system/tomcat-local.service

# Reload systemd
sudo systemctl daemon-reload

# Create directories
sudo mkdir -p /var/log/candlepin
sudo mkdir -p /var/lib/candlepin
sudo mkdir -p /var/cache/candlepin
sudo chown -R ${TOMCAT_USER}:${TOMCAT_GROUP} /var/log/candlepin
sudo chown -R ${TOMCAT_USER}:${TOMCAT_GROUP} /var/lib/candlepin
sudo chown -R ${TOMCAT_USER}:${TOMCAT_GROUP} /var/cache/candlepin

# Stop and disable system tomcat if running
if systemctl is-active --quiet tomcat 2>/dev/null; then
    echo ""
    echo "Stopping system tomcat service..."
    sudo systemctl stop tomcat
    sudo systemctl disable tomcat
fi

echo ""
echo "=========================================="
echo "Installation Complete!"
echo "=========================================="
echo ""
echo "Tomcat ${TOMCAT_VERSION} installed at: ${TOMCAT_INSTALL_DIR}"
echo ""
echo "Service: tomcat-local"
echo "  Start:  sudo systemctl start tomcat-local"
echo "  Stop:   sudo systemctl stop tomcat-local"
echo "  Status: sudo systemctl status tomcat-local"
echo ""
echo "Configuration: ${TOMCAT_INSTALL_DIR}/conf"
echo "Webapps:       ${TOMCAT_INSTALL_DIR}/webapps"
echo "Logs:          ${TOMCAT_INSTALL_DIR}/logs"
echo ""
echo "Next: Run deployment with -p flag for ML-DSA support"
echo "  ./bin/deployment/deploy -f -p"
echo ""
