#! /usr/bin/env python

import abc
import libxml2
import os
import shutil
import logging

from optparse import OptionParser
from contextlib import contextmanager

logging.basicConfig(level=logging.INFO, format="%(levelname)-7s %(message)s")
logger = logging.getLogger('update_server_xml')

SSL_CIPHERS = [
    "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
    "TLS_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
]

def compare_nodes(cnode, nnode):
    """Compares the two nodes and checks for differences in attributes or children.
    Returns True if the nodes differ, False otherwise."""

    logger.debug("Comparing nodes: %s and %s" % (cnode.name, nnode.name))

    # Check if the nodes have different names...
    if cnode.name != nnode.name:
        logger.debug("Node names differ: %s != %s" % (cnode.name, nnode.name))
        return True

    # Compare attributes immediately present on both nodes...
    cattribs = {}
    nattribs = {}

    if cnode.properties:
        cattribs = { np.name: np.content for np in cnode.properties if np.type == "attribute" }

    if nnode.properties:
        nattribs = { np.name: np.content for np in nnode.properties if np.type == "attribute" }

    if cattribs != nattribs:
        logger.debug("Attributes differ: %s != %s" % (cattribs, nattribs))
        return True

    # Compare children...
    # Impl note: depending on order is "safe" here, since a different order implies something changed
    # either in what this config editor generates, or the end-user has modified the server.xml
    cchildren = []
    nchildren = []

    if cnode.children:
        for child in cnode.children:
            cchildren.append(child)

    if nnode.children:
        for child in nnode.children:
            nchildren.append(child)

    if len(cchildren) != len(nchildren):
        return True

    for i in range(0, len(cchildren)):
        if compare_nodes(cchildren[i], nchildren[i]):
            logger.debug("Children differ: %s != %s" % (cchildren[i].name, nchildren[i].name))
            return True

    # The things we care about line up, no difference
    logger.debug("Nodes are the same")
    return False


@contextmanager
def open_xml(filename):
    """libxml2 does not handle cleaning up memory automatically. This
    context manager will take care of XML documents."""
    doc = libxml2.parseFile(filename)
    yield doc
    doc.freeDoc()


class AbstractBaseEditor(object):
    __metaclass__ = abc.ABCMeta

    def __init__(self, doc):
        self.doc = doc

    @abc.abstractproperty
    def parent_xpath(self):
        """The XPath expression to find the parent of the element you wish to edit.
        This is necessary because we need to check if the element to edit already exists
        under the parent."""
        pass

    @abc.abstractproperty
    def search_xpath(self):
        """The XPath expression to find the element you wish to edit.
        Should be relative to parent_xpath."""
        pass

    @abc.abstractproperty
    def new_node(self):
        """A new node to operate on.  Be careful not to create a new node every
        time this function is called.  Memoize the object or create it in __init__."""
        pass

    @abc.abstractproperty
    def attributes(self):
        pass

    def _add_attributes(self, node, attributes):
        # attributes is a list of 2-tuples formated like (attribute, value)
        for k, v in attributes:
            logger.debug("Setting %s to %s on %s" % (k, v, node.name))
            node.setProp(k, v)

    def _is_different(self, node):
        return compare_nodes(node, self.new_node)

    def _update(self, existing_nodes):
        different_nodes = filter(self._is_different, existing_nodes)

        for match in different_nodes:
            logger.info("Editing node \"%s\" on line %s" % (match.name, match.lineNo()))
            match.addPrevSibling(self.new_node)
            match.unlinkNode()
            match.freeNode()

    def _create(self, parent):
        logger.info("Creating node \"%s\" under \"%s\" on line %s" % (self.new_node.name, parent.name, parent.lineNo()))
        logger.debug("New node: %s" % (self.new_node))

        first_child = parent.firstElementChild()
        if first_child:
            # Insert the new node at the top so the output doesn't look like rubbish
            first_child.addPrevSibling(self.new_node)
            # Add a new line at the end of the new element
            first_child.addPrevSibling(libxml2.newText("\n\n"))
        else:
            parent.addChild(self.new_node)
            parent.addChild(libxml2.newText("\n\n"))

    def _delete(self, existing_nodes, parent):
        for match in existing_nodes:
            logger.info("Deleting node \"%s\" on line %s" % (match.name, match.lineNo()))
            comment_node = libxml2.newComment(" Removed by update-server-xml.py: %s " % str(match))
            match.addNextSibling(comment_node)
            match.unlinkNode()
            match.freeNode()

    def insert(self):
        insertion_points = self.doc.xpathEval(self.parent_xpath)
        for parent in insertion_points:
            existing_nodes = parent.xpathEval(self.search_xpath)
            logger.debug("Found %d nodes matching %s under \"%s\"" % (len(existing_nodes), self.search_xpath, self.parent_xpath))
            if existing_nodes:
                self._update(existing_nodes)
            else:
                self._create(parent)

    def remove(self):
        removal_points = self.doc.xpathEval(self.parent_xpath)
        for parent in removal_points:
            existing_nodes = parent.xpathEval(self.search_xpath)
            logger.debug("Found %d nodes matching %s under \"%s\"" % (len(existing_nodes), self.search_xpath, self.parent_xpath))
            if existing_nodes:
                self._delete(existing_nodes, parent)


class LegacySSLContextEditor(AbstractBaseEditor):
    def __init__(self, *args, **kwargs):
        super(LegacySSLContextEditor, self).__init__(*args, **kwargs)
        self.port = "8443"
        self._element = self._build_node()

    @property
    def parent_xpath(self):
        return "/Server/Service"

    @property
    def search_xpath(self):
        return "./Connector[@port='%s']" % self.port

    @property
    def new_node(self):
        return self._element

    @property
    def attributes(self):
        # We manually add the attributes below, so we don't want to return anything here.
        return []

    def _build_node(self):
        # Setup our node configuration
        connector = libxml2.newNode("Connector")
        self._add_attributes(connector, [
            ("port", self.port),
            ("protocol", "HTTP/1.1"),
            ("SSLEnabled", "true"),
            ("maxThreads", "150"),
            ("scheme", "https"),
            ("secure", "true"),
            ("clientAuth", "want"),
            # Note SSLv3 is not included, to avoid poodle
            # For the time being, TLSv1 needs to stay enabled in Satellite deployments to support
            # existing python-rhsm based clients (RHEL5).
            ("sslEnabledProtocols", "TLSv1.2,TLSv1.1,TLSv1"),
            ("SSLProtocol", "TLSv1.2,TLSv1.1,TLSv1"),
            ("ciphers", ",".join(SSL_CIPHERS)),
            ("truststoreFile", "conf/keystore"),
            ("truststorePass", "password"),
            ("keystoreFile", "conf/keystore"),
            ("keystorePass", "password"),
            ("keystoreType", "PKCS12"),
        ])

        # Return our top-level node
        return connector



class CandlepinConnectorEditorV3(AbstractBaseEditor):
    def __init__(self, *args, **kwargs):
        super(CandlepinConnectorEditorV3, self).__init__(*args, **kwargs)

        self.port = "8443"
        self._node = self._build_node()

    @property
    def parent_xpath(self):
        return "/Server/Service"

    @property
    def search_xpath(self):
        return './Connector[@port=\"{port}\"]'.format(port = self.port)

    @property
    def new_node(self):
        return self._node

    @property
    def attributes(self):
        return [
            ('port', self.port),
            ('protocol', 'HTTP/1.1'),
            ('scheme', 'https'),
            ('secure', 'true'),
            ('SSLEnabled', 'true'),
            ('maxThreads', '150')
        ]

    def _build_node(self):
        # <Connector port="8443" protocol="HTTP/1.1"
        #     scheme="https"
        #     secure="true"
        #     SSLEnabled="true"
        #     maxThreads="150">
        #
        #     <SSLHostConfig certificateVerification="optional"
        #         protocols="+TLSv1,+TLSv1.1,+TLSv1.2"
        #         sslProtocol="TLS"
        #         ciphers="SSL_RSA_WITH_3DES_EDE_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA,TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA">
        #
        #         <Certificate
        #                certificateFile="/etc/candlepin/certs/candlepin-ca.crt"
        #                certificateKeyFile="/etc/candlepin/certs/candlepin-ca.key"
        #                type="RSA"
        #         />
        #     </SSLHostConfig>
        # </Connector>

        connector = libxml2.newNode("Connector")
        self._add_attributes(connector, self.attributes)

        ssl_host_config = libxml2.newNode("SSLHostConfig")
        self._add_attributes(ssl_host_config, [
            ("certificateVerification", "optional"),
            # Note SSLv3 is not included, to avoid poodle
            # For the time being, TLSv1 needs to stay enabled in Satellite deployments to support
            # existing python-rhsm based clients (RHEL5).
            ("protocols", "+TLSv1,+TLSv1.1,+TLSv1.2"),
            ("sslProtocol", "TLS"),
            ("ciphers", ",".join(SSL_CIPHERS))
        ])

        certificate = libxml2.newNode("Certificate")
        self._add_attributes(certificate, [
            ('certificateFile', '/etc/candlepin/certs/candlepin-ca.crt'),
            ('certificateKeyFile', '/etc/candlepin/certs/candlepin-ca.key'),
            ('type', 'RSA')
        ])

        # Put it all together
        # The libxml2 bindings don't provide an obvious way to output indented XML, so we fake
        # it here to make it mostly human-readable.
        connector.addChild(libxml2.newText("\n  "))
        connector.addChild(ssl_host_config)
        connector.addChild(libxml2.newText("\n"))
        ssl_host_config.addChild(libxml2.newText("\n    "))
        ssl_host_config.addChild(certificate)
        ssl_host_config.addChild(libxml2.newText("\n  "))

        # return
        return connector



class AccessLogValveEditor(AbstractBaseEditor):
    def __init__(self, *args, **kwargs):
        super(AccessLogValveEditor, self).__init__(*args, **kwargs)
        self.access_valve_class = "org.apache.catalina.valves.AccessLogValve"
        self._element = libxml2.newNode("Valve")
        self._add_attributes(self._element, self.attributes)

    @property
    def parent_xpath(self):
        return "/Server/Service/Engine/Host"

    @property
    def search_xpath(self):
        return "./Valve[@className='%s']" % self.access_valve_class

    @property
    def new_node(self):
        return self._element

    @property
    def attributes(self):
        return [
            ("className", self.access_valve_class),
            ("directory", "/var/log/candlepin/"),
            ("prefix", "access"),
            ("rotatable", "false"),
            ("checkExists", "true"),
            ("suffix", ".log"),
            ("pattern", '%h %l %u %t "%r" %s %b "" "%{user-agent}i sm/%{x-subscription-manager-version}i" "req_time=%T,req=%{requestUuid}r"'),
            ("resolveHosts", "false"),
        ]


class AprListenerDeleter(AbstractBaseEditor):
    """The AprLifecycleListener attempts to load the Apache Portable Runtime (APR).  The
    APR is a native library used to speed up certain operations.  In our case, loading the
    APR causes an issue because the APR requires additional attributes in the Connector element
    (e.g. SSLCertificateFile) since the APR uses OpenSSL and PEM files instead of Java and JKS
    files.  See https://tomcat.apache.org/tomcat-8.0-doc/apr.html

    In Fedora, the tomcat-native package installs the APR and the tomcat package pulls in
    tomcat-native as a suggested dependency.  Rather than break developers every time they update
    Tomcat, the easier approach is just to disable the AprLifecycleListener."""

    def __init__(self, *args, **kwargs):
        super(AprListenerDeleter, self).__init__(*args, **kwargs)
        self.listener_class = "org.apache.catalina.core.AprLifecycleListener"

    @property
    def parent_xpath(self):
        return "/Server"

    @property
    def search_xpath(self):
        return "./Listener[@className='%s']" % self.listener_class

    @property
    def new_node(self):
        raise NotImplementedError

    @property
    def attributes(self):
        raise NotImplementedError


def parse_options():
    usage = "usage: %prog TOMCAT_CONF_DIRECTORY"
    parser = OptionParser(usage=usage)
    parser.add_option("--stdout", action="store_true", default=False,
            help="print results to stdout instead of writing to files.")
    parser.add_option("--debug", action="store_true", default=False,
            help="print debug output")
    parser.add_option("--tomcat-version", action="store", default=None, type=str, dest="tc_version",
            help="specify a Tomcat version to target")

    (options, args) = parser.parse_args()
    if len(args) != 1:
        parser.error("You must provide a Tomcat configuration directory")

    return (options, args)

def parse_tc_version(version):
    if not version:
        return None

    try:
        return [int(vchunk) for vchunk in version.split(".")]
    except ValueError:
        return None

def make_backup_config(conf_dir):
    logger.info("Backing up current server.xml")
    shutil.copy(os.path.join(conf_dir, "server.xml"), os.path.join(conf_dir, "server.xml.original"))


def main():
    (options, args) = parse_options()
    if options.debug:
        logger.setLevel(logging.DEBUG)

    conf_dir = args[0]

    logger.info("Updating server.xml for Tomcat v{tc_version} found at: {tc_home}"
        .format(tc_version = options.tc_version, tc_home = conf_dir))

    make_backup_config(conf_dir)

    # Determine which SSLContextEditor we need...
    tversion = parse_tc_version(options.tc_version)
    if not tversion or len(tversion) < 1 or tversion[0] > 8 or (tversion[0] == 8 and tversion[1] >= 5):
        ssl_editor_target = CandlepinConnectorEditorV3
    else:
        logger.warn("Using legacy Tomcat configuration")
        ssl_editor_target = LegacySSLContextEditor

    xml_file = os.path.join(conf_dir, "server.xml")
    logger.debug("Opening %s" % xml_file)
    with open_xml(xml_file) as doc:
        ssl_editor_target(doc).insert()
        AccessLogValveEditor(doc).insert()
        AprListenerDeleter(doc).remove()

        if options.stdout:
            print(doc.serialize())
        else:
            doc.saveFile(xml_file)


if __name__ == "__main__":
    main()
