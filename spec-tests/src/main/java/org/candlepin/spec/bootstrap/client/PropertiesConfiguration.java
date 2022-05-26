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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Immutable class responsible for storing configuration properties. On creation reads properties
 * from all provided property suppliers and retains them for the whole spec-test suite run.
 */
public class PropertiesConfiguration{
    private static final Logger log = LoggerFactory.getLogger(PropertiesConfiguration.class);
    private static final String MISSING_MESSAGE = "\"%s\" not found in configuration";

    private final Map<String, String> configMap = new ConcurrentHashMap<>();

    /**
     * @param providers Providers ordered by priority from low to high
     */
    public PropertiesConfiguration(Supplier<Properties>... providers) {
        for (Supplier<Properties> provider : providers) {
            for (Map.Entry<Object, Object> entry : provider.get().entrySet()) {
                this.setProperty((String) entry.getKey(), (String) entry.getValue());
            }
        }
    }

    public boolean getBool(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }

    public boolean isEmpty() {
        return configMap.isEmpty();
    }

    public boolean containsKey(String key) {
        return null != key && configMap.containsKey(key);
    }

    public void setProperty(String key, String value) {
        configMap.put(key, value);
    }

    public Iterable<String> getKeys() {
        return configMap.keySet();
    }

    public String getProperty(String key) {
        if (containsKey(key)) {
            return configMap.get(key);
        }
        else {
            throw new NoSuchElementException(missingMessage(key));
        }
    }

    public String getProperty(String key, String defaultValue) {
        if (containsKey(key)) {
            return configMap.get(key);
        }
        log.debug("Key: {} not found. Using default value: {}", key, defaultValue);
        return defaultValue;
    }

    public String getProperty(ConfigKey key, String defaultValue) {
        return getProperty(key.key(), defaultValue);
    }

    public long getLong(ConfigKey key) {
        return Long.parseLong(getProperty(key.key()));
    }

    private String missingMessage(String key) {
        return String.format(MISSING_MESSAGE, key);
    }

}
