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

import java.util.HashSet;
import java.util.Set;

/**
 * Provides common behaviour shared by concrete configuration validator implementations.
 */
public abstract class AbstractConfigurationValidator implements ConfigurationValidator {

    private String key;
    private Set<String> failures = new HashSet<>();

    /**
     * Creates a new abstract configuration validator for the congfiguration key that is provided.
     *
     * @param key
     *  the key to the configuration that will be validated
     *
     * @throws IllegalArgumentException
     *  if the provided configuration key is null or blank
     */
    protected AbstractConfigurationValidator(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("configuration key is null or blank");
        }

        this.key = key;
    }

    /**
     * @return
     *  the configuration key for the configuration that is being validated
     */
    public String getKey() {
        return key;
    }

    /**
     * Adds the failure message to the existing set of failure messages
     *
     * @param failure
     *  failure message to add
     *
     * @throws IllegalArgumentException
     *  if the provided message is null or blank
     */
    void addFailure(String failure) {
        if (failure == null || failure.isBlank()) {
            throw new IllegalArgumentException("failure message is null or blank");
        }

        failures.add(failure);
    }

    /**
     * @return
     *  the failure messages for this configuration validator
     */
    Set<String> getFailures() {
        return failures;
    }
}
