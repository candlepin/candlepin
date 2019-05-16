/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;

import javax.inject.Provider;



/**
 * Test suite for the AttributeValidator class
 */
public class AttributeValidatorTest {

    private static I18n i18n = I18nFactory.getI18n(AttributeValidatorTest.class, Locale.US,
        I18nFactory.FALLBACK);
    private static Provider<I18n> i18nProvider = () -> i18n;

    private Configuration config;

    @BeforeEach
    public void init() {
        this.config = new CandlepinCommonTestConfig();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "1", "0", "-1", "string value"})
    public void testLongAttributeKeyFailure(String value) {
        StringBuilder builder = new StringBuilder(AttributeValidator.ATTRIBUTE_MAX_LENGTH + 2);
        for (int i = 0; i < AttributeValidator.ATTRIBUTE_MAX_LENGTH + 1; ++i) {
            builder.append(i % 10);
        }

        AttributeValidator validator = new AttributeValidator(this.config, i18nProvider);
        assertThrows(PropertyValidationException.class, () -> validator.validate(builder.toString(), value));
    }

    @Test
    public void testLongAttributeValueFailure() {
        StringBuilder builder = new StringBuilder(AttributeValidator.ATTRIBUTE_MAX_LENGTH + 2);
        for (int i = 0; i < AttributeValidator.ATTRIBUTE_MAX_LENGTH + 1; ++i) {
            builder.append(i % 10);
        }

        AttributeValidator validator = new AttributeValidator(this.config, i18nProvider);
        assertThrows(PropertyValidationException.class, () -> validator.validate("key", builder.toString()));
    }

    @ParameterizedTest
    @CsvSource({
        "testattrib, testvalue, true",
        "testattrib, yes, true",
        "testattrib, no, true",
        "testattrib, y, true",
        "testattrib, n, true",
        "testattrib, true, true",
        "testattrib, false, true",
        "testattrib, 1, true",
        "testattrib, 0, true",
        "testattrib, -1, true",
        "testattrib, 2147483647, true",
        "testattrib, -2147483648, true",
        "testattrib, 9223372036854775807, true",
        "testattrib, -9223372036854775808, true",
        "testattrib, 3.14, true",
        "testattrib, -2.72, true",

        "intattrib, testvalue, false",
        "intattrib, yes, false",
        "intattrib, no, false",
        "intattrib, y, false",
        "intattrib, n, false",
        "intattrib, true, false",
        "intattrib, false, false",
        "intattrib, 1, true",
        "intattrib, 0, true",
        "intattrib, -1, true",
        "intattrib, 2147483647, true",
        "intattrib, -2147483648, true",
        "intattrib, 9223372036854775807, false",
        "intattrib, -9223372036854775808, false",
        "intattrib, 3.14, false",
        "intattrib, -2.72, false",

        "nnintattrib, testvalue, false",
        "nnintattrib, yes, false",
        "nnintattrib, no, false",
        "nnintattrib, y, false",
        "nnintattrib, n, false",
        "nnintattrib, true, false",
        "nnintattrib, false, false",
        "nnintattrib, 1, true",
        "nnintattrib, 0, true",
        "nnintattrib, -1, false",
        "nnintattrib, 2147483647, true",
        "nnintattrib, -2147483648, false",
        "nnintattrib, 9223372036854775807, false",
        "nnintattrib, -9223372036854775808, false",
        "nnintattrib, 3.14, false",
        "nnintattrib, -2.72, false",

        "longattrib, testvalue, false",
        "longattrib, yes, false",
        "longattrib, no, false",
        "longattrib, y, false",
        "longattrib, n, false",
        "longattrib, true, false",
        "longattrib, false, false",
        "longattrib, 1, true",
        "longattrib, 0, true",
        "longattrib, -1, true",
        "longattrib, 2147483647, true",
        "longattrib, -2147483648, true",
        "longattrib, 9223372036854775807, true",
        "longattrib, -9223372036854775808, true",
        "longattrib, 3.14, false",
        "longattrib, -2.72, false",

        "nnlongattrib, testvalue, false",
        "nnlongattrib, yes, false",
        "nnlongattrib, no, false",
        "nnlongattrib, y, false",
        "nnlongattrib, n, false",
        "nnlongattrib, true, false",
        "nnlongattrib, false, false",
        "nnlongattrib, 1, true",
        "nnlongattrib, 0, true",
        "nnlongattrib, -1, false",
        "nnlongattrib, 2147483647, true",
        "nnlongattrib, -2147483648, false",
        "nnlongattrib, 9223372036854775807, true",
        "nnlongattrib, -9223372036854775808, false",
        "nnlongattrib, 3.14, false",
        "nnlongattrib, -2.72, false",

        "boolattrib, testvalue, false",
        "boolattrib, yes, false",
        "boolattrib, no, false",
        "boolattrib, y, false",
        "boolattrib, n, false",
        "boolattrib, true, true",
        "boolattrib, false, true",
        "boolattrib, 1, true",
        "boolattrib, 0, true",
        "boolattrib, -1, false",
        "boolattrib, 2147483647, false",
        "boolattrib, -2147483648, false",
        "boolattrib, 9223372036854775807, false",
        "boolattrib, -9223372036854775808, false",
        "boolattrib, 3.14, false",
        "boolattrib, -2.72, false"})
    public void testFactValidation(String key, String value, boolean shouldValidate) {
        this.config.setProperty(ConfigProperties.INTEGER_ATTRIBUTES, "intattrib");
        this.config.setProperty(ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES, "nnintattrib");
        this.config.setProperty(ConfigProperties.LONG_ATTRIBUTES, "longattrib");
        this.config.setProperty(ConfigProperties.NON_NEG_LONG_ATTRIBUTES, "nnlongattrib");
        this.config.setProperty(ConfigProperties.BOOLEAN_ATTRIBUTES, "boolattrib");

        AttributeValidator validator = new AttributeValidator(this.config, i18nProvider);

        boolean result;

        try {
            validator.validate(key, value);
            result = true;
        }
        catch (PropertyValidationException e) {
            result = false;
        }

        assertEquals(shouldValidate, result);
    }

}
