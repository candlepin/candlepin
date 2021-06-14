#!/usr/bin/python

# Copyright (c) 2018 Red Hat, Inc.
#
# This software is licensed to you under the GNU General Public License,
# version 2 (GPLv2). There is NO WARRANTY for this software, express or
# implied, including the implied warranties of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
# along with this software; if not, see
# http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
#
# Red Hat trademarks are not licensed under GPLv2. No permission is
# granted to use or replicate Red Hat trademarks that are incorporated
# in this software or its documentation.
#

import os
import subprocess
import libxml2
import shutil
from contextlib import contextmanager
from optparse import OptionParser
import logging
import urllib2

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger('configure-artemis')

BASE_DIR = os.path.dirname(os.path.realpath(__file__))
DEFAULT_VERSION = "2.12.0"

# Installation paths for Artemis as suggested by the docs.
# For this reason, we are not allowing these to be changed by
# the caller.
INSTALL_DIR = "/opt"
BROKER_ROOT = "/var/lib/artemis"
BROKER_NAME = "candlepin"

SELINUX_BASE_DIR = os.path.join(BASE_DIR, "selinux")
SELINUX_POLICY_TEMPLATE = os.path.join(SELINUX_BASE_DIR, "artemis.te")
SELINUX_BUILD_DIR = os.path.join(SELINUX_BASE_DIR, "build")

SERVICE_TEMPLATE_PATH = os.path.join(BASE_DIR, "service", "artemis.service")
SERVICE_FILE_TARGET_PATH = "/usr/lib/systemd/system/artemis.service"


@contextmanager
def open_xml(filename):
    """libxml2 does not handle cleaning up memory automatically. This
    context manager will take care of XML documents."""
    doc = libxml2.parseFile(filename)
    yield doc
    doc.freeDoc()


def is_debug():
    return logger.level == logging.DEBUG


def download_artemis(version, target_dir):
    filename = "apache-artemis-%s-bin.tar.gz" % (version)
    path_to_file = os.path.join(target_dir, filename)
    url = "https://archive.apache.org/dist/activemq/activemq-artemis/%s/%s" % (version, filename)

    logger.info("Downloading Artemis version %s" % version)
    if os.path.exists(path_to_file):
        logger.debug("Artemis already downloaded.")
        return path_to_file

    try:
        logger.debug("Downloading artemis: %s" % (url))
        response = urllib2.urlopen(url, timeout = 5)
        content = response.read()

        with open(path_to_file, 'w') as dl_file:
            dl_file.write(content)

        return path_to_file

    except urllib2.URLError as e:
        logger.error("Unable to download artemis install file: %s" % (url))
        raise e


def extract_artemis(basedir, path_to_file):
    filename = os.path.basename(path_to_file).replace("-bin.tar.gz", "")
    file_path = os.path.join(basedir, filename)
    logger.info("Extracting Artemis package...")
    if not os.path.exists(file_path):
        logger.debug("Extracting %s to %s" % (path_to_file, basedir))
        # Could use tarfile lib here but it appears to have an issue
        # extracting the entire archive in older versions of the lib.
        call("sudo tar xvzf %s -C %s" % (path_to_file, basedir), "Failed to extract artemis package.")
    else:
        logger.debug("File already extracted.")
    return file_path


def install_artemis(version, install_path="/opt"):
    dl_file_path = download_artemis(version, install_path)
    return extract_artemis(install_path, dl_file_path)


def create_broker(artemis_install_path, broker_root_path):
    if not os.path.exists(broker_root_path):
        os.mkdir(broker_root_path)

    broker_path = os.path.join(broker_root_path, BROKER_NAME)
    if os.path.exists(broker_path):
        logger.info("Broker already exists, skipping creation.")
        return broker_path

    logger.info("Creating artemis broker: %s" % (broker_path))
    cmd = "%s/bin/artemis create --user admin --password admin --allow-anonymous %s" % \
          (artemis_install_path, broker_path)
    call(cmd, "Failed to create broker.")
    return broker_path


def call(cmd, error_msg, allow_failure=False):
    logger.debug("Calling '%s'" % cmd)
    with open(os.devnull, 'w') as dnull:
        std_out = None if is_debug() else dnull
        ret = subprocess.call(cmd.split(" "), stdout=std_out, stderr=subprocess.STDOUT)
        if not allow_failure and ret:
            raise RuntimeError(error_msg)


def call_with_array(cmd, error_msg, allow_failure=False):
    logger.debug("Calling '%s'" % " ".join(cmd))
    with open(os.devnull, 'w') as dnull:
        std_out = None if is_debug() else dnull
        ret = subprocess.call(cmd, stdout=std_out, stderr=subprocess.STDOUT)
        if not allow_failure and ret:
            raise RuntimeError(error_msg)


def install_artemis_service():
    logger.info("Setting up artemis service.")

    logger.debug("Creating artemis user.")
    call("sudo useradd artemis --home %s" % BROKER_ROOT, "Failed to create 'artemis' user.", True)

    logger.debug("Setting file permissions for artemis user.")
    call("sudo chown -R artemis:artemis %s" % BROKER_ROOT, "Failed to set permissions for artemis user.")

    logger.debug("Installing artemis service file.")
    shutil.copy(SERVICE_TEMPLATE_PATH, SERVICE_FILE_TARGET_PATH)

    logger.debug("Reloading systemd daemons.")
    call("sudo systemctl daemon-reload", "Failed to reload systemd daemons.")

    logger.debug("Enabling artemis service.")
    call("sudo systemctl enable artemis", "Failed to enable artemis service")

    setup_selinux()


def setup_selinux():
    logger.debug("Setting up SELinux policy.")

    if not os.path.exists(SELINUX_BUILD_DIR):
        os.mkdir(SELINUX_BUILD_DIR)

    call("checkmodule -M -m -o %s/artemis.mod %s" % (SELINUX_BUILD_DIR, SELINUX_POLICY_TEMPLATE),
         "Failed to compile SELinux module for artemis service.")
    call("semodule_package -m %s/artemis.mod -o %s/artemis.pp" % (SELINUX_BUILD_DIR, SELINUX_BUILD_DIR),
         "Failed to package SELinux module for artemis service.")
    call("sudo semodule -vr artemis", "", allow_failure=True)
    call("sudo semodule -vi %s/artemis.pp" % SELINUX_BUILD_DIR,
         "Failed to load SELinux policy for artemis service")


def generate_certs(broker_path):
    logger.debug("Creating the certs/ directory.")
    certs_dir = os.path.join(broker_path, "certs/")
    if os.path.exists(certs_dir):
        shutil.rmtree(certs_dir)
        logger.debug("%s already exists. Recreating it now." % certs_dir)
    os.mkdir(certs_dir)

    logger.debug("Setting file permissions on certs directory for artemis user.")
    call("sudo chown -R artemis:artemis %s" % certs_dir, "Failed to set permissions on certs directory.")

    logger.debug("Generating a server keystore.")
    call_with_array(["sudo", "keytool", "-genkey", "-keystore", "%s/artemis-server.ks" % certs_dir,
                     "-storepass", "securepassword", "-keypass", "securepassword", "-dname",
                     "CN=$HOSTNAME, L=Brno, S=South Moravia, C=CZ", "-keyalg", "RSA", "-storetype",
                     "pkcs12"], "Failed to generate a server keystore...")

    logger.debug("Exporting the server certificate from the server keystore.")
    call("sudo keytool -export -keystore {0}/artemis-server.ks -file {0}/artemis-server.crt "
         "-storepass securepassword".format(certs_dir),
         "Failed to export the server certificate from the server keystore...")

    logger.debug("Importing the server certificate to a client trust store.")
    call("sudo keytool -import -keystore {0}/artemis-client.ts -file {0}/artemis-server.crt "
         "-storepass securepassword -keypass securepassword -noprompt".format(certs_dir),
         "Failed to import the server certificate to a client truststore...")

    logger.debug("Generating a client keystore.")
    call_with_array(["sudo", "keytool", "-genkey", "-keystore", "%s/artemis-client.ks" % certs_dir,
                     "-storepass", "securepassword", "-keypass", "securepassword", "-dname",
                     "CN=$HOSTNAME, L=Brno, S=South Moravia, C=CZ", "-keyalg", "RSA", "-storetype",
                     "pkcs12"], "Failed to generate a client keystore...")

    logger.debug("Exporting the client certificate from the client keystore.")
    call("sudo keytool -export -keystore {0}/artemis-client.ks -file {0}/artemis-client.crt "
         "-storepass securepassword".format(certs_dir),
         "Failed to export the client certificate from the client keystore...")

    logger.debug("Importing the client certificate to a server trust store.")
    call("sudo keytool -import -keystore {0}/artemis-server.ts -file {0}/artemis-client.crt "
         "-storepass securepassword -keypass securepassword -noprompt".format(certs_dir),
         "Failed to import the client certificate to a server truststore...")


def modify_broker_xml(broker_xml_path):
    logger.info("Updating broker configuration...")
    broker_data_dir = "./data"

    with open_xml(broker_xml_path) as doc:
        ctx = doc.xpathNewContext()
        ctx.xpathRegisterNs("activemq", "urn:activemq")
        ctx.xpathRegisterNs("core", "urn:activemq:core")

        # Update the acceptor configuration
        acceptor_nodes = ctx.xpathEval("//activemq:configuration/core:core/core:acceptors/core:acceptor")
        acceptor_nodes[0].setProp("name", "netty")

        certs_dir = os.path.join(BROKER_ROOT, BROKER_NAME, "certs")
        connector_string = ("tcp://localhost:61613?sslEnabled=true;"
                            "keyStorePath={0}/artemis-server.ks;"
                            "keyStorePassword=securepassword;"
                            "needClientAuth=true;"
                            "trustStorePath={0}/artemis-server.ts;"
                            "trustStorePassword=securepassword".format(certs_dir))
        acceptor_nodes[0].setContent(connector_string)

        # Update the data store locations
        bindings = ctx.xpathEval("//activemq:configuration/core:core/core:bindings-directory")[0]
        bindings.setContent("%s/bindings" % broker_data_dir)

        journal = ctx.xpathEval("//activemq:configuration/core:core/core:journal-directory")[0]
        journal.setContent("%s/journal" % broker_data_dir)

        large_msg = ctx.xpathEval("//activemq:configuration/core:core/core:large-messages-directory")[0]
        large_msg.setContent("%s/largemsgs" % broker_data_dir)

        paging = ctx.xpathEval("//activemq:configuration/core:core/core:paging-directory")[0]
        paging.setContent("%s/paging" % broker_data_dir)

        doc.saveFile(broker_xml_path)


def update_broker_config(broker_path, candlepin_broker_conf):
    # Move the generated conf file if it needs to be referenced later.
    broker_xml = os.path.join(broker_path, "etc/broker.xml")
    old_broker_xml = os.path.join(broker_path, "etc/broker.xml.old")
    if not os.path.exists(old_broker_xml):
        logger.debug("Backing up generated broker config file: %s -> %s" % (broker_xml, old_broker_xml))
        shutil.move(broker_xml, old_broker_xml)

    new_location = os.path.join(broker_path, "etc")
    logger.debug("Copying default candlepin config file into broker: %s -> %s" %
                 (candlepin_broker_conf, new_location))
    shutil.copy(candlepin_broker_conf, new_location)

    generate_certs(broker_path)

    # Update the Acceptor configuration.
    modify_broker_xml(broker_xml)


def cleanup(version, install_dir, broker_root):
    logger.info("Cleaning up artemis installation.")
    call("sudo systemctl stop artemis", "Failed to stop the artemis service.", True)
    uninstall_service()

    if os.path.exists(broker_root):
        logger.debug("Removing broker root: %s" % (broker_root))
        shutil.rmtree(broker_root)

    artemis_install_path = os.path.join(install_dir, "apache-artemis-%s" % (version))
    if os.path.exists(artemis_install_path):
        logger.debug("Removing artemis installation: %s" % (artemis_install_path))
        shutil.rmtree(artemis_install_path)


def uninstall_service():
    call("sudo semodule -vr artemisservice", "", allow_failure=True)
    if os.path.exists(SERVICE_FILE_TARGET_PATH):
        call("sudo systemctl disable artemis", "Failed to disable artemis service", allow_failure=True)
        os.remove(SERVICE_FILE_TARGET_PATH)
    call("sudo systemctl daemon-reload", "Failed to reload systemd daemons.")
    call("sudo systemctl reset-failed", "Failed to reset failed services.")

    if os.path.exists(SELINUX_BUILD_DIR):
        shutil.rmtree(SELINUX_BUILD_DIR)


def parse_options():
    usage = "usage: %prog"
    parser = OptionParser(usage=usage)
    parser.add_option("--version", action="store", type="string", default=DEFAULT_VERSION,
                      help="the version of artemis to install")
    parser.add_option("--broker-config", action="store", type="string",
                      default="%s/../../../src/main/resources/broker.xml" % (BASE_DIR),
                      help="the broker config file to use.")
    parser.add_option("--clean", action="store_true", help="clean current installation")
    parser.add_option("--start", action="store_true", help="start broker after install")
    parser.add_option("--debug", action="store_true", help="enables debug logging")

    return parser.parse_args()


def main():
    (options, args) = parse_options()

    if options.debug:
        logger.setLevel("DEBUG")

    if options.clean:
        cleanup(options.version, INSTALL_DIR, BROKER_ROOT)
        return

    logger.info("Installing Artemis...")
    artemis_path = install_artemis(options.version, INSTALL_DIR)
    broker_path = create_broker(artemis_path, BROKER_ROOT)
    install_artemis_service()
    update_broker_config(broker_path, options.broker_config)
    logger.info("Artemis was successfully installed!")

    if options.start:
        logger.info("Starting the Artemis server.")
        call("sudo systemctl restart artemis", "Unable to start the artemis service.")


if __name__ == "__main__":
    main()
