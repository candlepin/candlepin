/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.jpa;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parser for persistence.xml files. Reads and parses the persistence configuration file, providing access to
 * individual persistence units and their configurations.
 */
public class PersistenceXmlParser {

    private static final String DEFAULT_RESOURCE_NAME = "META-INF/persistence.xml";
    private static final XmlMapper XML_MAPPER = new XmlMapper();

    private PersistenceXmlParser() {
        // Intentionally left blank
    }

    /**
     * Parses the default persistence.xml file from the classpath.
     *
     * @throws IOException
     *  if the persistence.xml file cannot be read or parsed
     *
     * @return the parsed persistence xml object
     */
    public static PersistenceXml parse() throws IOException {
        return parse(DEFAULT_RESOURCE_NAME);
    }

    /**
     * Reads and parses the provided persistence.xml file from the classpath.
     *
     * @param resourceName
     *  the name of the resource to load from the classpath
     *
     * @throws IOException
     *  if the persistence.xml file cannot be read or parsed
     *
     * @return the parsed persistence xml object
     */
    public static PersistenceXml parse(String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException(String.format("Could not find %s on the classpath", resourceName));
            }

            return XML_MAPPER.readValue(inputStream, PersistenceXml.class);
        }
    }
}

