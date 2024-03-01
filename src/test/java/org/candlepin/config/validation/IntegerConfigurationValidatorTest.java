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

@ExtendWith(MockitoExtension.class)
public class IntegerConfigurationValidatorTest {

    @Mock
    protected Configuration mockConfig;

    @ParameterizedTest
    @NullAndEmptySource
    public void testConstuctorWithInvalidKey(String key) {
        assertThrows(IllegalArgumentException.class, () -> new IntegerConfigurationValidator(key));
    }

    @Test
    public void testValidateWithNonIntegerConfigurationValue() {
        String key = TestUtil.randomString();
        doReturn(TestUtil.randomString()).when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateWithNullConfigurationValue() {
        String key = TestUtil.randomString();

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateWithValueGreaterThanMinCriteria() {
        String key = TestUtil.randomString();
        doReturn("100").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .min(1);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateWithValueLessThanMinCriteria() {
        String key = TestUtil.randomString();
        doReturn("1").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .min(100);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateWithValueEqualToMinCriteria() {
        String key = TestUtil.randomString();
        doReturn("100").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .min(100);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateWithValueGreaterThanMaxCriteria() {
        String key = TestUtil.randomString();
        doReturn("100").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .max(1);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateWithValueLessThanMaxCriteria() {
        String key = TestUtil.randomString();
        doReturn("1").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .max(100);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateWithValueEqualToMaxCriteria() {
        String key = TestUtil.randomString();
        doReturn("100").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .max(100);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateRangeWithValueGreaterThanRange() {
        String key = TestUtil.randomString();
        doReturn("100").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .range(1, 10);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateRangeWithValueLessThanRange() {
        String key = TestUtil.randomString();
        doReturn("0").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .range(1, 10);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateRangeWithValueInsideRange() {
        String key = TestUtil.randomString();
        doReturn("5").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .range(1, 10);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateGreaterThanCriteriaWithInvalidKey(String otherKey) {
        String key = TestUtil.randomString();

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key);

        assertThrows(IllegalArgumentException.class, () -> validator.greaterThan(otherKey));
    }

    @Test
    public void testValidateGreaterThanCriteriaWithMissingOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .greaterThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateGreaterThanCriteriaWithLargerOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("1000").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .greaterThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateGreaterThanCriteriaWithSmallerOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("1").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .greaterThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateGreaterThanCriteriaWithEqualOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("5").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .greaterThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateMultipleGreaterThanCriteria() {
        String key = TestUtil.randomString();
        String otherKey1 = TestUtil.randomString();
        String otherKey2 = TestUtil.randomString();
        String otherKey3 = TestUtil.randomString();
        String otherKey4 = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("10").when(mockConfig).getString(otherKey1);
        doReturn("20").when(mockConfig).getString(otherKey2);
        doReturn("1").when(mockConfig).getString(otherKey3);
        doReturn("5").when(mockConfig).getString(otherKey4);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .greaterThan(otherKey1)
            .greaterThan(otherKey2)
            .greaterThan(otherKey3)
            .greaterThan(otherKey4);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .hasSize(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testValidateLessThanCriteriaWithInvalidKey(String otherKey) {
        String key = TestUtil.randomString();

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key);

        assertThrows(IllegalArgumentException.class, () -> validator.lessThan(otherKey));
    }

    @Test
    public void testValidateLessThanCriteriaWithMissingOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .lessThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateLessThanCriteriaWithLargerOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("1000").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .lessThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateLessThanCriteriaWithSmallerOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("1").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .lessThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testValidateLessThanCriteriaWithEqualOtherValue() {
        String key = TestUtil.randomString();
        String otherKey = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("5").when(mockConfig).getString(otherKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .lessThan(otherKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testValidateMultipleLessThanCriteria() {
        String key = TestUtil.randomString();
        String otherKey1 = TestUtil.randomString();
        String otherKey2 = TestUtil.randomString();
        String otherKey3 = TestUtil.randomString();
        String otherKey4 = TestUtil.randomString();

        doReturn("5").when(mockConfig).getString(key);
        doReturn("10").when(mockConfig).getString(otherKey1);
        doReturn("2").when(mockConfig).getString(otherKey2);
        doReturn("1").when(mockConfig).getString(otherKey3);
        doReturn("5").when(mockConfig).getString(otherKey4);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .lessThan(otherKey1)
            .lessThan(otherKey2)
            .lessThan(otherKey3)
            .lessThan(otherKey4);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .hasSize(2);
    }

    @Test
    public void testValidateWithMultipleFailures() {
        String key = TestUtil.randomString();
        String greaterThanKey = TestUtil.randomString();
        String lessThanKey = TestUtil.randomString();

        doReturn("100").when(mockConfig).getString(key);
        doReturn("1000").when(mockConfig).getString(greaterThanKey);
        doReturn("10").when(mockConfig).getString(lessThanKey);

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key)
            .range(1, 10)
            .greaterThan(greaterThanKey)
            .lessThan(lessThanKey);

        assertThat(validator.validate(mockConfig))
            .isNotNull()
            .hasSize(3);
    }
}

