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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Mutable implementation of configuration for easier testing without smallrye dependency.
 */
public class DevConfig implements Configuration {
    private final Map<String, String> config;

    public DevConfig() {
        this(new HashMap<>());
    }

    public DevConfig(Map<String, String> config) {
        this.config = new HashMap<>(Objects.requireNonNull(config));
    }

    @Override
    public Map<String, String> getValuesByPrefix(String prefix) {
        return config.keySet().stream()
            .filter(key -> key.startsWith(prefix))
            .collect(Collectors.toMap(Function.identity(), config::get));
    }

    @Override
    public Properties toProperties() {
        Properties result = new Properties(this.config.size());
        result.putAll(this.config);
        return result;
    }

    public void setProperty(String key, String value) {
        this.config.put(key, value);
    }

    public void clearProperty(String key) {
        this.config.remove(key);
    }

    @Override
    public Iterable<String> getKeys() {
        return this.config.keySet();
    }

    @Override
    public boolean getBoolean(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        String value = this.config.get(key);
        return Boolean.parseBoolean(value);
    }

    @Override
    public int getInt(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        String value = this.config.get(key);
        return Integer.parseInt(value);
    }

    @Override
    public long getLong(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        String value = this.config.get(key);
        return Long.parseLong(value);
    }

    @Override
    public String getString(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        String value = this.config.get(key);
        if (value != null) {
            return value.trim();
        }
        return null;
    }

    private List<String> parseList(String value) {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .toList();
    }

    @Override
    public List<String> getList(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        String value = this.config.get(key);
        if (value == null || StringUtils.isEmpty(value)) {
            return new ArrayList<>();
        }

        return parseList(value);
    }

    @Override
    public Set<String> getSet(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (!this.config.containsKey(key)) {
            throw new NoSuchElementException("key not found: " + key);
        }

        return new HashSet<>(getList(key));
    }

}
