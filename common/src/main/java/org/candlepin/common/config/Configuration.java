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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Inspired by Apache's Commons Configuration library.
 * <p>
 * This class should only be used to hold <b>immutable objects</b>.  If you place
 * a mutable object in the configuration and using the subset() method, both configurations
 * will reference the same object and any changes to that object will be reflected
 * in both!
 */
public interface Configuration {

    /**
     * Enumeration that defines whether or not to trim whitespace from a String.
     */
    public static enum TrimMode {
        TRIM,
        NO_TRIM;
    }

    /**
     * Return a Configuration object composed only of properties beginning
     * with the provided prefix.
     *
     * @param prefix
     * @return a new Configuration object containing only properties beginning with the provided
     * prefix.  The object will be empty if no matches are found.
     */
    Configuration subset(String prefix);

    Map<String, Object> getNamespaceMap(String prefix);
    Map<String, Object> getNamespaceMap(String prefix, Map<String, Object> defaults);
    Properties getNamespaceProperties(String prefix);
    Properties getNamespaceProperties(String prefix, Map<String, Object> defaults);

    /**
     * Begin with the configuration provided by base but for any keys defined in
     * both objects, use the values in this object.
     * @param base
     * @return the merged configuration
     */
    Configuration merge(Configuration base);

    /**
     * Check if the configuration is empty.
     *
     * @return {@code true} if the configuration contains no property,
     *         {@code false} otherwise.
     */
    boolean isEmpty();

    /**
     * Check if the configuration contains the specified key.
     *
     * @param key the key whose presence in this configuration is to be tested
     *
     * @return {@code true} if the configuration contains a value for this
     *         key, {@code false} otherwise
     */
    boolean containsKey(String key);

    /**
     * Set a property, this will replace any previously set values. Set values
     * is implicitly a call to clearProperty(key), addProperty(key, value).
     *
     * @param key The key of the property to change
     * @param value The new value
     */
    void setProperty(String key, Object value);

    /**
     * Remove all properties from the configuration.
     */
    void clear();

    /**
     * Remove a property.
     * @param key the key to remove
     */
    void clearProperty(String key);

    Iterable<String> getKeys();

    Object getProperty(String key);
    Object getProperty(String key, Object defaultValue);

    boolean getBoolean(String key);
    boolean getBoolean(String key, boolean defaultValue);

    int getInt(String key);
    int getInt(String key, int defaultValue);

    long getLong(String key);
    long getLong(String key, long defaultValue);

    /**
     * Return a property of type String <b>with all whitespace trimmed!</b>
     * @param key
     * @return a String property with all whitespace trimmed by String.trim()
     */
    String getString(String key);

    /**
     * Return a property of type String <b>with all whitespace trimmed!</b> or
     * the default value if the key is not found.
     * @param key
     * @return a String property with all whitespace trimmed by String.trim() or the default value
     * if the key is not found.
     */
    String getString(String key, String defaultValue);

    String getString(String key, String defaultValue, TrimMode trimMode);

    List<String> getList(String key);
    List<String> getList(String key, List<String> defaultValue);
    Set<String> getSet(String key);
    Set<String> getSet(String key, Set<String> defaultValue);
}
