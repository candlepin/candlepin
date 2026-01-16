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

import io.smallrye.config.SmallRyeConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;



public class RyeConfig implements Configuration {
    private final SmallRyeConfig config;

    public RyeConfig(SmallRyeConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Map<String, String> getValuesByPrefix(String prefix) {
        return StreamSupport.stream(this.config.getPropertyNames().spliterator(), false)
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toMap(Function.identity(), this.config::getRawValue));
    }

    @Override
    public Properties toProperties() {
        Properties result = new Properties();
        this.config.getPropertyNames().forEach(x -> {
            if (this.config.getRawValue(x) != null) {
                result.put(x, this.config.getRawValue(x));
            }
        });

        return result;
    }

    @Override
    public Iterable<String> getKeys() {
        return this.config.getPropertyNames();
    }

    @Override
    public String getString(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        // This whole implementation is crazy and *really* stupid. SmallRye config goes out of its way at
        // several places to make determining the difference between absence, null, and empty impossible. Our
        // only hope is to pull the ConfigValue and sort it out ourselves. Dumb. So very dumb.
        String value = this.config.getConfigValue(key)
            .getValue();

        if (value == null) {
            throw new NoSuchElementException(key);
        }

        return value.trim();
    }

    @Override
    public boolean getBoolean(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.config.getValue(key, Boolean.class);
    }

    @Override
    public int getInt(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.config.getValue(key, Integer.class);
    }

    @Override
    public long getLong(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.config.getValue(key, Long.class);
    }

    /**
     * Attempts to fetch the value of the given configuration key as a collection of trimmed strings. If the
     * configuration key is not defined or the source value cannot be converted to a collection, this method
     * throws an exception. If the key is defined, but is empty or blank -- that is, String.isBlank() would
     * return true -- this method returns an empty collection.
     * <p>
     * This method splits the source value first by checking for the presence of SmallRye's "indexed values",
     * or those defined as key[0], key[1], etc.. If indexed values are not found, the source value is
     * interpreted as a comma-delimited string, with backslashes (\) used to escape commas. The individual
     * values are trimmed after splitting or reinterpretation.
     *
     * @param key
     *  the configuration key of the desired value
     *
     * @param collectionBuilder
     *  an integer function which can build the target collection from a specified collection size
     *
     * @throws NoSuchElementException
     *  if the configuration key is not defined
     *
     * @throws IllegalArgumentException
     *  if the source value of the given key cannot be interpreted as a collection of strings
     *
     * @return
     *  a collection of trimmed strings from the source value
     */
    private <T extends Collection<String>> T getCollection(String key, IntFunction<T> collectionBuilder) {
        try {
            return this.config.getValues(key, String::trim, collectionBuilder);
        }
        catch (NoSuchElementException e) {
            // We need to check if it was absent or empty. In the latter case, return an empty list instead
            // of throwing an exception. In the former case, allow the exception to propagate.
            if (this.getString(key).isBlank()) {
                return collectionBuilder.apply(0);
            }
        }

        // We shouldn't ever get here, but we're contingent on SmallRye working the way we expect for that
        // to hold true. Also the compiler can't tell.
        throw new IllegalArgumentException("property value cannot be converted to a collection: " + key);
    }

    @Override
    public List<String> getList(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.getCollection(key, ArrayList::new);
    }

    @Override
    public Set<String> getSet(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        return this.getCollection(key, HashSet::new);
    }

}
