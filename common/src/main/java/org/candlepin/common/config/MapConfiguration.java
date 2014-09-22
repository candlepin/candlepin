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

import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Configuration implementation.
 */
public class MapConfiguration extends AbstractConfiguration {
    private ConcurrentHashMap<String, Object> configMap;


    public MapConfiguration() {
        configMap = new ConcurrentHashMap<String, Object>();
    }

    public MapConfiguration(Map<String, ?> configMap) {
        this.configMap = new ConcurrentHashMap<String, Object>(configMap);
    }

    @Override
    public Configuration subset(String prefix) {
        return new MapConfiguration(subsetMap(prefix));
    }

    protected Map<String, Object> subsetMap(String prefix) {
        Map<String, Object> subset = new TreeMap<String, Object>();

        for (Map.Entry<String, Object> e : configMap.entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                subset.put(e.getKey(), e.getValue());
            }
        }

        return subset;
    }

    @Override
    public Configuration merge(Configuration base) {
        MapConfiguration mergedConfig = new MapConfiguration(configMap);
        for (String key : base.getKeys()) {
            if (!containsKey(key)) {
                mergedConfig.setProperty(key, base.getProperty(key));
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
        return configMap.containsKey(key);
    }

    @Override
    public void setProperty(String key, Object value) {
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

    @Override
    public String toString() {
        return configMap.toString();
    }

    @Override
    public Map<String, Object> getNamespaceMap(String prefix) {
        return getNamespaceMap(prefix, null);
    }

    @Override
    public Map<String, Object> getNamespaceMap(String prefix, Map<String, Object> defaults) {
        Map<String, Object> m = new TreeMap<String, Object>();

        if (defaults != null) {
            for (Entry<String, Object> entry : defaults.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                    m.put(entry.getKey(), entry.getValue());
                }
            }
        }
        m.putAll(subsetMap(prefix));
        return m;
    }

    @Override
    public Properties getNamespaceProperties(String prefix) {
        return getNamespaceProperties(prefix, null);
    }

    @Override
    public Properties getNamespaceProperties(String prefix, Map<String, Object> defaults) {
        // TODO: HACK HACK HACK
        if (prefix.startsWith(JPAConfigParser.JPA_CONFIG_PREFIX)) {
            return new JPAConfigParser().parseConfig(configMap);
        }

        Properties p = new Properties();

        if (defaults != null) {
            for (Entry<String, Object> entry : defaults.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(prefix)) {
                    p.put(entry.getKey(), entry.getValue());
                }
            }
        }
        p.putAll(subsetMap(prefix));
        return p;
    }
}
