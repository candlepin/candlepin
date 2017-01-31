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
package org.candlepin.util;

import static org.junit.Assert.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;



/**
 * Test suite for the FactValidator class
 */
@RunWith(JUnitParamsRunner.class)
public class FactValidatorTest {

    private static I18n i18n = I18nFactory.getI18n(FactValidatorTest.class, Locale.US, I18nFactory.FALLBACK);
    private Configuration config;

    @Before
    public void init() {
        this.config = new CandlepinCommonTestConfig();
    }

    @Test(expected = PropertyValidationException.class)
    @Parameters({"true", "false", "1", "0", "-1", "string value"})
    public void testLongFactKeyFailure(String value) {
        StringBuilder builder = new StringBuilder(FactValidator.FACT_MAX_LENGTH + 2);
        for (int i = 0; i < FactValidator.FACT_MAX_LENGTH + 1; ++i) {
            builder.append(i % 10);
        }

        FactValidator validator = new FactValidator(this.config, i18n);
        validator.validate(builder.toString(), value);
    }

    @Test(expected = PropertyValidationException.class)
    public void testLongFactValueFailure() {
        StringBuilder builder = new StringBuilder(FactValidator.FACT_MAX_LENGTH + 2);
        for (int i = 0; i < FactValidator.FACT_MAX_LENGTH + 1; ++i) {
            builder.append(i % 10);
        }

        FactValidator validator = new FactValidator(this.config, i18n);
        validator.validate("key", builder.toString());
    }

    @Test
    @Parameters({
        "testfact, testvalue, true",
        "testfact, yes, true",
        "testfact, no, true",
        "testfact, y, true",
        "testfact, n, true",
        "testfact, true, true",
        "testfact, false, true",
        "testfact, 1, true",
        "testfact, 0, true",
        "testfact, -1, true",
        "testfact, 2147483647, true",
        "testfact, -2147483648, true",
        "testfact, 9223372036854775807, true",
        "testfact, -9223372036854775808, true",
        "testfact, 3.14, true",
        "testfact, -2.72, true",

        "intfact, testvalue, false",
        "intfact, yes, false",
        "intfact, no, false",
        "intfact, y, false",
        "intfact, n, false",
        "intfact, true, false",
        "intfact, false, false",
        "intfact, 1, true",
        "intfact, 0, true",
        "intfact, -1, true",
        "intfact, 2147483647, true",
        "intfact, -2147483648, true",
        "intfact, 9223372036854775807, false",
        "intfact, -9223372036854775808, false",
        "intfact, 3.14, false",
        "intfact, -2.72, false",

        "nnintfact, testvalue, false",
        "nnintfact, yes, false",
        "nnintfact, no, false",
        "nnintfact, y, false",
        "nnintfact, n, false",
        "nnintfact, true, false",
        "nnintfact, false, false",
        "nnintfact, 1, true",
        "nnintfact, 0, true",
        "nnintfact, -1, false",
        "nnintfact, 2147483647, true",
        "nnintfact, -2147483648, false",
        "nnintfact, 9223372036854775807, false",
        "nnintfact, -9223372036854775808, false",
        "nnintfact, 3.14, false",
        "nnintfact, -2.72, false"})
    public void testFactValidation(String key, String value, boolean shouldValidate) {
        this.config.setProperty(ConfigProperties.INTEGER_FACTS, "fact1,intfact,fact2");
        this.config.setProperty(ConfigProperties.NON_NEG_INTEGER_FACTS, "nnintfact");

        FactValidator validator = new FactValidator(this.config, i18n);

        boolean result;

        try {
            validator.validate(key, value);
            result = true;
        }
        catch (PropertyValidationException e) {
            result = false;
        }

        assertEquals(shouldValidate, result);
    }

}
