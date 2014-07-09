/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.hibernate;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.validation.MessageInterpolator;

/**
 * CandlepinMessageInterpolator
 */
public class CandlepinMessageInterpolator implements MessageInterpolator {
    public static final Map<String, ValidationMessage> MESSAGES =
        new HashMap<String, ValidationMessage>() {
            {
                put("{javax.validation.constraints.AssertFalse.message}",
                    new ValidationMessage(I18n.marktr("must be false")));
                put("{javax.validation.constraints.AssertTrue.message}",
                    new ValidationMessage(I18n.marktr("must be true")));
                put("{javax.validation.constraints.DigitsMax.message}",
                    new ValidationMessage(I18n.marktr("must be less than or equal to {0}"),
                        "value"));
                put("{javax.validation.constraints.DigitsMin.message}",
                    new ValidationMessage(I18n.marktr("must be greater than or equal to {0}"),
                        "value"));
                put("{javax.validation.constraints.Digits.message}",
                    new ValidationMessage(I18n.marktr(
                        "numeric value out of bounds (<{integer} digits>" +
                        ".<{fraction} digits> expected)"),
                        "integer", "fraction"));
                put("{javax.validation.constraints.Future.message}",
                    new ValidationMessage(I18n.marktr("must be in the future")));
                put("{javax.validation.constraints.Max.message}",
                    new ValidationMessage(I18n.marktr("must be less than or equal to {0}"),
                        "value"));
                put("{javax.validation.constraints.Min.message}",
                    new ValidationMessage(I18n.marktr("must be greater than or equal to {0}"),
                        "value"));
                put("{javax.validation.constraints.NotNull.message}",
                    new ValidationMessage(I18n.marktr("may not be null")));
                put("{javax.validation.constraints.Null.message}",
                    new ValidationMessage(I18n.marktr("must be null")));
                put("{javax.validation.constraints.Past.message}",
                    new ValidationMessage(I18n.marktr("must be in the past")));
                put("{javax.validation.constraints.Pattern.message}",
                    new ValidationMessage(I18n.marktr("must match ''{regexp}''"),
                        "regexp"));
                put("{javax.validation.constraints.Size.message}",
                    new ValidationMessage(I18n.marktr("size must be between {0} and {1}"),
                        "min", "max"));
                put("{org.hibernate.validator.constraints.CreditCardNumber.message}",
                    new ValidationMessage(I18n.marktr("invalid credit card number")));
                put("{org.hibernate.validator.constraints.Email.message}",
                    new ValidationMessage(I18n.marktr("not a well-formed email address")));
                put("{org.hibernate.validator.constraints.Length.message}",
                    new ValidationMessage(I18n.marktr("size must be between {0} and {1}"),
                        "min", "max"));
                put("{org.hibernate.validator.constraints.NotBlank.message}",
                    new ValidationMessage(I18n.marktr("may not be empty")));
                put("{org.hibernate.validator.constraints.NotEmpty.message}",
                    new ValidationMessage(I18n.marktr("may not be empty")));
                put("{org.hibernate.validator.constraints.Range.message}",
                    new ValidationMessage(I18n.marktr("must be between {min} and {max}"),
                        "min", "max"));
                put("{org.hibernate.validator.constraints.SafeHtml.message}",
                    new ValidationMessage(I18n.marktr("may have unsafe html content")));
                put("{org.hibernate.validator.constraints.ScriptAssert.message}",
                    new ValidationMessage(I18n.marktr("script expression ''{0}'' didn't evaluate to true"),
                        "script"));
                put("{org.hibernate.validator.constraints.URL.message}",
                    new ValidationMessage(I18n.marktr("must be a valid URL")));
            }
        };

    /**
     * Validation message
     * ValidationMessage
     */
    public static class ValidationMessage {
        private String message;

        private List<String> paramNames;

        public ValidationMessage(String message, String... paramNames) {
            this.message = message;
            this.paramNames = Collections.unmodifiableList(Arrays.asList(paramNames));
        }

        public String getMessage() {
            return message;
        }


        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getParamNames() {
            return paramNames;
        }

        public void setParamNames(List<String> paramNames) {
            this.paramNames = paramNames;
        }
    }

    // You must use the Provider here otherwise you will end up with a stale
    // I18n object!
    private Provider<I18n> i18nProvider;

    @Inject
    public CandlepinMessageInterpolator(Provider<I18n> i18nProvider) {
        this.i18nProvider = i18nProvider;
    }

    @Override
    public String interpolate(String message, Context context) {
        return interpolate(message, context, i18nProvider.get().getLocale());
    }

    @Override
    public String interpolate(String messageTemplate, Context context,
        Locale locale) {
        Map<String, Object> attrs = context.getConstraintDescriptor().getAttributes();

        ValidationMessage validationMessage = MESSAGES.get(messageTemplate);
        List<Object> paramList = new ArrayList<Object>();
        for (String param : validationMessage.getParamNames()) {
            if (attrs.containsKey(param)) {
                paramList.add(attrs.get(param));
            }
            else {
                paramList.add(param);
            }
        }

        return i18nProvider.get().tr(validationMessage.getMessage(), paramList.toArray());
    }
}
