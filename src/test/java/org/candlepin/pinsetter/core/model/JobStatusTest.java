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
package org.candlepin.pinsetter.core.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.lang.RandomStringUtils;
import org.candlepin.auth.Principal;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;


/**
 * JobStatusTest
 */
public class JobStatusTest {

    private JobStatus status;

    @Before
    public void init() {
        JobDetail detail = mock(JobDetail.class);
        JobDataMap map = new JobDataMap();
        // put in some default values to the map for basic tests
        map.put(JobStatus.TARGET_ID, "10");
        when(detail.getKey()).thenReturn(JobKey.jobKey("name", "group"));
        when(detail.getJobDataMap()).thenReturn(map);
        status = new JobStatus(detail);
    }

    @Test
    public void handleNullResult() {
        status.setResult(null);
        assertEquals(null, status.getResult());
    }

    @Test
    public void handleTooLongResult() {
        String longstr = RandomStringUtils.randomAlphanumeric(300);
        status.setResult(longstr);
        assertEquals(longstr.substring(0, JobStatus.RESULT_COL_LENGTH), status.getResult());
    }

    @Test
    public void handleShortResult() {
        status.setResult("shorty");
        assertEquals("shorty", status.getResult());
    }

    @Test
    public void handleExactResult() {
        // pass in a string of EXACTLY column size characters
        String longstr = RandomStringUtils.randomAlphanumeric(
            JobStatus.RESULT_COL_LENGTH);
        status.setResult(longstr);
        assertEquals(longstr.substring(0, JobStatus.RESULT_COL_LENGTH),
            status.getResult());
    }

    @Test
    public void statusPath() {
        assertEquals("/jobs/name", status.getStatusPath());
    }

    @Test
    public void group() {
        assertEquals("group", status.getGroup());
    }

    @Test
    public void id() {
        assertEquals("name", status.getId());
    }

    @Test
    public void finishTimeBeforeUpdate() {
        assertEquals(null, status.getFinishTime());
    }

    @Test
    public void startTimeBeforeUpdate() {
        assertEquals(null, status.getFinishTime());
    }

    @Test
    public void initialState() {
        assertEquals(JobStatus.JobState.CREATED, status.getState());
    }

    @Test
    public void setState() {
        status.setState(JobStatus.JobState.CANCELED);
        assertEquals(JobStatus.JobState.CANCELED, status.getState());
    }

    @Test
    public void nonNullTargetType() {
        JobDetail detail = mock(JobDetail.class);
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        when(detail.getKey()).thenReturn(JobKey.jobKey("name", "group"));
        when(detail.getJobDataMap()).thenReturn(map);
        status = new JobStatus(detail);

        assertEquals("owner", status.getTargetType());
    }

    @Test
    public void nullTargetType() {
        assertEquals(null, status.getTargetType());
    }

    @Test
    public void targetId() {
        assertEquals("10", status.getTargetId());
    }

    @Test
    public void principalNameUnknown() {
        assertEquals("unknown", status.getPrincipalName());
    }

    @Test
    public void knownPrincipalName() {
        Principal p = mock(Principal.class);
        when(p.getPrincipalName()).thenReturn("admin");
        JobDetail detail = mock(JobDetail.class);
        JobDataMap map = new JobDataMap();
        map.put(PinsetterJobListener.PRINCIPAL_KEY, p);
        when(detail.getKey()).thenReturn(JobKey.jobKey("name", "group"));
        when(detail.getJobDataMap()).thenReturn(map);

        status = new JobStatus(detail);
        assertEquals("admin", status.getPrincipalName());
    }
}
