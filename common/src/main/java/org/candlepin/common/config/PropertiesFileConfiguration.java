/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.common.config;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration implementation that reads from a Java Properties object.  If
 * save functionality is ever added, we will likely need a ReentrantReadWriteLock
 * to prevent concurrent reads and writes.
 */
public class PropertiesFileConfiguration extends AbstractConfiguration
    implements FileConfiguration {

    protected Charset encoding;

    private MapConfiguration backingMap = new MapConfiguration();

    public PropertiesFileConfiguration() {
    }

    public PropertiesFileConfiguration(String fileName) throws ConfigurationException {
        this(fileName, Charset.defaultCharset());
    }

    public PropertiesFileConfiguration(String fileName, Charset encoding)
        throws ConfigurationException {
        setEncoding(encoding);
        load(fileName);
    }

    public PropertiesFileConfiguration(File file) throws ConfigurationException {
        this(file, Charset.defaultCharset());
    }

    public PropertiesFileConfiguration(File file, Charset encoding)
        throws ConfigurationException {
        setEncoding(encoding);
        load(file);
    }

    public PropertiesFileConfiguration(InputStream inStream) throws ConfigurationException {
        this(inStream, Charset.defaultCharset());
    }

    public PropertiesFileConfiguration(InputStream inStream, Charset encoding)
        throws ConfigurationException {
        setEncoding(encoding);
        load(inStream);
    }

    public PropertiesFileConfiguration(Properties properties) {
        load(properties);
    }

    @Override
    public Configuration subset(String prefix) {
        return backingMap.subset(prefix);
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public boolean containsKey(String key) {
        return backingMap.containsKey(key);
    }

    @Override
    public void setProperty(String key, String value) {
        backingMap.setProperty(key, value);
    }

    @Override
    public void clear() {
        backingMap.clear();
    }

    @Override
    public void clearProperty(String key) {
        backingMap.clearProperty(key);
    }

    @Override
    public Iterable<String> getKeys() {
        return backingMap.getKeys();
    }

    @Override
    public String getProperty(String key) {
        return backingMap.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return backingMap.getProperty(key, defaultValue);
    }

    @Override
    public Charset getEncoding() {
        return encoding;
    }

    @Override
    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public static PropertiesFileConfiguration merge(Configuration ... configs) {
        PropertiesFileConfiguration mergedConfig = new PropertiesFileConfiguration();

        for (Configuration c : configs) {
            for (String key : c.getKeys()) {
                if (!mergedConfig.containsKey(key)) {
                    mergedConfig.setProperty(key, c.getProperty(key));
                }
            }
        }

        return mergedConfig;
    }

    @Override
    public void load(String fileName) throws ConfigurationException {
        load(new File(fileName));
    }

    @Override
    public void load(File file) throws ConfigurationException {
        try {
            load(new BufferedInputStream(new FileInputStream(file)));
        }
        catch (FileNotFoundException e) {
            throw new ConfigurationException(e);
        }
    }

    @Override
    public void load(InputStream inStream) throws ConfigurationException {
        load(new BufferedReader(new InputStreamReader(inStream, getEncoding())));
    }

    @Override
    public void load(Reader reader) throws ConfigurationException {
        try {
            Properties p = new Properties();
            p.load(reader);
            load(p);
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }

    /**
     * Calling this method directly is primarily meant for testing purposes.
     * @param properties
     */
    public void load(Properties properties) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            backingMap.setProperty((String) entry.getKey(), (String) entry.getValue());
        }
    }

    public String toString() {
        return backingMap.toString();
    }

    @Override
    public Map<String, String> getNamespaceMap(String prefix) {
        return backingMap.getNamespaceMap(prefix);
    }

    @Override
    public Map<String, String> getNamespaceMap(String prefix,
            Map<String, String> defaults) {
        return backingMap.getNamespaceMap(prefix, defaults);
    }

    @Override
    public Properties getNamespaceProperties(String prefix) {
        return backingMap.getNamespaceProperties(prefix);
    }

    @Override
    public Properties getNamespaceProperties(String prefix,
            Map<String, String> defaults) {
        return backingMap.getNamespaceProperties(prefix, defaults);
    }
}
