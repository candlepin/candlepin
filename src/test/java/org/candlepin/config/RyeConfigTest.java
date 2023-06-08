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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

public class RyeConfigTest {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry("test.string", "str"),
        Map.entry("test.string.trim", " a "),
        Map.entry("test.bool", "true"),
        Map.entry("test.int", "123"),
        Map.entry("test.long", Long.toString(Long.MAX_VALUE)),
        Map.entry("test.list", "a,b, c"),
        Map.entry("test.set", "a,b, c")
    );
    public static final String PREFIX = "test";

    @Test
    public void valueByPrefixFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        Map<String, String> values = ryeConfig.getValuesByPrefix(PREFIX);

        assertThat(values)
            .isNotEmpty()
            .containsEntry("test.string", "str");
    }

    @Test
    public void valueByPrefixEmpty() {
        RyeConfig ryeConfig = buildEmptyConfig();

        Map<String, String> values = ryeConfig.getValuesByPrefix(PREFIX);

        assertThat(values).isEmpty();
    }

    @Test
    public void toProperties() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        Properties properties = ryeConfig.toProperties();

        assertThat(properties)
            .containsEntry("test.string", "str");
    }

    @Test
    public void toPropertiesEmpty() {
        RyeConfig ryeConfig = buildEmptyConfig();

        Properties properties = ryeConfig.toProperties();

        assertThat(properties).isEmpty();
    }

    @Test
    public void listKeys() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        Iterable<String> keys = ryeConfig.getKeys();

        assertThat(keys).contains("test.string");
    }

    @Test
    public void listKeysEmpty() {
        RyeConfig ryeConfig = buildEmptyConfig();

        Properties properties = ryeConfig.toProperties();

        assertThat(properties).isEmpty();
    }

    @Test
    public void stringValueFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        String value = ryeConfig.getString("test.string");

        assertThat(value)
            .isEqualTo("str");
    }

    @Test
    public void stringValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        String value = ryeConfig.getString("test.string");

        assertThat(value).isNull();
    }

    @Test
    public void stringValueTrimmed() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        String value = ryeConfig.getString("test.string.trim");

        assertThat(value).isEqualTo("a");
    }

    @Test
    public void boolValueFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        boolean value = ryeConfig.getBoolean("test.bool");

        assertThat(value).isTrue();
    }

    @Test
    public void boolValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getBoolean("test.bool"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void intValueFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        int value = ryeConfig.getInt("test.int");

        assertThat(value).isEqualTo(123);
    }

    @Test
    public void intValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getBoolean("test.int"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void longValueFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        long value = ryeConfig.getLong("test.long");

        assertThat(value).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void longValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getLong("test.long"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void listFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        List<String> value = ryeConfig.getList("test.list");

        assertThat(value)
            .containsExactly("a", "b", "c");
    }

    @Test
    public void listMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        List<String> value = ryeConfig.getList("test.list");

        assertThat(value).isEmpty();
    }

    @Test
    public void setFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);

        Set<String> value = ryeConfig.getSet("test.set");

        assertThat(value)
            .containsExactly("a", "b", "c");
    }

    @Test
    public void setMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        Set<String> value = ryeConfig.getSet("test.set");

        assertThat(value).isEmpty();
    }

    private RyeConfig buildEmptyConfig() {
        return buildConfig(Map.of());
    }

    private RyeConfig buildConfig(Map<String, String> defaults) {
        SmallRyeConfig smallRyeConfig = new SmallRyeConfigBuilder()
            .withDefaultValues(defaults)
            .build();
        return new RyeConfig(smallRyeConfig);
    }
}
