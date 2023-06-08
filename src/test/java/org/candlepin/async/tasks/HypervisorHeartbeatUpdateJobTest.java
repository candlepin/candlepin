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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;


@ExtendWith(MockitoExtension.class)
public class HypervisorHeartbeatUpdateJobTest {

    @Mock protected ConsumerCurator mockConsumerCurator;

    private HypervisorHeartbeatUpdateJob testJob;

    @BeforeEach
    public void setUp() {
        this.testJob = new HypervisorHeartbeatUpdateJob(this.mockConsumerCurator);
    }

    @Test
    public void testBasicConfig() {
        JobConfig config = HypervisorHeartbeatUpdateJob.createJobConfig();

        assertEquals(HypervisorHeartbeatUpdateJob.JOB_KEY, config.getJobKey());
        assertEquals(HypervisorHeartbeatUpdateJob.JOB_NAME, config.getJobName());
    }

    @Test
    public void testConfigSetOwner() {
        String ownerKey = "test_owner";
        Owner owner = new Owner(ownerKey, ownerKey);

        JobConfig config = HypervisorHeartbeatUpdateJob.createJobConfig()
            .setOwner(owner);

        // Verify the owner key was set
        JobArguments args = config.getJobArguments();

        assertNotNull(args);
        assertEquals(1, args.size());

        // We aren't concerned with the key it's stored under, just that it's stored. As such, we
        // need to get the key from the key set so we can reference it for use with getAsString.
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(ownerKey, args.getAsString(argKey));

        assertEquals(owner, config.getContextOwner());
    }

    @Test
    public void testConfigSetReporterId() {
        String reporterId = "test_reporter_id";

        JobConfig config = HypervisorHeartbeatUpdateJob.createJobConfig()
            .setReporterId(reporterId);

        // Verify the owner key was set
        JobArguments args = config.getJobArguments();

        assertNotNull(args);
        assertEquals(1, args.size());

        // We aren't concerned with the key it's stored under, just that it's stored. As such, we
        // need to get the key from the key set so we can reference it for use with getAsString.
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(reporterId, args.getAsString(argKey));
    }


    @Test
    public void testExecution() throws JobExecutionException {
        String ownerKey = "test_owner";
        String reporterId = "test_reporter_id";

        Owner owner = new Owner(ownerKey, "ownerName");

        JobConfig config = HypervisorHeartbeatUpdateJob.createJobConfig()
            .setOwner(owner)
            .setReporterId(reporterId);

        JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(config.getJobArguments()).when(context).getJobArguments();

        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);

        Date min = new Date();
        this.testJob.execute(context);
        Date max = new Date();

        verify(this.mockConsumerCurator, times(1))
            .heartbeatUpdate(eq(reporterId), captor.capture(), eq(ownerKey));

        Date actual = captor.getValue();

        assertNotNull(actual);
        assertTrue(min.getTime() <= actual.getTime());
        assertTrue(actual.getTime() <= max.getTime());
    }

}
