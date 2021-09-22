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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.Consumer;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;

import java.util.Map;
import java.util.Set;

import javax.inject.Provider;
import javax.inject.Singleton;



/**
 * The FactValidator is a PropertyValidator implementation, configured to validate consumer facts.
 */
@Singleton
public class FactValidator extends PropertyValidator {
    /** The maximum length of any fact key (name) or value */
    public static final int FACT_MAX_LENGTH = 255;

    @Inject
    public FactValidator(Configuration config, Provider<I18n> i18nProvider) {
        Set<String> attributes;

        // Add integer attributes...
        attributes = config.getSet(ConfigProperties.INTEGER_FACTS, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.IntegerValidator(i18nProvider, "fact"));
            }
        }

        // Add non-negative integer attributes...
        attributes = config.getSet(ConfigProperties.NON_NEG_INTEGER_FACTS, null);
        if (attributes != null) {
            for (String key : attributes) {
                this.validators.put(key, new PropertyValidator.NonNegativeIntegerValidator(i18nProvider,
                    "fact"));
            }
        }

        // Add global validators...
        this.globalValidators.add(new PropertyValidator.LengthValidator(i18nProvider, "fact",
            FACT_MAX_LENGTH));
    }

    /**
     * Validates the facts associated with the given consumer. If any fact fails validation a
     * PropertyValidationException will be thrown.
     *
     * @param consumer
     *  The consumer containing the facts to validate
     */
    public void validate(Consumer consumer) {
        // Impl note:
        // Unlike the previous implementation, we are no longer attempting to "fix" anything here;
        // if it's broken at this point, we're in trouble, so we're going to throw an exception
        // instead of waiting for CP to die with a DB exception sometime in the very near future.
        //
        // Also, we're no longer using ConfigProperties.CONSUMER_FACTS_MATCHER at this point, as
        // it's something that belongs with the other input validation and filtering.

        Map<String, String> facts = consumer.getFacts();
        if (facts != null) {
            for (Map.Entry<String, String> fact : facts.entrySet()) {
                this.validate(fact.getKey(), fact.getValue());
            }
        }
    }

}
