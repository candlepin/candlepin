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

import org.candlepin.config.Configuration;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Set;

public class AbstractConfigurationValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    public void testConstructorWithInvalidKey(String key) {
        assertThrows(IllegalArgumentException.class, () -> new TestConfigurationValidator(key));
    }

    @Test
    public void testGetKey() {
        String expected = TestUtil.randomString();

        TestConfigurationValidator validator = new TestConfigurationValidator(expected);

        assertThat(validator.getKey())
            .isNotNull()
            .isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testAddFailuresWithInvalidMessage(String message) {
        TestConfigurationValidator validator = new TestConfigurationValidator(TestUtil.randomString());

        assertThrows(IllegalArgumentException.class, () -> validator.addFailure(message));
    }

    @Test
    public void testGetFailures() {
        String message1 = TestUtil.randomString();
        String message2 = TestUtil.randomString();

        TestConfigurationValidator validator = new TestConfigurationValidator(TestUtil.randomString());
        validator.addFailure(message1);
        validator.addFailure(message2);

        assertThat(validator.getFailures())
            .containsExactlyInAnyOrder(message1, message2);
    }

    public class TestConfigurationValidator extends AbstractConfigurationValidator {

        public TestConfigurationValidator(String key) {
            super(key);
        }

        @Override
        public Set<String> validate(Configuration config) {
            throw new UnsupportedOperationException("Unimplemented");
        }
    }
}

