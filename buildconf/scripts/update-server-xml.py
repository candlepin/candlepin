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
    def insertion_xpath(self):
        """The XPath expression to find the parent of the element you wish to edit.
        This is necessary because we need to check if the element to edit already exists
        under the parent."""
        pass

    @abc.abstractproperty
    def search_xpath(self):
        """The XPath expression to find the element you wish to edit.
        Should be relative to insertion_xpath."""
        pass

    @abc.abstractproperty
    def element(self):
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
        current_attributes = {}
        for property in node.properties:
            if property.type == "attribute":
                current_attributes[property.name] = property.content
        return node.name != self.element or current_attributes != dict(self.attributes)

    def _update(self, existing_nodes):
        different_nodes = filter(self._is_different, existing_nodes)
        for match in different_nodes:
            logger.info("Editing %s on line %s" % (match.name, match.lineNo()))
            for property in match.properties:
                if property.type == "attribute":
                    property.unlinkNode()
                    property.freeNode()

            self._add_attributes(match, self.attributes)

    def _create(self, parent):
        logger.info("Creating %s under %s on line %s" % (self.element, parent.name, parent.lineNo()))
        new_element = libxml2.newNode(self.element)
        self._add_attributes(new_element, self.attributes)
        first_child = parent.firstElementChild()
        if first_child:
            # Insert the new node at the top so the output doesn't look like rubbish
            first_child.addPrevSibling(new_element)
            # Add a new line at the end of the new element
            first_child.addPrevSibling(libxml2.newText("\n\n"))
        else:
            parent.addChild(new_element)
            parent.addChild(libxml2.newText("\n\n"))

    def insert(self):
        insertion_points = self.doc.xpathEval(self.insertion_xpath)
        for parent in insertion_points:
            existing_nodes = parent.xpathEval(self.search_xpath)
            logger.debug("Found %d nodes matching %s under %s" %
                    (len(existing_nodes), self.search_xpath, self.insertion_xpath))
            if existing_nodes:
                self._update(existing_nodes)
            else:
                self._create(parent)


class SslContextEditor(AbstractBaseEditor):
    def __init__(self, *args, **kwargs):
        super(SslContextEditor, self).__init__(*args, **kwargs)
        self.port = "8443"

    @property
    def insertion_xpath(self):
        return "/Server/Service"

    @property
    def search_xpath(self):
        return "./Connector[@port='%s']" % self.port

    @property
    def element(self):
        return "Connector"

    @property
    def attributes(self):
        ciphers = ",".join([
            "SSL_RSA_WITH_3DES_EDE_CBC_SHA,TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        ])

        # This is a list of tuples instead of a dict so we can preserve the attribute
        # ordering.  OrderedDict didn't get added until 2.7.
        return [
            ("port", self.port),
            ("protocol", "HTTP/1.1"),
            ("SSLEnabled", "true"),
            ("maxThreads", "150"),
            ("scheme", "https"),
            ("secure", "true"),
            ("clientAuth", "want"),
            ("SSLProtocol", "TLS"),
            ("keystoreFile", "conf/keystore"),
            ("truststoreFile", "conf/keystore"),
            ("keystorePass", "password"),
            ("keystoreType", "PKCS12"),
            ("ciphers", ciphers),
            ("truststorePass", "password"),
        ]


class AccessValveEditor(AbstractBaseEditor):
    def __init__(self, *args, **kwargs):
        super(AccessValveEditor, self).__init__(*args, **kwargs)
        self.access_valve_class = "org.apache.catalina.valves.AccessLogValve"

    @property
    def insertion_xpath(self):
        return "/Server/Service/Engine/Host"

    @property
    def search_xpath(self):
        return "./Valve[@className='%s']" % self.access_valve_class

    @property
    def element(self):
        return "Valve"

    @property
    def attributes(self):
        return [
            ("className", self.access_valve_class),
            ("directory", "/var/log/candlepin/"),
            ("prefix", "access"),
            ("rotatable", "false"),
            ("suffix", ".log"),
            ("pattern", '%h %l %u %t "%r" %s %b "" "%{user-agent}i sm/%{x-subscription-manager-version}i" "req_time=%T,req=%{requestUuid}r"'),
            ("resolveHosts", "false"),
        ]


def parse_options():
    usage = "usage: %prog TOMCAT_CONF_DIRECTORY"
    parser = OptionParser(usage=usage)
    parser.add_option("--stdout", action="store_true", default=False,
            help="print results to stdout instead of writing to files.")
    parser.add_option("--debug", action="store_true", default=False,
            help="print debug output")

    (options, args) = parser.parse_args()
    if len(args) != 1:
        parser.error("You must provide a Tomcat configuration directory")
    return (options, args)


def make_backup_config(conf_dir):
    logger.info("Backing up current server.xml")
    shutil.copy(os.path.join(conf_dir, "server.xml"), os.path.join(conf_dir, "server.xml.original"))


def main():
    (options, args) = parse_options()
    if options.debug:
        logger.setLevel(logging.DEBUG)
    conf_dir = args[0]
    make_backup_config(conf_dir)
    xml_file = os.path.join(conf_dir, "server.xml")
    logger.debug("Opening %s" % xml_file)
    with open_xml(xml_file) as doc:
        SslContextEditor(doc).insert()
        AccessValveEditor(doc).insert()

        if options.stdout:
            print doc.serialize()
        else:
            doc.saveFile(xml_file)


if __name__ == "__main__":
    main()
