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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
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
        String rawValue = this.config.getRawValue(key);
        if (rawValue != null) {
            return rawValue.trim();
        }
        return null;
    }

    @Override
    public boolean getBoolean(String key) {
        return this.config.getValue(key, Boolean.class);
    }

    @Override
    public int getInt(String key) {
        return this.config.getValue(key, Integer.class);
    }

    @Override
    public long getLong(String key) {
        return this.config.getValue(key, Long.class);
    }

    @Override
    public List<String> getList(String key) {
        if (StringUtils.isEmpty(this.config.getRawValue(key))) {
            return new ArrayList<>();
        }
        return this.config.getValues(key, String.class).stream()
            .map(String::trim)
            .toList();
    }

    @Override
    public Set<String> getSet(String key) {
        return new HashSet<>(getList(key));
    }

}
