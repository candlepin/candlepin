/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;



/**
 * The JobDataMap is a typed HashMap that provides utility methods for fetching values as various
 * datatypes.
 */
public class JobDataMap implements Map<String, Object> {

    final Map<String, Object> map;

    /**
     * Creates a new JobDataMap instance backed by the provided map.
     *
     * @param map
     *  The map to use as the backing map
     *
     * @throws IllegalArgumentException
     *  if the provided map is null
     */
    public JobDataMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("map is null");
        }

        this.map = map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        this.map.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(Object key) {
        return this.map.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return this.map.entrySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return this.map.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(Object key) {
        return this.map.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> keySet() {
        return this.map.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object put(String key, Object value) {
        return this.map.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putAll(Map<? extends String, ? extends Object> map) {
        this.map.putAll(map);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object remove(Object key) {
        return this.map.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.map.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Object> values() {
        return this.map.values();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append(" {");
        Iterator<Map.Entry<String, Object>> iterator = this.map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();

            builder.append(entry.getKey());
            builder.append('=');
            builder.append(entry.getValue());

            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append('}');

        return builder.toString();
    }

    // Utility methods

    /**
     * Fetches the value associated with the specified key, cast to a boolean value. If the key
     * does not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @throws ClassCastException
     *  if the value cannot be cast to a Boolean value
     *
     * @return
     *  the value associated with the specified key as a boolean value, or the default value if the
     *  key does not exist
     */
    public Boolean getAsBoolean(String key, Boolean defaultValue) {
        return this.containsKey(key) ? (Boolean) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to a boolean value. If the key
     * does not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @throws ClassCastException
     *  if the value cannot be cast to a Boolean value
     *
     * @return
     *  the value associated with the specified key as a boolean value, or null if the key does
     *  not exist
     */
    public Boolean getAsBoolean(String key) {
        return this.getAsBoolean(key, null);
    }

    /**
     * Fetches the value associated with the specified key, cast to an integer value. If the key
     * does not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @throws ClassCastException
     *  if the value cannot be cast to an Integer value
     *
     * @return
     *  the value associated with the specified key as an integer value, or the default value if the
     *  key does not exist
     */
    public Integer getAsInteger(String key, Integer defaultValue) {
        return this.containsKey(key) ? (Integer) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to an integer value. If the key
     * does not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @throws ClassCastException
     *  if the value cannot be cast to an Integer value
     *
     * @return
     *  the value associated with the specified key as an integer value, or null if the key does
     *  not exist
     */
    public Integer getAsInteger(String key) {
        return this.getAsInteger(key, null);
    }

    /**
     * Fetches the value associated with the specified key, cast to a long value. If the key
     * does not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @return
     *  the value associated with the specified key as a long value, or the default value if the
     *  key does not exist
     */
    public Long getAsLong(String key, Long defaultValue) {
        return this.containsKey(key) ? (Long) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to a long value. If the key does
     * not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @return
     *  the value associated with the specified key as a long value, or null if the key does not
     *  exist
     */
    public Long getAsLong(String key) {
        return this.getAsLong(key, null);
    }

    /**
     * Fetches the value associated with the specified key, cast to a floating point value. If the
     * key does not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @return
     *  the value associated with the specified key as a floating point value, or the default value
     *  if the key does not exist
     */
    public Float getAsFloat(String key, Float defaultValue) {
        return this.containsKey(key) ? (Float) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to a floating point value. If the
     * key does not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @return
     *  the value associated with the specified key as a floating point value, or null if the key
     *  does not exist
     */
    public Float getAsFloat(String key) {
        return this.getAsFloat(key, null);
    }

    /**
     * Fetches the value associated with the specified key, cast to a floating point value. If the
     * key does not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @return
     *  the value associated with the specified key as a floating point value, or the default value
     *  if the key does not exist
     */
    public Double getAsDouble(String key, Double defaultValue) {
        return this.containsKey(key) ? (Double) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to a floating point value. If the
     * key does not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @return
     *  the value associated with the specified key as a floating point value, or null if the ke
     *  does not exist
     */
    public Double getAsDouble(String key) {
        return this.getAsDouble(key, null);
    }

    /**
     * Fetches the value associated with the specified key, cast to a string value. If the key does
     * not exist, the given default value is returned instead.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @param defaultValue
     *  The default value to return if the key does not exist
     *
     * @return
     *  the value associated with the specified key as a string value, or the default value if the
     *  key does not exist
     */
    public String getAsString(String key, String defaultValue) {
        return this.containsKey(key) ? (String) this.get(key) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, cast to a string value. If the key does
     * not exist, this method returns null.
     *
     * @param key
     *  The key for which to fetch the value
     *
     * @return
     *  the value associated with the specified key as a string value, or null if the key does not
     *  exist
     */
    public String getAsString(String key) {
        return this.getAsString(key, null);
    }

}
