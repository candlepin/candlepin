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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.gutterball.EmbeddedMongoRule;
import org.candlepin.gutterball.MongoJsonDataImporter;
import org.candlepin.gutterball.curator.ComplianceDataCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.mongodb.DBObject;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

@RunWith(JukitoRunner.class)
public class ConsumerStatusReportTest {


    private static final String TEST_START_DATE = "2014-08-15T00:00:00.000+0000";
    private static final String TEST_END_DATE = "2014-08-16T00:00:00.000+0000";

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    public static EmbeddedMongoRule serverRule = new EmbeddedMongoRule();

    @Inject
    private HttpServletRequest mockReq;


    private ComplianceDataCurator complianceDataCurator;
    private ConsumerStatusReport report;

    @BeforeClass
    public static void setupData() throws Exception {
        MongoJsonDataImporter importer = new MongoJsonDataImporter(serverRule.getMongoConnection());
        importer.importFile(ComplianceDataCurator.COLLECTION, "compliance_snapshot_data.json");
    }

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);
        complianceDataCurator = new ComplianceDataCurator(serverRule.getMongoConnection());
        report = new ConsumerStatusReport(i18nProvider, complianceDataCurator);
    }

    @Test
    public void startDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(new ArrayList<String>());
        when(params.containsKey("start_date")).thenReturn(true);

        validateParams(params, "hours", "Can not be used with start_date parameter.");

    }

    @Test
    public void endDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(new ArrayList<String>());
        when(params.containsKey("end_date")).thenReturn(true);

        validateParams(params, "hours", "Can not be used with end_date parameter.");
    }

    @Test
    public void hoursParamMustBeAnInteger() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(Arrays.asList("24a"));

        validateParams(params, "hours", "Parameter must be an Integer value");
    }

    @Test
    public void endDateMustBeSpecifiedWithStartDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList(TEST_START_DATE));
        when(params.containsKey("end_date")).thenReturn(false);

        validateParams(params, "start_date", "Parameter must be used with end_date.");
    }

    @Test
    public void startDateMustBeSpecifiedWithEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList(TEST_END_DATE));

        validateParams(params, "end_date", "Parameter must be used with start_date.");
    }

    @Test
    public void testDateFormatValidatedOnStartDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "start_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATE_FORMAT);
    }

    @Test
    public void testDateFormatValidatedOnEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2010-18-4"));

        validateParams(params, "end_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATE_FORMAT);
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

    @Test
    public void testGetStartEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDate = TEST_START_DATE;
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDate));

        String endDate = TEST_END_DATE;
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDate));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(9, results.size());
    }

    @Test
    public void testGetStartEndDateSameDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String targetDate = "2014-08-15T14:09:05.572+0000";
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(targetDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(targetDate));

        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(targetDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(targetDate));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());

        DBObject result = results.get(0);
        DBObject consumer = (DBObject) result.get("consumer");
        assertEquals("fa35a697-beec-4afe-b105-5bda7c29c8c8", consumer.get("uuid"));
    }

    @Test
    public void testGetByOwner() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDate = TEST_START_DATE;
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDate));

        String endDate = TEST_END_DATE;
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDate));

        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList("donaldduck"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(4, results.size());
        for (DBObject r : results) {
            DBObject consumer = (DBObject) r.get("consumer");
            DBObject owner = (DBObject) consumer.get("owner");
            assertEquals("donaldduck", owner.get("key"));
        }
    }

    @Test
    public void testGetByConsumerUuid() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDate = TEST_START_DATE;
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDate));

        String endDate = TEST_END_DATE;
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDate));

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c5b87d1a-1b9f-408b-a6ac-be3bf74c46c4"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject row = results.get(0);
        DBObject consumer = (DBObject) row.get("consumer");
        assertEquals("c5b87d1a-1b9f-408b-a6ac-be3bf74c46c4", consumer.get("uuid"));
    }

    @Test
    public void testGetByStatus() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDate = TEST_START_DATE;
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDate));

        String endDate = TEST_END_DATE;
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDate));

        when(params.containsKey("status")).thenReturn(true);
        when(params.get("status")).thenReturn(Arrays.asList("valid"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(5, results.size());
        for (DBObject r : results) {
            DBObject status = (DBObject) r.get("status");
            assertEquals("valid", status.get("status"));
        }
    }

    // TODO Test no date range or hours.
}
