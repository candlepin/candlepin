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
package org.fedoraproject.candlepin.configuration;

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

public class JPAConfiguration {
    final public static String JPA_CONFIG_PREFIX = "jpa.config";
    final public static int PREFIX_LENGTH = JPA_CONFIG_PREFIX.length();
    
    final public static String URL_CONFIG = "hibernate.connection.url";
    final public static String USER_CONFIG = "hibernate.connection.username";
    final public static String PASSWORD_CONFIG = "hibernate.connection.password";
    
    
    public Properties parseConfig(Map<String, String> inputConfiguration) {
        
        Properties toReturn = new Properties(defaultConfigurationSettings());
        toReturn.putAll(stripPrefixFromConfigKeys(inputConfiguration));
        return toReturn;
    }

    public Properties stripPrefixFromConfigKeys(Map<String, String> inputConfiguration) {
        Properties toReturn = new Properties();
        
        for (String key : inputConfiguration.keySet()) {
            toReturn.put(key.substring(PREFIX_LENGTH + 1), inputConfiguration.get(key));
        }
        return toReturn;
    }
    
    public Properties defaultConfigurationSettings() {
        try {
            return loadDefaultConfigurationSettings("production", "persistence.xml");
        } catch (Exception e) {
            throw new RuntimeException("exception when loading setting from persistence.xml", e);
        }
    }

    public Properties loadDefaultConfigurationSettings(String persistenceUnit, String configFile) 
            throws XPathExpressionException, IOException, ParserConfigurationException, SAXException {
        return parsePropertiesFromConfigFile(persistenceUnit, parseXML(configFile));
    }
    
    public Document parseXML(String fileName) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(false); 
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(getClass().getResourceAsStream(fileName));
        return doc;
    }
    
    public Properties parsePropertiesFromConfigFile(String persistenceUnitName, Document doc) 
            throws XPathExpressionException {
        
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expr = xpath.compile("//persistence-unit[@name='" + persistenceUnitName + "']/properties/property");

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
