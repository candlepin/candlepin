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

package org.candlepin.gutterball.report;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.guice.I18nProvider;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnap.commons.i18n.I18n;

import java.util.Arrays;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

@RunWith(JukitoRunner.class)
public class ParameterDescriptorTest {

    @Inject
    private HttpServletRequest mockReq;

    private ParameterDescriptor desc;

    @Before
    public void setUp() throws Exception {
        I18n i18n = new I18nProvider(mockReq).get();
        desc = new ParameterDescriptor(i18n, "test-name", "test-desc");
    }

    @Test
    public void testDefaults() {
        assertEquals("test-name", desc.getName());
        assertEquals("test-desc", desc.getDescription());
        assertFalse(desc.isMandatory());
        assertFalse(desc.isMultiValued());
    }

    @Test
    public void validatesMandatoryProperty() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(false);

        desc.mandatory();
        assertInvalidParameter(desc, params, "Required parameter.");
    }

    @Test
    public void ignoresNonMandatoryParameterWhenItDoesNotExist() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(false);
        assertValidParameter(desc, params);
    }

    @Test
    public void validatesIntegerValue() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("1a"));

        desc.mustBeInteger();
        assertInvalidParameter(desc, params, "Parameter must be an Integer value.");
    }

    @Test
    public void testValidInteger() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("1"));

        desc.mustBeInteger();
        assertValidParameter(desc, params);
    }

    @Test
    public void validatesMultipleIntegerValues() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("1", "2", "4s"));

        desc.mustBeInteger();
        assertInvalidParameter(desc, params, "Parameter must be an Integer value.");
    }

    @Test
    public void validatesDateFormat() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-nineteen19"));

        desc.mustBeDate("yyyy-MM-dd");
        assertInvalidParameter(
            desc, params, "Invalid date/time string: \"2014-04-nineteen19\". Accepted formats: [yyyy-MM-dd]"
        );
    }

    @Test
    public void testValidDateFormat() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-19"));

        desc.mustBeDate("yyyy-MM-dd");
        assertValidParameter(desc, params);
    }

    @Test
    public void validatesMultipleDateValues() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-19", "2014-04-20",
            "a2014-21-d04"));

        desc.mustBeDate("yyyy-MM-dd");
        assertInvalidParameter(
            desc, params, "Invalid date/time string: \"a2014-21-d04\". Accepted formats: [yyyy-MM-dd]"
        );
    }

    @Test
    public void validatesMultipleDateFormats() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-f19"));

        desc.mustBeDate(Arrays.asList("yyyy-MM-dd", "yyyy-MM-dd'T'HH"));
        assertInvalidParameter(desc, params,
            "Invalid date/time string: \"2014-04-f19\". Accepted formats: [yyyy-MM-dd, yyyy-MM-dd'T'HH]"
        );
    }

    @Test
    public void validatesDateFormatSingleCallPersistsMultipleValues() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-f19"));

        desc.mustBeDate(Arrays.asList("yyyy-MM-dd"));
        desc.mustBeDate(Arrays.asList("yyyy-MM-dd", "yyyy-MM-dd'T'HH"));
        assertInvalidParameter(desc, params,
            "Invalid date/time string: \"2014-04-f19\". Accepted formats: [yyyy-MM-dd, yyyy-MM-dd'T'HH]"
        );
    }

    @Test
    public void validatesDateFormatSingleCallPersists() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-f19"));

        desc.mustBeDate(Arrays.asList("yyyy-MM-dd", "yyyy-MM-dd'T'HH"));
        desc.mustBeDate("yyyy-MM-dd");

        assertInvalidParameter(desc, params,
            "Invalid date/time string: \"2014-04-f19\". Accepted formats: [yyyy-MM-dd]"
        );
    }

    @Test
    public void validatesMultipleDateFormatsWithMultipleValues() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("2014-04-19", "2015-10-12T12", "nope"));

        desc.mustBeDate(Arrays.asList("yyyy-MM-dd", "yyyy-MM-dd'T'HH"));
        assertInvalidParameter(desc, params,
            "Invalid date/time string: \"nope\". Accepted formats: [yyyy-MM-dd, yyyy-MM-dd'T'HH]"
        );
    }

    @Test
    public void validatesTimeZone() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("nope"));

        desc.mustBeTimeZone();
        assertInvalidParameter(
            desc,
            params,
            "Invalid time zone string. Time zones must be recognized time zone names " +
            "or offsets specified in the form of \"GMT[+-]HH:?MM\"."
        );
    }

    @Test
    public void validatesValidTimeZones() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList(
            "gmt",
            "america/chicago",
            "GMT-0600",
            "GMT+17:15"
        ));

        desc.mustBeTimeZone();
        assertValidParameter(desc, params);
    }

    @Test
    public void validatesMultipleTimeZones() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("gmt", "america/chicago", "nope"));

        desc.mustBeTimeZone();
        assertInvalidParameter(
            desc,
            params,
            "Invalid time zone string. Time zones must be recognized time zone names " +
            "or offsets specified in the form of \"GMT[+-]HH:?MM\"."
        );
    }

    @Test
    public void validateExtValidations() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.get(desc.getName())).thenReturn(Arrays.asList("tv1", "tv2", "test_value"));

        ParameterValidator validator = new ParameterValidator() {
            public void validate(ParameterDescriptor descriptor, String value) {
                if (value.length() < 5) {
                    return;
                }

                throw new ParameterValidationException(
                    desc.getName(),
                    String.format("Called with descriptor and value: %s, %s", descriptor.getName(), value)
                );
            }
        };

        desc.mustSatisfy(validator);
        assertInvalidParameter(desc, params,
            String.format("Called with descriptor and value: %s, %s", desc.getName(), "test_value")
        );
    }

    @Test
    public void validatesMustHaves() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.containsKey("a")).thenReturn(true);
        when(params.containsKey("b")).thenReturn(false);

        desc.mustHave("a", "b");
        assertInvalidParameter(desc, params, "Parameter must be used with b.");
    }

    @Test
    public void testValidMustHaves() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.containsKey("a")).thenReturn(true);
        when(params.containsKey("b")).thenReturn(true);

        desc.mustHave("a", "b");
        assertValidParameter(desc, params);
    }

    @Test
    public void validatesMustNotHaves() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.containsKey("a")).thenReturn(false);
        when(params.containsKey("b")).thenReturn(true);

        desc.mustNotHave("a", "b");
        assertInvalidParameter(desc, params, "Parameter must not be used with b.");
    }

    @Test
    public void testValidMustNotHaves() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey(desc.getName())).thenReturn(true);
        when(params.containsKey("a")).thenReturn(false);
        when(params.containsKey("b")).thenReturn(false);

        desc.mustNotHave("a", "b");
        assertValidParameter(desc, params);
    }


    private void assertValidParameter(ParameterDescriptor descriptor, MultivaluedMap<String, String> params) {
        descriptor.validate(params);
    }

    private void assertInvalidParameter(ParameterDescriptor descriptor,
            MultivaluedMap<String, String> params, String expectedMessage) {
        try {
            descriptor.validate(params);
            fail("Expected param validation error.");
        }
        catch (ParameterValidationException e) {
            assertEquals(descriptor.getName(), e.getParamName());
            assertEquals(descriptor.getName() + ": " + expectedMessage, e.getMessage());
        }
    }

}
