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
package org.candlepin.pinsetter.tasks;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * RefreshPoolsJobTest
 */
public class RefreshPoolsJobTest {

    @Test
    public void execute() throws Exception {
        // prep
        CandlepinPoolManager pm = mock(CandlepinPoolManager.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        Owner owner = mock(Owner.class);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap jdm = mock(JobDataMap.class);
        Refresher refresher = mock(Refresher.class);

        when(ctx.getMergedJobDataMap()).thenReturn(jdm);
        when(jdm.getString(eq(JobStatus.TARGET_ID))).thenReturn("someownerkey");
        when(jdm.getBoolean(eq(RefreshPoolsJob.LAZY_REGEN))).thenReturn(true);
        when(oc.lookupByKey(eq("someownerkey"))).thenReturn(owner);
        when(owner.getDisplayName()).thenReturn("test owner");
        when(pm.getRefresher(eq(true))).thenReturn(refresher);
        when(refresher.add(eq(owner))).thenReturn(refresher);

        // test
        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm);
        rpj.execute(ctx);

        // verification
        verify(pm).getRefresher(true);
        verify(refresher).add(owner);
        verify(refresher).run();
        verify(ctx).setResult(eq("Pools refreshed for owner test owner"));
    }

    @Test
    public void forOwner() {
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn("owner key");

        JobDetail detail = RefreshPoolsJob.forOwner(owner, true);
        assertNotNull(detail);
        assertNotNull(detail.getJobDataMap());
        assertTrue(detail.requestsRecovery());
        assertEquals("owner key", detail.getJobDataMap().get(JobStatus.TARGET_ID));
    }

    @Test(expected = JobExecutionException.class)
    public void handleException() throws JobExecutionException {
        // prep
        CandlepinPoolManager pm = mock(CandlepinPoolManager.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        Owner owner = mock(Owner.class);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        JobDataMap jdm = mock(JobDataMap.class);
        Refresher refresher = mock(Refresher.class);

        when(ctx.getMergedJobDataMap()).thenReturn(jdm);
        when(jdm.getString(eq(JobStatus.TARGET_ID))).thenReturn("someownerkey");
        when(jdm.getBoolean(eq(RefreshPoolsJob.LAZY_REGEN))).thenReturn(true);
        when(oc.lookupByKey(eq("someownerkey"))).thenReturn(owner);
        when(pm.getRefresher(eq(true))).thenReturn(refresher);
        when(refresher.add(eq(owner))).thenReturn(refresher);

        // the real thing we want to handle
        doThrow(new NullPointerException()).when(refresher).run();

        // test
        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm);
        rpj.execute(ctx);
    }
}
