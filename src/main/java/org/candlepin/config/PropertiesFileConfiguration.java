/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.config;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;



/**
 * Configuration implementation that reads from a Java Properties object.  If
 * save functionality is ever added, we will likely need a ReentrantReadWriteLock
 * to prevent concurrent reads and writes.
 */
public class PropertiesFileConfiguration extends AbstractConfiguration
    implements FileConfiguration {

    /** Default character set to use when none is provided */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    // TODO: Flatten this. We don't utilize the value we get out of having a separate configuration
    // instance backing this facade.
    private MapConfiguration backingMap = new MapConfiguration();

    public PropertiesFileConfiguration() {
        // Intentionally left empty
    }

    @Override
    public Configuration subset(String prefix) {
        return backingMap.subset(prefix);
    }

    @Override
    public Configuration strippedSubset(String prefix) {
        return backingMap.strippedSubset(prefix);
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

    /**
     * Merge configuration objects.  Any collisions on keys will use the value
     * from the leftmost argument.
     *
     * @param configs
     * @return the merged configuration
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Charset getDefaultCharset() {
        return DEFAULT_CHARSET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(String filename) throws ConfigurationException {
        this.load(filename, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(String filename, Charset encoding) throws ConfigurationException {
        if (filename == null) {
            throw new IllegalArgumentException("filename is null");
        }

        this.load(new File(filename), encoding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(File file) throws ConfigurationException {
        this.load(file, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(File file, Charset encoding) throws ConfigurationException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        try {
            this.load(new BufferedInputStream(new FileInputStream(file)), encoding);
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(InputStream istream) throws ConfigurationException {
        this.load(istream, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(InputStream istream, Charset encoding) throws ConfigurationException {
        if (istream == null) {
            throw new IllegalArgumentException("input stream is null");
        }

        this.load(new InputStreamReader(istream, encoding != null ? encoding : DEFAULT_CHARSET));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(Reader reader) throws ConfigurationException {
        if (reader == null) {
            throw new IllegalArgumentException("reader is null");
        }

        try {
            Properties properties = new Properties();
            properties.load(reader);

            this.backingMap.clear();
            properties.forEach((key, value) -> this.backingMap.setProperty((String) key, (String) value));
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return backingMap.toString();
    }
}
