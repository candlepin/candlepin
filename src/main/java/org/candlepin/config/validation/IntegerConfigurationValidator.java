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

/**
 * Validator for configurations with expected integer values
 */
public class IntegerConfigurationValidator extends AbstractConfigurationValidator {

    private Integer min;
    private Integer max;
    private Set<String> lessThanKeys = new HashSet<>();
    private Set<String> greaterThanKeys = new HashSet<>();

    /**
     * Creates a new validator for configurations that have an integer value.
     *
     * @param key
     *  the key for configuration that will be validated
     */
    public IntegerConfigurationValidator(String key) {
        super(key);
    }

    /**
     * Creates a validation criteria that dictates that the configuration value must be greater than or equal
     * to the provided minimum value.
     *
     * @param min
     *  the minimum value this configuration's value can be
     *
     * @return
     *  a reference to this integer configuration validator
     */
    public IntegerConfigurationValidator min(int min) {
        this.min = min;
        return this;
    }

    /**
     * Creates a validation criteria that dictates that the configuration value must be less than or equal
     * to the provided maximum value.
     *
     * @param max
     *  the maximum value this configuration's value can be
     *
     * @return
     *  a reference to this integer configuration validator
     */
    public IntegerConfigurationValidator max(int max) {
        this.max = max;
        return this;
    }

    /**
     * Creates a validation criteria that dictates that the configuration value must be greater than or equal
     * to the provided minimum value and less than or equal to the provided maximum value.
     *
     * @param min
     *  the minimum value this configuration's value can be
     *
     * @param max
     *  the maximum value this configuration's value can be
     *
     * @return
     *  a reference to this integer configuration validator
     */
    public IntegerConfigurationValidator range(int min, int max) {
        this.min = min;
        this.max = max;
        return this;
    }

    /**
     * Generates validation criteria specifying that the configuration should be equal to or less than the
     * configuration value corresponding to provided key. If the provided configuration key corresponds to a
     * non-integer value, it results in a failure. Successive calls to this method append extra criteria to
     * this validator that must be satisfied.
     *
     * @param key
     *  a key used to retrieve a configuration value to be compared
     *
     * @return
     *  a reference to this integer configuration validator
     */
    public IntegerConfigurationValidator lessThan(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("null or blank key");
        }

        this.lessThanKeys.add(key);
        return this;
    }

    /**
     * Generates validation criteria specifying that the configuration should be equal to or greater than the
     * configuration value corresponding to provided key. If the provided configuration key corresponds to a
     * non-integer value, it results in a failure. Successive calls to this method append extra criteria to
     * this validator that must be satisfied.
     *
     * @param key
     *  the configuration key used to retrieve a value to be compared against this configuration
     *
     * @return
     *  a reference to this integer configuration validator
     */
    public IntegerConfigurationValidator greaterThan(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("null or blank key");
        }

        this.greaterThanKeys.add(key);
        return this;
    }

    @Override
    public Set<String> validate(Configuration config) {
        String key = getKey();
        Integer value = getInt(key, config);
        if (value == null) {
            return this.getFailures();
        }

        if (min != null && value < min) {
            this.addFailure("Configuration \"" + key + "\" must be larger than " + min);
        }

        if (max != null && value > max) {
            this.addFailure("Configuration \"" + key + "\" must be smaller than " + max);
        }

        validateLessThanConfigs(value, config);

        validateGreaterThanConfigs(value, config);

        return this.getFailures();
    }

    private void validateLessThanConfigs(Integer value, Configuration config) {
        if (value == null) {
            return;
        }

        if (!lessThanKeys.isEmpty()) {
            for (String lessThanKey : lessThanKeys) {
                Integer otherValue = getInt(lessThanKey, config);
                if (otherValue != null && value > otherValue) {
                    this.addFailure("Configuration \"" + getKey() +
                        "\" must be smaller than the value of \"" + lessThanKey + "\"");
                }
            }
        }
    }

    private void validateGreaterThanConfigs(Integer value, Configuration config) {
        if (value == null) {
            return;
        }

        if (!greaterThanKeys.isEmpty()) {
            for (String greaterThanKey : greaterThanKeys) {
                Integer otherValue = getInt(greaterThanKey, config);
                if (otherValue != null && value < otherValue) {
                    this.addFailure("Configuration \"" + getKey() +
                        "\" must be larger than the value of \"" + greaterThanKey + "\"");
                }
            }
        }
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
    private Integer getInt(String key, Configuration config) {
        String stringValue = config.getString(key);
        if (stringValue == null) {
            this.addFailure("Required configuration \"" + key + "\" does not have a value");
            return null;
        }

        try {
            return Integer.parseInt(stringValue);
        }
        catch (NumberFormatException e) {
            this.addFailure("Configuration \"" + key + "\" must have an integer value");
        }

        return null;
    }
}

