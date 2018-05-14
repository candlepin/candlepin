/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.util.PropertyValidator.Validator;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.inject.Provider;



/**
 * Test suite for the PropertyValidator class
 */
@RunWith(JUnitParamsRunner.class)
public class PropertyValidatorTest {

    private I18n i18n;
    private Provider<I18n> i18nProvider = () -> i18n;

    @Before
    public void init() {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    /**
     * Utility method for fetching validators
     */
    private Validator getValidator(Class<? extends Validator> validatorClass, Object... args) {
        Map<Class, Class> translator = new HashMap<>();
        translator.put(Boolean.class, Boolean.TYPE);
        translator.put(Character.class, Character.TYPE);
        translator.put(Byte.class, Byte.TYPE);
        translator.put(Short.class, Short.TYPE);
        translator.put(Integer.class, Integer.TYPE);
        translator.put(Long.class, Long.TYPE);
        translator.put(Float.class, Float.TYPE);
        translator.put(Double.class, Double.TYPE);
        translator.put(Void.class, Void.TYPE);
        translator.put(i18nProvider.getClass(), Provider.class);

        try {
            if (args != null && args.length > 0) {
                Class[] classes = new Class[args.length];

                for (int i = 0; i < args.length; ++i) {
                    Class base = args[i].getClass();
                    classes[i] = translator.containsKey(base) ? translator.get(base) : base;
                }

                Constructor<? extends Validator> constructor = validatorClass.getConstructor(classes);
                constructor.setAccessible(true);

                return constructor.newInstance(args);
            }
            else {
                Constructor<? extends Validator> constructor = validatorClass.getConstructor();
                constructor.setAccessible(true);

                return constructor.newInstance();
            }
        }
        catch (Exception e) {
            // Rethrow the exception as a RuntimeException so we don't need to pad all our
            // tests with try/catch blocks or throws declarations.
            throw new RuntimeException(e);
        }
    }

    @Test
    @Parameters({
        "5, key, value, true",
        "3, key, value, false",
        "5, longkey, value, false",
        "0, key, value, false"})
    public void testLengthValidator(int length, String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.LengthValidator.class,
            this.i18nProvider, "test", length);

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

    @Test(expected = RuntimeException.class)
    public void testLengthValidatorConfigurationFailure() {
        Validator validator = this.getValidator(PropertyValidator.LengthValidator.class,
            this.i18nProvider, "test", -1);
    }

    @Test
    @Parameters({
        "key, 50, true",
        "key, -50, true",
        "key, 2147483647, true",
        "key, -2147483648, true",
        "key, string, false",
        "key, two, false",
        "key, 3.14, false",
        "key, -2.72, false"})
    public void testIntegerValidator(String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.IntegerValidator.class, this.i18nProvider,
            "test");

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

    @Test
    @Parameters({
        "key, 50, true",
        "key, -50, false",
        "key, 2147483647, true",
        "key, -2147483648, false",
        "key, string, false",
        "key, two, false",
        "key, 3.14, false",
        "key, -2.72, false"})
    public void testNonNegativeIntegerValidator(String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.NonNegativeIntegerValidator.class,
            this.i18nProvider, "test");

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

    @Test
    @Parameters({
        "key, 50, true",
        "key, -50, true",
        "key, 9223372036854775807, true",
        "key, -9223372036854775808, true",
        "key, string, false",
        "key, two, false",
        "key, 3.14, false",
        "key, -2.72, false"})
    public void testLongValidator(String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.LongValidator.class, this.i18nProvider,
            "test");

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

    @Test
    @Parameters({
        "key, 50, true",
        "key, -50, false",
        "key, 9223372036854775807, true",
        "key, -9223372036854775808, false",
        "key, string, false",
        "key, two, false",
        "key, 3.14, false",
        "key, -2.72, false"})
    public void testNonNegativeLongValidator(String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.NonNegativeLongValidator.class,
            this.i18nProvider, "test");

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

    @Test
    @Parameters({
        "key, true, true",
        "key, false, true",
        "key, 1, true",
        "key, 0, true",
        "key, yes, false",
        "key, no, false",
        "key, y, false",
        "key, n, false"})
    public void testBooleanValidator(String key, String value, boolean shouldValidate) {
        Validator validator = this.getValidator(PropertyValidator.BooleanValidator.class,
            this.i18nProvider, "test");

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
