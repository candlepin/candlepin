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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

@RunWith(JukitoRunner.class)
public class ConsumerStatusReportTest {

    @Inject
    private HttpServletRequest mockReq;

    private ConsumerStatusReport report;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);
        report = new ConsumerStatusReport(i18nProvider);
    }

    @Test
    public void startDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.containsKey("start_date")).thenReturn(true);

        validateParams(params, "hours", "Can not be used with start_date or end_date parameters");

    }

    @Test
    public void endDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.containsKey("end_date")).thenReturn(true);

        validateParams(params, "hours", "Can not be used with start_date or end_date parameters");
    }

    @Test
    public void hoursParamMustBeAnInteger() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.getFirst("hours")).thenReturn("a");

        validateParams(params, "hours", "Parameter must be an Integer value");
    }

    @Test
    public void endDateMustBeSpecifiedWithStartDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(false);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.containsKey("end_date")).thenReturn(false);

        validateParams(params, "end_date", "Missing required parameter. Must be used with start_date");
    }

    @Test
    public void startDateMustBeSpecifiedWithEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(false);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(true);

        validateParams(params, "start_date", "Missing required parameter. Must be used with end_date");
    }

    private void validateParams(MultivaluedMap<String, String> params, String expectedParam,
            String expectedMessage) {
        try {
            report.validateParameters(params);
            fail("Expected param validation error.");
        }
        catch (ParameterValidationException e) {
            assertEquals(expectedParam, e.getParamName());
            assertEquals(expectedParam + ": " + expectedMessage, e.getMessage());
        }
    }

    // TODO: Test the run method once hooked up.
}
