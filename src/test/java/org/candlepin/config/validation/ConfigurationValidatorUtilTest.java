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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ConfigurationValidatorUtilTest {

    private DevConfig config;

    @BeforeEach
    public void init() {
        this.config = TestConfig.defaults();
    }

    @Test
    public void testValidateConfigurationsWithNullValidators() {
        assertThrows(IllegalArgumentException.class, () -> {
            ConfigurationValidatorUtil.validateConfigurations(null, config);
        });
    }

    @Test
    public void testValidateConfigurationsWithNullConfiguration() {
        List<ConfigurationValidator> validators = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () -> {
            ConfigurationValidatorUtil.validateConfigurations(validators, null);
        });
    }

    @Test
    public void testValidateConfigurations() {
        String key1 = TestUtil.randomString();
        String key2 = TestUtil.randomString();

        IntegerConfigurationValidator validator1 = new IntegerConfigurationValidator(key1)
            .min(0)
            .lessThan(ConfigProperties.PAGING_MAX_PAGE_SIZE);
        ConfigurationValidator validator2 = new IntegerConfigurationValidator(key2)
            .min(0);

        config.setProperty(key1, "100");
        config.setProperty(key2, "1000");

        List<ConfigurationValidator> validators = List.of(validator1, validator2);

        ConfigurationValidatorUtil.validateConfigurations(validators, config);

        // Assert no exceptions
    }

    @Test
    public void testValidateConfigurationsWithValidationException() {
        String key1 = TestUtil.randomString();
        String key2 = TestUtil.randomString();

        IntegerConfigurationValidator validator = new IntegerConfigurationValidator(key1)
            .min(0)
            .lessThan(key2);
        ConfigurationValidator invalidValidator = new IntegerConfigurationValidator(key2)
            .max(0);

        config.setProperty(key1, "100");
        config.setProperty(key2, "1000");

        assertThrows(RuntimeException.class, () -> {
            ConfigurationValidatorUtil.validateConfigurations(List.of(validator, invalidValidator), config);
        });
    }
}
