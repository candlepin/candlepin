/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.config;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;



/**
 * Configuration interface defining
 */
public interface Configuration {

    Map<String, String> getValuesByPrefix(String prefix);
    Properties toProperties();
    Iterable<String> getKeys();

    /**
     * Return a property of type String <b>with all whitespace trimmed!</b>
     * @param key the key of the retrieved property
     * @return a String property with all whitespace trimmed by String.trim()
     */
    String getString(String key);
    boolean getBoolean(String key);
    int getInt(String key);
    long getLong(String key);
    List<String> getList(String key);
    Set<String> getSet(String key);

    // Below are cludges to deal with the many shortcomings of SmallRye's API and the embarrassingly
    // lackluster abstraction we built around it. Unfortunately the least painful way of getting to the things
    // I need without losing *other* functionality (e.g. password protected keys) is to expose some of
    // SmallRye's "optionals" APIs. Fortunately this is among the least bad options available to us at the
    // time of writing.

    /**
     * Fetches the trimmed string representation of the given configuration key, if it exists. If the
     * configuration does not define the given key, this method returns an empty optional. If the given key
     * is, itself, null, this method throws an exception. This method will never return null.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the configuration value for the given key as a trimmed string, or an empty
     *  optional if the key is not defined
     */
    default Optional<String> getOptionalString(String key) {
        try {
            return Optional.of(this.getString(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Fetches the boolean representation of the given configuration key, if it exists. If the configuration
     * does not define the given key, this method returns an empty optional. If the given key is, itself, null
     * or the value cannot be expressed as a boolean, this method throws an exception. This method will never
     * return null.
     * <p>
     * The source value is converted by interpreting the following case-insensitive strings as true: "true",
     * "yes", "1"; whereas the following will be interpreted as false: "false", "no", "0". The underlying
     * configuration library may also add additional interpretations, but these are not guaranteed nor
     * required.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the configuration value for the given key as a boolean value, or an empty
     *  optional if the key is not defined
     */
    default Optional<Boolean> getOptionalBoolean(String key) {
        try {
            return Optional.of(this.getBoolean(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Fetches the integer representation of the given configuration key, if it exists. If the configuration
     * does not define the given key, this method returns an empty optional. If the given key is, itself, null
     * or the value cannot be expressed as an integer, this method throws an exception. This method will never
     * return null.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the configuration value for the given key as an integer value, or an empty
     *  optional if the key is not defined
     */
    default Optional<Integer> getOptionalInt(String key) {
        try {
            return Optional.of(this.getInt(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Fetches the long int representation of the given configuration key, if it exists. If the configuration
     * does not define the given key, this method returns an empty optional. If the given key is, itself, null
     * or the value cannot be expressed as a long integer, this method throws an exception. This method will
     * never return null.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the configuration value for the given key as an long value, or an empty
     *  optional if the key is not defined
     */
    default Optional<Long> getOptionalLong(String key) {
        try {
            return Optional.of(this.getLong(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Fetches the configuration value stored at the specified key as a list of trimmed strings. The list is
     * built as if splitting the raw string value on the regular expression "\s*,\s*", retaining the order in
     * which the values appear in the original value, retaining any duplicate values which may be present. If
     * the value only defines a single value (that is, lacks the comma delimiter), this method returns a list
     * with a single, trimmed value. If the key is defined but does not have a value -- such that
     * String.isBlank() would return true -- this method returns an empty list. If the key is not defined at
     * all, this method returns an empty optional. This method will never return null.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the a list of trimmed strings parsed from the configuration value for the given
     *  key, or an optional if the key is not defined
     */
    default Optional<List<String>> getOptionalList(String key) {
        try {
            return Optional.of(this.getList(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /**
     * Fetches the configuration value stored at the specified key as a set of trimmed strings. The set is
     * built as if splitting the raw string value on the regular expression "\s*,\s*", with duplicate values
     * removed but order being lost. If the value only defines a single value (that is, lacks the comma
     * delimiter), this method returns a list with a single, trimmed value. If the key is defined but does not
     * have a value -- such that String.isBlank() would return true -- this method returns an empty list. If
     * the key is not defined at all, this method returns an empty optional. This method will never return
     * null.
     *
     * @param key
     *  the key of the desired configuration value, case-sensitive
     *
     * @throws IllegalArgumentException
     *  if the given key is null
     *
     * @return
     *  an optional containing the a set of trimmed strings parsed from the configuration value for the given
     *  key, or an optional if the key is not defined
     */
    default Optional<Set<String>> getOptionalSet(String key) {
        try {
            return Optional.of(this.getSet(key));
        }
        catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

}
