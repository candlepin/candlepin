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

import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class RyeConfig implements Configuration {
    SmallRyeConfig config;

    public Configuration setConfig(SmallRyeConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public Configuration subset(String prefix) {
        Map<String, String> subMap = new HashMap<>();
        config.getPropertyNames().forEach(x -> {
            if (x.startsWith(prefix)) {
                subMap.put(x, config.getRawValue(x));
            }
        });
        return (Configuration) new RyeConfig()
            .setConfig(new SmallRyeConfigBuilder()
            .withSources(new PropertiesConfigSource(subMap, prefix, 100))
            .build());
    }

    @Override
    public Configuration strippedSubset(String prefix) {
        Map<String, String> subMap = new HashMap<>();
        String thePrefix = prefix.endsWith(".") ? prefix : (prefix + ".");
        config.getPropertyNames().forEach(x -> {
            if (x.startsWith(thePrefix)) {
                subMap.put(x.substring(thePrefix.length()), config.getRawValue(x));
            }
        });
        return new RyeConfig()
            .setConfig(new SmallRyeConfigBuilder()
            .withSources(new PropertiesConfigSource(subMap, prefix, 100))
            .build());
    }

    @Override
    public Properties toProperties() {
        Properties result = new Properties();
        config.getPropertyNames().forEach(x -> result.put(x, config.getRawValue(x)));
        return  result;
    }

    @Override
    public Properties toProperties(Map<String, String> defaults) {
        Properties result = new Properties();
        defaults.keySet().forEach(x -> result.put(x, defaults.get(x)));
        config.getPropertyNames().forEach(x -> result.put(x, config.getRawValue(x)));
        return  result;
    }

    @Override
    public Properties toProperties(Properties defaults) {
        Properties result = new Properties();
        defaults.keySet().forEach(x -> result.put(x, defaults.get(x)));
        config.getPropertyNames().forEach(x -> result.put(x, config.getRawValue(x)));
        return  result;
    }

    @Override
    public Map<String, String> toMap() {
        Map result = new HashMap();
        config.getPropertyNames().forEach(x -> result.put(x, config.getRawValue(x)));
        return  result;
    }

    @Override
    public Map<String, String> toMap(Map<String, String> defaults) {
        Map result = new HashMap();
        defaults.keySet().forEach(x -> result.put(x, defaults.get(x)));
        config.getPropertyNames().forEach(x -> result.put(x, config.getRawValue(x)));
        return  result;
    }

    @Override
    public boolean isEmpty() {
        return !config.getPropertyNames().iterator().hasNext();
    }

    @Override
    public boolean containsKey(String key) {
        return config.isPropertyPresent(key);
    }

    @Override
    public void setProperty(String key, String value) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue != null) {
            configValue.withValue(value);
        }
        else {
            throw new RuntimeException("Cannot set value for unknown configuration property");
        }
    }

    @Override
    public void clear() {
        config.getPropertyNames().forEach(x -> clearProperty(x));
    }

    @Override
    public void clearProperty(String key) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue != null) {
            configValue.withValue(null);
        }
    }

    @Override
    public Iterable<String> getKeys() {
        return config.getPropertyNames();
    }

    @Override
    public String getProperty(String key) {
        return config.getRawValue(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getProperty(key);
    }

    @Override
    public boolean getBoolean(String key) {
        return config.getValue(key, Boolean.class);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getBoolean(key);
    }

    @Override
    public int getInt(String key) {
        return config.getValue(key, Integer.class);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getInt(key);
    }

    @Override
    public long getLong(String key) {
        return config.getValue(key, Long.class);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getLong(key);
    }

    @Override
    public String getString(String key) {
        return config.getRawValue(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getString(key);
    }

    @Override
    public String getString(String key, String defaultValue, TrimMode trimMode) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return TrimMode.TRIM.equals(trimMode) ? getString(key).trim() : getString(key);
    }

    @Override
    public List<String> getList(String key) {
        if (config.getConfigValue(key) == null ||
            StringUtils.isEmpty(config.getRawValue(key))) {
            return new ArrayList<>();
        }
        return config.getValues(key, String.class);
    }

    @Override
    public List<String> getList(String key, List<String> defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getList(key);
    }

    @Override
    public Set<String> getSet(String key) {
        return getList(key).stream().collect(Collectors.toSet());
    }

    @Override
    public Set<String> getSet(String key, Set<String> defaultValue) {
        ConfigValue configValue = config.getConfigValue(key);
        if (configValue == null || configValue.getValue() == null) {
            return defaultValue;
        }
        return getSet(key);
    }
}
