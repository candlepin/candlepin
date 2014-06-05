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
package org.candlepin.gutterball.configuration;

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
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration implementation that reads from a Java Properties object.  If
 * save functionality is ever added, we will likely need a ReentrantReadWriteLock
 * to prevent concurrent reads and writes.
 */
public class PropertiesFileConfiguration extends AbstractConfiguration
    implements FileConfiguration {

    protected String fileName;
    protected File file;
    protected InputStream inStream;

    protected Charset encoding;

    private ConcurrentHashMap<String, Object> configMap = new ConcurrentHashMap<String, Object>();

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

    @Override
    public Configuration subset(String prefix) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isEmpty() {
        return configMap.isEmpty();
    }

    @Override
    public boolean containsKey(String key) {
        return configMap.containsKey(key);
    }

    @Override
    public void addProperty(String key, Object value) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setProperty(String key, Object value) {
        clearProperty(key);
        addProperty(key, value);
    }

    @Override
    public void clear() {
        configMap.clear();
    }

    @Override
    public void clearProperty(String key) {
        configMap.remove(key);
    }

    @Override
    public Iterable<String> getKeys() {
        return configMap.keySet();
    }

    @Override
    public Object getProperty(String key) {
        if (containsKey(key)) {
            return configMap.get(key);
        }
        else {
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        return (containsKey(key)) ? configMap.get(key) : defaultValue;
    }

    public void setSource(String fileName) throws ConfigurationException {
        this.fileName = fileName;
    }

    public void setSource(File file) throws ConfigurationException {
        this.file = file;
    }

    public void setSource(InputStream inStream) throws ConfigurationException {
        this.inStream = inStream;
    }

    @Override
    public Charset getEncoding() {
        return encoding;
    }

    @Override
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
            throw new ConfigurationException(e);
        }
    }

    public void load(InputStream inStream) throws ConfigurationException {
        load(new BufferedReader(new InputStreamReader(inStream, getEncoding())));
    }

    public void load(Reader reader) throws ConfigurationException {
        try {
            Properties p = new Properties();
            p.load(reader);
            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                configMap.put((String) entry.getKey(),
                        (String) entry.getValue());
            }
        }
        catch (IOException e) {
            throw new ConfigurationException(e);
        }
    }

    @Override
    public Configuration merge(Configuration base) {
        for (String key : base.getKeys()) {
            if (!containsKey(key)) {
                configMap.put(key, base.getProperty(key));
            }
        }
        return this;
    }
}
