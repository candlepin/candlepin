/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.config.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import org.candlepin.config.Configuration;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class StringConfigurationValidatorTest {

    @Mock
    protected Configuration mockConfig;

    @ParameterizedTest
    @NullAndEmptySource
    public void testConstuctorWithInvalidKey(String key) {
        assertThrows(IllegalArgumentException.class, () -> new StringConfigurationValidator(key));
    }

    @Test
    public void testValidateWithAllowedValue() {
        String key = TestUtil.randomString();
        String value = "allowedValue";

        doReturn(value).when(mockConfig).getString(key);

        StringConfigurationValidator validator = new StringConfigurationValidator(key)
            .allowedValues(Set.of(value));

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateWithDisallowedValue() {
        String key = TestUtil.randomString();
        String value = "disallowedValue";

        doReturn(value).when(mockConfig).getString(key);

        Set<String> allowedValues = new HashSet<>();
        allowedValues.add("allowedValue");

        StringConfigurationValidator validator = new StringConfigurationValidator(key)
            .allowedValues(allowedValues);

        assertThat(validator.validate(mockConfig)).isNotNull()
            .singleElement()
            .isEqualTo("Configuration \"" + key + "\" must be one of the allowed values: " + allowedValues);
    }

    @Test
    public void testValidateWithNoValue() {
        String key = TestUtil.randomString();

        doReturn(null).when(mockConfig).getString(key);

        Set<String> allowedValues = new HashSet<>();
        allowedValues.add("allowedValue");

        StringConfigurationValidator validator = new StringConfigurationValidator(key)
            .allowedValues(allowedValues);

        assertThat(validator.validate(mockConfig)).isNotNull()
            .singleElement()
            .isEqualTo("Required configuration \"" + key + "\" does not have a value");
    }

    @Test
    public void testAllowedValuesWithNullValues() {
        String key = TestUtil.randomString();
        StringConfigurationValidator validator = new StringConfigurationValidator(key);
        assertThrows(IllegalArgumentException.class, () -> validator.allowedValues(null));
    }

    @Test
    public void testAllowedValuesWithEmptyValues() {
        String key = TestUtil.randomString();
        StringConfigurationValidator validator = new StringConfigurationValidator(key);
        assertThrows(IllegalArgumentException.class, () -> validator.allowedValues(Collections.emptySet()));
    }
}
