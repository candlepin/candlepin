/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.config;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * JPAConfiguration
 * @version $Rev$
 */
public class JPAConfiguration {
    /** JPA configuration prefix */
    public static final String JPA_CONFIG_PREFIX = "jpa.config";
    /** Length of the <code>JPA_CONFIG_PREFIX</code> */
    public static final int PREFIX_LENGTH = JPA_CONFIG_PREFIX.length();
    
    /** hibernate connection url */
    public static final String URL_CONFIG = "hibernate.connection.url";
    /** Comment for <code>USER_CONFIG</code> */
    public static final String USER_CONFIG = "hibernate.connection.username";
    /** Comment for <code>PASSWORD_CONFIG</code> */
    public static final String PASSWORD_CONFIG = "hibernate.connection.password";
    
    
    /**
     * Converts the given Map into a Properties object. 
     * @param inputConfiguration Configuration to be converted.
     * @return config as a Properties file
     */
    public Properties parseConfig(Map<String, String> inputConfiguration) {
        
        Properties toReturn = new Properties(defaultConfigurationSettings());
        toReturn.putAll(stripPrefixFromConfigKeys(inputConfiguration));
        return toReturn;
    }

    /**
     * Return a copy of the input without the prefixes.
     * @param inputConfiguration Configuration to be converted.
     * @return config as a Properties object without the prefixes.
     */
    public Properties stripPrefixFromConfigKeys(Map<String, String> inputConfiguration) {
        Properties toReturn = new Properties();
        
        for (String key : inputConfiguration.keySet()) {
            toReturn.put(key.substring(PREFIX_LENGTH + 1), inputConfiguration.get(key));
        }
        return toReturn;
    }
    
    /**
     * @return the default jpa configuration
     */
    public Properties defaultConfigurationSettings() {
        try {
            return loadDefaultConfigurationSettings(
                "production",
                new File(getClass().getResource("persistence.xml").toURI()));
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "exception when loading setting from persistence.xml", e);
        }
    }

    /**
     * loads the default configuration from the file.
     * @param persistenceUnit JPA persistence unit name.
     * @param configFile location of the configuration file.
     * @return jpa configuration as a JPA for the given unit name.
     * @throws XPathExpressionException thrown for invalid xml file
     * @throws IOException thrown if file is not found.
     * @throws ParserConfigurationException thrown for invalid xml file
     * @throws SAXException thrown for invalid xml file
     */
    public Properties loadDefaultConfigurationSettings(
            String persistenceUnit, File configFile) 
        throws XPathExpressionException, IOException,
            ParserConfigurationException, SAXException {
        return parsePropertiesFromConfigFile(persistenceUnit, parseXML(configFile));
    }
    
    /**
     * parses the XML file.
     * @param file file to parse
     * @return returns XML Document for the given file.
     * @throws IOException thrown if there's a problem reading a file.
     * @throws ParserConfigurationException thrown for invalid xml file.
     * @throws SAXException thrown for invalid xml file.
     */
    public Document parseXML(File file)
        throws IOException, ParserConfigurationException, SAXException {

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false); 
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(file);
        return doc;
    }
    
    /**
     * @param persistenceUnitName jpa persistence unit name
     * @param doc XML Document
     * @return configuration as a Properties.
     * @throws XPathExpressionException thrown for invalid xml file.
     */
    public Properties parsePropertiesFromConfigFile(
                String persistenceUnitName, Document doc) 
        throws XPathExpressionException {
        
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile(
                "//persistence-unit[@name='" + persistenceUnitName +
                "']/properties/property");

        Object result = expr.evaluate(doc, XPathConstants.NODESET);
        NodeList nodes = (NodeList) result;
        Properties toReturn = new Properties();
        for (int i = 0; i < nodes.getLength(); i++) {
            String name = nodeValue(nodes, i, "name");
            String value = nodeValue(nodes, i, "value");
            toReturn.put(name, value);
        }
        
        return toReturn;
    }

    private String nodeValue(NodeList nodes, int i, String name) {
        return nodes.item(i).getAttributes().getNamedItem(name).getNodeValue();
    }
}
