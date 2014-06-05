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

/** Inspired by Apache's Commons Configuration library
 */
public interface Configuration {
    /**
     * Return a Configuration object composed only of properties beginning
     * with the provided prefix.
     *
     * @param prefix
     * @return a new Configuration object containing only properties beginning with the provided
     * prefix
     */
    Configuration subset(String prefix);

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
     * Add a property to the configuration. If it already exists then the value
     * stated here will be added to the configuration entry. For example, if
     * the property:
     *
     * <pre>resource.loader = file</pre>
     *
     * is already present in the configuration and you call
     *
     * <pre>addProperty("resource.loader", "classpath")</pre>
     *
     * Then you will end up with a List like the following:
     *
     * <pre>["file", "classpath"]</pre>
     *
     * @param key The key to add the property to.
     * @param value The value to add.
     */
    void addProperty(String key, Object value);

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

    Boolean getBoolean(String key);
    Boolean getBoolean(String key, Boolean defaultValue);

    Integer getInteger(String key);
    Integer getInteger(String key, Integer defaultValue);

    Long getLong(String key);
    Long getLong(String key, Long defaultValue);

    String getString(String key);
    String getString(String key, String defaultValue);
}
