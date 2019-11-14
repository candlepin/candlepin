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

import org.candlepin.util.ObjectMapperFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;



/**
 * The JobArguments class is a map-like view of the arguments provided to the job during
 * construction. Unlike a typical map, the types of the values must be known to properly fetch them.
 */
@SuppressWarnings("checkstyle:JavadocMethodMain")
public class JobArguments {

    private final Map<String, Object> data;

    /**
     * Creates a new JobArguments map view using the specified map as a data store.
     *
     * @param map
     *  the map to use as the data store for this view
     */
    public JobArguments(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }

        this.data = data;
    }

    /**
     * Checks if the given key is present in the arguments map.
     *
     * @param key
     *  the key to check
     *
     * @return
     *  true if the key is present in the arguments map; false otherwise
     */
    public boolean containsKey(String key) {
        return this.data.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof JobArguments && this.data.equals(((JobArguments) obj).data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return this.data.hashCode();
    }

    /**
     * Checks if the arguments map is empty
     *
     * @return
     *  true if the arguments map is empty; false otherwise
     */
    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    /**
     * Fetches the set of keys currently set in the arguments map. If the argument map does not
     * contain any arguments, this method returns an empty set.
     *
     * @return
     *  the set of keys currently set in the arguments map
     */
    public Set<String> keySet() {
        return this.data.keySet();
    }

    /**
     * Fetches the size of the arguments map
     *
     * @return
     *  the number of arguments in the arguments map
     */
    public int size() {
        return this.data.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("JobArguments [size: %d, keys: (%s)]",
            this.size(), String.join(", ", this.keySet()));
    }

    // Utility methods

    /**
     * Fetches the data associated with the given key without casting it to any particular type. If
     * the key is not currently associated with any value, this method returns null.
     *
     * @param key
     *  the key for which to fetch the associated value
     *
     * @return
     *  the value associated with the specified key, or null if the key is not currently associated
     *  with a value
     */
    public Object get(String key) {
        return this.data.get(key);
    }

    /**
     * Fetches the value associated with the specified key, deserialized as the given type. If the
     * key is not currently associated with any value, this method returns the given default value.
     *
     * @param key
     *  the key for which to fetch the associated value
     *
     * @param type
     *  the type to use to deserialize the the value
     *
     * @param defaultValue
     *  the value to return if the key is not currently associated with a value
     *
     * @return
     *  the value associated with the specified key, or the given default value if the
     *  key is not currently associated with a value
     */
    public <T> T getAs(String key, Class<T> type, T defaultValue) {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }

        return this.containsKey(key) ? type.cast(this.data.get(key)) : defaultValue;
    }

    /**
     * Fetches the value associated with the specified key, deserialized as the given type. If the
     * key is not currently associated with any value, this method returns null.
     *
     * @param key
     *  the key for which to fetch the associated value
     *
     * @param type
     *  the type to use to deserialize the the value
     *
     * @return
     *  the deserialized value associated with the specified key, or null if the key is not
     *  currently associated with a value
     */
    public <T> T getAs(String key, Class<T> type) {
        return this.getAs(key, type, null);
    }

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
        return this.getAs(key, Boolean.class, defaultValue);
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
        return this.getAs(key, Boolean.class, null);
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
        return this.getAs(key, Integer.class, defaultValue);
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
        return this.getAs(key, Integer.class, null);
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
        return this.getAs(key, Long.class, defaultValue);
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
        return this.getAs(key, Long.class, null);
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
        return this.getAs(key, Float.class, defaultValue);
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
        return this.getAs(key, Float.class, null);
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
        return this.getAs(key, Double.class, defaultValue);
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
        return this.getAs(key, Double.class, null);
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
        return this.getAs(key, String.class, defaultValue);
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
        return this.getAs(key, String.class, null);
    }
}
