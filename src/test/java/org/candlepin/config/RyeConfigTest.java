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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;


// TODO: FIXME: This test suite is very weak. Rewrite it.


public class RyeConfigTest {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
        Map.entry("test.string", "str"),
        Map.entry("test.string.trim", " a "),
        Map.entry("test.bool", "true"),
        Map.entry("test.int", "123"),
        Map.entry("test.long", Long.toString(Long.MAX_VALUE)),
        Map.entry("test.list", "a,b, c"),
        Map.entry("test.set", "a,b, c, d, a,b")
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
    public void stringValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        String value = ryeConfig.getString("test.value");

        assertThat(value)
            .isEmpty();
    }

    @Test
    public void stringValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getString("test.string"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void stringValueTrimmed() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);
        String value = ryeConfig.getString("test.string.trim");

        assertThat(value).isEqualTo("a");
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "TRUE", "TrUe", "1", "yes", "YES", "yEs" })
    public void testBooleanValuePresentWithTruthyValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        boolean output = ryeConfig.getBoolean("test.value");

        assertThat(output)
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "false", "FALSE", "FaLsE", "0", "no", "NO", "nO" })
    public void testBooleanValuePresentWithFalseyValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        boolean output = ryeConfig.getBoolean("test.value");

        assertThat(output)
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "123", "true but not really", "false but not really" })
    public void booleanValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        boolean output = ryeConfig.getBoolean("test.value");

        // This behavior is trash, btw. Invalid values should trigger an exception. Like they do in other
        // conversions.
        assertThat(output)
            .isFalse();
    }

    @Test
    public void boolValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");

        assertThatThrownBy(() -> ryeConfig.getBoolean("test.value"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void boolValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getBoolean("test.bool"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "-123", "2147483647", "-2147483648", "0" })
    public void integerValueFoundWithValidValue(String value) {
        int expected = Integer.parseInt(value);

        RyeConfig ryeConfig = buildConfig("test.value", value);
        int output = ryeConfig.getInt("test.value");

        assertThat(output).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "2147483648", "-2147483649" })
    public void integerValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);

        assertThatThrownBy(() -> ryeConfig.getInt("test.value"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void intValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");

        assertThatThrownBy(() -> ryeConfig.getInt("test.value"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void intValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getInt("test.int"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "-123", "9223372036854775807", "-9223372036854775808", "0" })
    public void longValueFoundWithValidValue(String value) {
        long expected = Long.parseLong(value);

        RyeConfig ryeConfig = buildConfig("test.value", value);
        long output = ryeConfig.getLong("test.value");

        assertThat(output).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "9223372036854775808", "-9223372036854775809" })
    public void longValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);

        assertThatThrownBy(() -> ryeConfig.getLong("test.value"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void longValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");

        assertThatThrownBy(() -> ryeConfig.getLong("test.value"))
            .isInstanceOf(NoSuchElementException.class);
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
    public void listEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        List<String> value = ryeConfig.getList("test.value");

        assertThat(value)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void listMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getList("test.list"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void setFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);
        Set<String> value = ryeConfig.getSet("test.set");

        assertThat(value)
            .containsExactly("a", "b", "c", "d");
    }

    @Test
    public void setEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Set<String> value = ryeConfig.getSet("test.value");

        assertThat(value)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void setMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();

        assertThatThrownBy(() -> ryeConfig.getSet("test.set"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void optionalStringValueFound() {
        RyeConfig ryeConfig = buildConfig("test.value", "value");
        Optional<String> value = ryeConfig.getOptionalString("test.value");

        assertThat(value)
            .isNotNull()
            .isPresent()
            .hasValue("value");
    }

    @Test
    public void optionalStringValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<String> value = ryeConfig.getOptionalString("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalStringValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<String> value = ryeConfig.getOptionalString("test.value");

        assertThat(value)
            .isNotNull()
            .isPresent()
            .hasValue("");
    }

    @Test
    public void optionalStringValueTrimmed() {
        RyeConfig ryeConfig = buildConfig("test.value", "  value  ");
        Optional<String> value = ryeConfig.getOptionalString("test.value");

        assertThat(value)
            .isNotNull()
            .isPresent()
            .hasValue("value");
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "TRUE", "TrUe", "1", "yes", "YES", "yEs" })
    public void optionalBooleanValueFoundWithTruthyValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        Optional<Boolean> output = ryeConfig.getOptionalBoolean("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValue(true);
    }

    @ParameterizedTest
    @ValueSource(strings = { "false", "FALSE", "FaLsE", "0", "no", "NO", "nO" })
    public void optionalBooleanValueFoundWithFalseyValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        Optional<Boolean> output = ryeConfig.getOptionalBoolean("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValue(false);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "123", "true but not really", "false but not really" })
    public void optionalBooleanValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);
        Optional<Boolean> output = ryeConfig.getOptionalBoolean("test.value");

        // This behavior is trash, btw. Invalid values should trigger an exception. Like they do in other
        // conversions.
        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValue(false);
    }

    @Test
    public void optionalBooleanValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<Boolean> value = ryeConfig.getOptionalBoolean("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalBooleanValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<Boolean> value = ryeConfig.getOptionalBoolean("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "-123", "2147483647", "-2147483648", "0" })
    public void optionalIntegerValueFoundWithValidValue(String value) {
        int expected = Integer.parseInt(value);

        RyeConfig ryeConfig = buildConfig("test.value", value);
        Optional<Integer> output = ryeConfig.getOptionalInt("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "2147483648", "-2147483649" })
    public void optionalIntegerValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);

        assertThatThrownBy(() -> ryeConfig.getOptionalInt("test.value"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void optionalIntegerValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<Integer> value = ryeConfig.getOptionalInt("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalIntegerValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<Integer> value = ryeConfig.getOptionalInt("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = { "123", "-123", "9223372036854775807", "-9223372036854775808", "0" })
    public void optionalLongValueFoundWithValidValue(String value) {
        long expected = Long.parseLong(value);

        RyeConfig ryeConfig = buildConfig("test.value", value);
        Optional<Long> output = ryeConfig.getOptionalLong("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "asd", "9223372036854775808", "-9223372036854775809" })
    public void optionalLongValueFoundWithInvalidValue(String value) {
        RyeConfig ryeConfig = buildConfig("test.value", value);

        assertThatThrownBy(() -> ryeConfig.getOptionalLong("test.value"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void optionalLongValueMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<Long> value = ryeConfig.getOptionalLong("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalLongValueEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<Long> value = ryeConfig.getOptionalLong("test.value");

        assertThat(value)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalListFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);
        Optional<List<String>> output = ryeConfig.getOptionalList("test.list");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> {
                assertThat(value).containsExactly("a", "b", "c");
            });
    }

    @Test
    public void optionalListEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<List<String>> output = ryeConfig.getOptionalList("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> {
                assertThat(value).isEmpty();
            });
    }

    @Test
    public void optionalListMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<List<String>> output = ryeConfig.getOptionalList("test.value");

        assertThat(output)
            .isNotNull()
            .isNotPresent();
    }

    @Test
    public void optionalSetFound() {
        RyeConfig ryeConfig = buildConfig(DEFAULTS);
        Optional<Set<String>> output = ryeConfig.getOptionalSet("test.set");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> {
                assertThat(value).containsExactly("a", "b", "c", "d");
            });
    }

    @Test
    public void optionalSetEmpty() {
        RyeConfig ryeConfig = buildConfig("test.value", "");
        Optional<Set<String>> output = ryeConfig.getOptionalSet("test.value");

        assertThat(output)
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> {
                assertThat(value).isEmpty();
            });
    }

    @Test
    public void optionalSetMissing() {
        RyeConfig ryeConfig = buildEmptyConfig();
        Optional<Set<String>> output = ryeConfig.getOptionalSet("test.value");

        assertThat(output)
            .isNotNull()
            .isNotPresent();
    }

    private static RyeConfig buildConfig(Map<String, String> defaults) {
        SmallRyeConfig smallRyeConfig = new SmallRyeConfigBuilder()
            .withDefaultValues(defaults)
            .build();

        return new RyeConfig(smallRyeConfig);
    }

    private static RyeConfig buildConfig(String key, String value) {
        return buildConfig(Map.of(key, value));
    }

    private static RyeConfig buildEmptyConfig() {
        return buildConfig(Map.of());
    }
}
