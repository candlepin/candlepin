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

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Configuration implementation.
 */
public class MapConfiguration extends AbstractConfiguration {
    private ConcurrentHashMap<String, Object> configMap = new ConcurrentHashMap<String, Object>();

    @Override
    public Configuration subset(String prefix) {
        Configuration subset = new MapConfiguration();
        for (Map.Entry<String, Object> e : configMap.entrySet()) {
            String k = e.getKey();
            if (k.startsWith(prefix)) {
                subset.setProperty(k, e.getValue());
            }
        }

        return subset;
    }

    @Override
    public Configuration merge(Configuration base) {
        for (String key : base.getKeys()) {
            if (!containsKey(key)) {
                setProperty(key, base.getProperty(key));
            }
        }
        return this;
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
}
