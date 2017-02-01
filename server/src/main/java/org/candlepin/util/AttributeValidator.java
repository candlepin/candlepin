/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Set;



/**
 * The AttributeValidator is a PropertyValidator implementation, configured to validate pool or
 * product attributes.
 */
public class AttributeValidator extends PropertyValidator {
    /** The maximum length of any fact key (name) or value */
    public static final int ATTRIBUTE_MAX_LENGTH = 255;

    @Inject
    public AttributeValidator(Configuration config, I18n i18n) {
        Set<String> attributes;

        // Add integer attributes...
        attributes = config.getSet(ConfigProperties.INTEGER_ATTRIBUTES, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.IntegerValidator(i18n, "attribute"));
            }
        }

        // Add non-negative integer attributes...
        attributes = config.getSet(ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key,
                    new PropertyValidator.NonNegativeIntegerValidator(i18n, "attribute"));
            }
        }

        // Add long attributes...
        attributes = config.getSet(ConfigProperties.LONG_ATTRIBUTES, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.LongValidator(i18n, "attribute"));
            }
        }

        // Add non-negative long attributes...
        attributes = config.getSet(ConfigProperties.NON_NEG_LONG_ATTRIBUTES, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.NonNegativeLongValidator(i18n, "attribute"));
            }
        }

        // Add boolean attributes...
        attributes = config.getSet(ConfigProperties.BOOLEAN_ATTRIBUTES, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.BooleanValidator(i18n, "attribute"));
            }
        }

        // Add global validators...
        this.globalValidators.add(
            new PropertyValidator.LengthValidator(i18n, "attribute", ATTRIBUTE_MAX_LENGTH));
    }

}
