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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory Configuration implementation.
 */
public class MapConfiguration extends AbstractConfiguration {
    private static Logger log = LoggerFactory.getLogger(MapConfiguration.class);

    private Map<String, String> configMap;

    public MapConfiguration() {
        configMap = new ConcurrentHashMap<String, String>();
    }

    public MapConfiguration(Map<String, String> configMap) {
        // ConcurrentHashMap doesn't work with null keys but HashMap
        // supports them.  We have to check the type first because
        // ConcurrentHashMap will throw a NPE on containsKey(null)
        if (HashMap.class.isAssignableFrom(configMap.getClass()) &&
            configMap.containsKey(null)) {
            log.error("Keys with a null value are not supported!");
            throw new RuntimeException(
                new ConfigurationException("Keys with a null value are not supported"));
        }
        this.configMap = new ConcurrentHashMap<String, String>(configMap);
    }

    public MapConfiguration(Configuration config) {
        this(config.toMap());
    }

    @Override
    public Configuration subset(String prefix) {
        return new MapConfiguration(subsetMap(prefix));
    }

    @Override
    public Configuration strippedSubset(String prefix) {
        Map<String, String> subset = subsetMap(prefix);
        Configuration c = new MapConfiguration();
        for (Map.Entry<String, String> entry : subset.entrySet()) {
            String strippedKey = entry.getKey().replaceFirst(Pattern.quote(prefix), "");
            c.setProperty(strippedKey, entry.getValue());
        }
        return c;
    }

    protected Map<String, String> subsetMap(String prefix) {
        Map<String, String> subset = new ConcurrentHashMap<String, String>();

        for (Map.Entry<String, String> e : configMap.entrySet()) {
            // ConcurrentHashMaps do not allow null as a key but other
            // implementations do (HashMap) so we'll check to prevent
            // future bugs should the backing store change.
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                subset.put(e.getKey(), e.getValue());
            }
        }

        return subset;
    }

    /**
     * Merge configuration objects.  Any collisions on keys will use the value
     * from the leftmost argument.
     *
     * @param configs
     * @return the merged configuration
     */
    public static MapConfiguration merge(Configuration ... configs) {
        MapConfiguration mergedConfig = new MapConfiguration();
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
    public boolean isEmpty() {
        return configMap.isEmpty();
    }

    @Override
    public boolean containsKey(String key) {
        return (null == key) ? false : configMap.containsKey(key);
    }

    @Override
    public void setProperty(String key, String value) {
        configMap.put(key, value);
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
    public String getProperty(String key) {
        if (containsKey(key)) {
            return configMap.get(key);
        }
        else {
            super.missingMessage(key);
            throw new NoSuchElementException(doesNotMapMessage(key));
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return (containsKey(key)) ? configMap.get(key) : defaultValue;
    }

    @Override
    public String toString() {
        return configMap.toString();
    }
}
