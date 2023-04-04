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
package org.candlepin.util;

import org.xnap.commons.i18n.I18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;



/**
 * The PropertyValidator class provides a simple framework for determining whether or not a given
 * value is valid for a specified property. The abstraction of this logic allows the logic to be
 * reused for multiple classes of property (i.e. attributes, facts, etc.).
 */
public abstract class PropertyValidator {

    /**
     * Mini-class for performing validation of a class of values
     */
    protected abstract static class Validator {
        protected Provider<I18n> i18nProvider;
        protected String propertyType;

        public Validator(Provider<I18n> i18nProvider, String propertyType) {
            if (i18nProvider == null) {
                throw new IllegalArgumentException("i18n is null");
            }

            if (propertyType == null) {
                throw new IllegalArgumentException("propertyType is null");
            }

            this.i18nProvider = i18nProvider;
            this.propertyType = propertyType;
        }

        public abstract void validate(String key, String value);
    }

    /**
     * Validator verifying the key or value are small enough to fit in the database
     */
    protected static class LengthValidator extends Validator {
        private int length;

        public LengthValidator(Provider<I18n> i18nProvider, String propertyType, int length) {
            super(i18nProvider, propertyType);

            if (length < 0) {
                throw new IllegalArgumentException("length is a non-positive value");
            }

            this.length = length;
        }

        public void validate(String key, String value) {
            if (key.length() > this.length) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} name \"{1}\" must not exceed {2} characters in length",
                    this.propertyType, key, this.length
                ));
            }

            if (value != null && value.length() > this.length) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The value for {0} \"{1}\" must not exceed {2} characters in length",
                    this.propertyType, key, this.length
                ));
            }
        }
    }

    /**
     * Validator providing validation for integer properties
     */
    protected static class IntegerValidator extends Validator {
        public IntegerValidator(Provider<I18n> i18nProvider, String propertyType) {
            super(i18nProvider, propertyType);
        }

        public void validate(String key, String value) {
            try {
                Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} \"{1}\" must be an integer value", this.propertyType, key));
            }
        }
    }

    /**
     * Validator providing validation for non-negative properties
     */
    protected static class NonNegativeIntegerValidator extends Validator {
        public NonNegativeIntegerValidator(Provider<I18n> i18nProvider, String propertyType) {
            super(i18nProvider, propertyType);
        }

        public void validate(String key, String value) {
            try {
                int parsed = Integer.parseInt(value);

                if (parsed < 0) {
                    throw new PropertyValidationException(this.i18nProvider.get().tr(
                        "The {0} \"{1}\" must be a positive, integer value", this.propertyType, key));
                }
            }
            catch (NumberFormatException nfe) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} \"{1}\" must be a positive, integer value", this.propertyType, key));
            }
        }
    }

    /**
     * Validator providing validation for long-integer properties
     */
    protected static class LongValidator extends Validator {
        public LongValidator(Provider<I18n> i18nProvider, String propertyType) {
            super(i18nProvider, propertyType);
        }

        public void validate(String key, String value) {
            try {
                Long.parseLong(value);
            }
            catch (NumberFormatException nfe) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} \"{1}\" must be a long-integer value", this.propertyType, key));
            }
        }
    }

    /**
     * Validator providing validation for non-negative, long-integer properties
     */
    protected static class NonNegativeLongValidator extends Validator {
        public NonNegativeLongValidator(Provider<I18n> i18nProvider, String propertyType) {
            super(i18nProvider, propertyType);
        }

        public void validate(String key, String value) {
            try {
                long parsed = Long.parseLong(value);

                if (parsed < 0) {
                    throw new PropertyValidationException(this.i18nProvider.get().tr(
                        "The {0} \"{1}\" must be a positive, long-integer value", this.propertyType, key));
                }
            }
            catch (NumberFormatException nfe) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} \"{1}\" must be a positive, long-integer value", this.propertyType, key));
            }
        }
    }

    /**
     * Validator providing validation for Boolean properties
     */
    protected static class BooleanValidator extends Validator {
        private static final Set<String> BOOLEAN_VALUES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("true", "false", "1", "0")));

        public BooleanValidator(Provider<I18n> i18nProvider, String propertyType) {
            super(i18nProvider, propertyType);
        }

        public void validate(String key, String value) {
            if (!(BOOLEAN_VALUES.contains(value.trim().toLowerCase()))) {
                throw new PropertyValidationException(this.i18nProvider.get().tr(
                    "The {0} \"{1}\" must be a Boolean value", this.propertyType, key));
            }
        }
    }


    protected Map<String, Validator> validators;
    protected Set<Validator> globalValidators;

    /**
     * Creates a new PropertyValidator instance.
     */
    public PropertyValidator() {
        this.validators = new HashMap<>();
        this.globalValidators = new HashSet<>();

        // Validators will be added by subclasses as necessary
    }

    /**
     * Checks whether the value given for the specified key is valid.
     *
     * @param key
     *  The key (property name) to validate
     *
     * @param value
     *  The value to validate
     *
     * @throws IllegalArgumentException
     *  if key is null
     *
     * #throws PropertyValidationException
     *  if the given property has an illegal value
     */
    // TODO: Fix the Javadoc above once we can workaround/fix the checkstyle issue that prevents
    // it from finding the PropertyValidationException.
    public void validate(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        for (Validator validator : this.globalValidators) {
            if (validator != null) {
                validator.validate(key, value);
            }
        }

        if (value != null && !value.isEmpty()) {
            Validator validator = this.validators.get(key);

            if (validator != null) {
                validator.validate(key, value);
            }
        }
    }

}
