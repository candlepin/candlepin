/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Enumeration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.naming.ConfigurationException;

public class PropertiesConfiguration{
    private static final Logger log = LoggerFactory.getLogger(PropertiesConfiguration.class);
    private Map<String, String> configMap = new ConcurrentHashMap<>();
    protected static final String ERROR_MESSAGE = "\"%s\" doesn't map to an existing object";
    protected static final String MISSING_MESSAGE = "\"%s\" not present in configuration";
    protected Charset encoding;

    /**
     * @param providers Providers ordered by priority from low to high
     */
    public PropertiesConfiguration(Supplier<Properties>... providers) {
        for (Supplier<Properties> provider : providers) {
            putAll(provider.get());
        }
    }

    /**
     * @param properties Properties ordered by priority from low to high
     */
    public PropertiesConfiguration(Properties... properties) {
        for (Properties prop : properties) {
            putAll(prop);
        }
    }

    public PropertiesConfiguration(String fileName) throws ConfigurationException {
        this(fileName, Charset.defaultCharset());
    }

    public PropertiesConfiguration(String fileName, Charset encoding) throws ConfigurationException {
        setEncoding(encoding);
        load(fileName);
    }

    public PropertiesConfiguration(File file) throws ConfigurationException {
        this(file, Charset.defaultCharset());
    }

    public PropertiesConfiguration(File file, Charset encoding) throws ConfigurationException {
        setEncoding(encoding);
        load(file);
    }

    public PropertiesConfiguration(InputStream inStream) throws ConfigurationException {
        this(inStream, Charset.defaultCharset());
    }

    public PropertiesConfiguration(InputStream inStream, Charset encoding) throws ConfigurationException {
        setEncoding(encoding);
        load(inStream);
    }

    public void putAll(Properties properties) {
        Enumeration<?> names = properties.propertyNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            this.setProperty(name, properties.getProperty(name));
        }
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }

    public boolean isEmpty() {
        return configMap.isEmpty();
    }

    public boolean containsKey(String key) {
        return (null == key) ? false : configMap.containsKey(key);
    }

    public void setProperty(String key, String value) {
        configMap.put(key, value);
    }

    public void clear() {
        configMap.clear();
    }

    public void clearProperty(String key) {
        configMap.remove(key);
    }

    public Iterable<String> getKeys() {
        return configMap.keySet();
    }

    public String getProperty(String key) {
        if (containsKey(key)) {
            return configMap.get(key);
        }
        else {
            missingMessage(key);
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    public String getProperty(String key, String defaultValue) {
        return (containsKey(key)) ? configMap.get(key) : defaultValue;
    }

    protected String doesNotMapMessage(String key) {
        return String.format(ERROR_MESSAGE, key);
    }

    protected String missingMessage(String key) {
        return String.format(MISSING_MESSAGE, key);
    }

    public Charset getEncoding() {
        return encoding;
    }

    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    public void load(String fileName) throws ConfigurationException {
        load(new File(fileName));
    }

    public void load(File file) throws ConfigurationException {
        try {
            load(new BufferedInputStream(new FileInputStream(file)));
        }
        catch (FileNotFoundException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    public void load(InputStream inStream) throws ConfigurationException {
        load(new BufferedReader(new InputStreamReader(inStream, getEncoding())));
    }

    public void load(Reader reader) throws ConfigurationException {
        try {
            Properties p = new Properties();
            p.load(reader);
            putAll(p);
        }
        catch (IOException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }
}
