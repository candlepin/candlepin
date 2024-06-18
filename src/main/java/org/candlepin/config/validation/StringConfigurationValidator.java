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

import java.util.HashSet;
import java.util.Set;

public class StringConfigurationValidator extends AbstractConfigurationValidator {

    private Set<String> allowedValues = new HashSet<>();

    /**
     * Creates a new validator for configurations that have a string value.
     *
     * @param key
     *  the key for configuration that will be validated
     */
    public StringConfigurationValidator(String key) {
        super(key);
    }

    /**
     * Creates a validation criteria that dictates that the configuration value must be one of
     * the allowed values.
     *
     * @param values
     *  the allowed values for this configuration
     *
     * @return
     *  a reference to this string configuration validator
     */
    public StringConfigurationValidator allowedValues(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("allowedValues cannot be null or empty");
        }

        this.allowedValues.addAll(values);
        return this;
    }

    @Override
    public Set<String> validate(Configuration config) {
        String key = getKey();
        String value = getString(key, config);
        if (value == null) {
            return this.getFailures();
        }

        if (!allowedValues.isEmpty() && !allowedValues.contains(value)) {
            this.addFailure(
                "Configuration \"" + key + "\" must be one of the allowed values: " + allowedValues);
        }

        return this.getFailures();
    }

    /**
     * Retrieves the configuration value that corresponds to the provided configuration key. Failure to
     * retrieve the value will result in a null being returned and detailed failure messages being added to
     * the failure message list.
     *
     * @param key
     *  the configuration key used to retrieved the configuration value
     *
     * @param config
     *  used to retrieve the configuration value
     *
     * @return
     *  the configuration value or null if unable to retrieve the value
     */
    private String getString(String key, Configuration config) {
        String stringValue = config.getString(key);
        if (stringValue == null) {
            this.addFailure("Required configuration \"" + key + "\" does not have a value");
            return null;
        }

        return stringValue;
    }
}
