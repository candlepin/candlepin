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

import org.candlepin.config.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for validating configurations
 */
public final class ConfigurationValidatorUtil {

    private static Logger log = LoggerFactory.getLogger(ConfigurationValidatorUtil.class);

    private ConfigurationValidatorUtil() {
        // Intentionally left blank
    }

    /**
     * Validates configurations found in {@link Configuration} based on criteria in the provided
     * {@link ConfigurationValidator}s.
     *
     * @param validators
     *  {@link ConfigurationValidator}s used to validate configurations
     *
     * @param config
     *  used to retrieve the configuration values based on configuration keys
     *
     * @throws IllegalArgumentException
     *  if the provided validators or the configuration is null
     *
     * @throws RuntimeException
     *  if there is an invalid configuration
     */
    public static void validateConfigurations(Collection<ConfigurationValidator> validators,
        Configuration config) {
        if (validators == null) {
            throw new IllegalArgumentException("validators is null");
        }

        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }

        Set<String> failures =  validators.stream()
            .flatMap(validator -> validator.validate(config).stream())
            .collect(Collectors.toSet());

        if (!failures.isEmpty()) {
            failures.forEach(failure -> log.error(failure));

            throw new RuntimeException("Invalid configurations");
        }
    }
}

