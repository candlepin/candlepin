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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;

import org.xnap.commons.i18n.I18n;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;



/**
 * The AttributeValidator is a PropertyValidator implementation, configured to validate pool or
 * product attributes.
 */
@Singleton
public class AttributeValidator extends PropertyValidator {
    /** The maximum length of any fact key (name) or value */
    public static final int ATTRIBUTE_MAX_LENGTH = 255;

    @Inject
    public AttributeValidator(Configuration config, Provider<I18n> i18nProvider) {
        // Add integer attributes...
        for (String key : config.getSet(ConfigProperties.INTEGER_ATTRIBUTES)) {
            this.validators.put(key, new PropertyValidator.IntegerValidator(i18nProvider, "attribute"));
        }

        // Add non-negative integer attributes...
        for (String key : config.getSet(ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES)) {
            this.validators
                .put(key, new PropertyValidator.NonNegativeIntegerValidator(i18nProvider, "attribute"));
        }

        // Add long attributes...
        for (String key : config.getSet(ConfigProperties.LONG_ATTRIBUTES)) {
            this.validators.put(key, new PropertyValidator.LongValidator(i18nProvider, "attribute"));
        }

        // Add non-negative long attributes...
        for (String key : config.getSet(ConfigProperties.NON_NEG_LONG_ATTRIBUTES)) {
            this.validators.put(key, new PropertyValidator.NonNegativeLongValidator(i18nProvider,
                "attribute"));
        }

        // Add boolean attributes...
        for (String key : config.getSet(ConfigProperties.BOOLEAN_ATTRIBUTES)) {
            this.validators.put(key, new PropertyValidator.BooleanValidator(i18nProvider, "attribute"));
        }

        // Add global validators...
        this.globalValidators
            .add(new PropertyValidator.LengthValidator(i18nProvider, "attribute", ATTRIBUTE_MAX_LENGTH));
    }

}
