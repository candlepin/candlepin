/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.tasks;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.controller.CandlepinPoolManager;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;

import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;

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

        when(ctx.getMergedJobDataMap()).thenReturn(jdm);
        when(jdm.getString(eq(JobStatus.TARGET_ID))).thenReturn("someownerkey");
        when(oc.lookupByKey(eq("someownerkey"))).thenReturn(owner);
        when(owner.getDisplayName()).thenReturn("test owner");

        // test
        RefreshPoolsJob rpj = new RefreshPoolsJob(oc, pm);
        rpj.execute(ctx);

        // verification
        verify(pm).refreshPools(owner);
        verify(ctx).setResult(eq("Pools refreshed for owner test owner"));
    }

    @Test
    public void forOwner() {
        Owner owner = mock(Owner.class);
        when(owner.getKey()).thenReturn("owner key");

        JobDetail detail = RefreshPoolsJob.forOwner(owner);
        assertNotNull(detail);
        assertNotNull(detail.getJobDataMap());
        assertEquals("owner key", detail.getJobDataMap().get(JobStatus.TARGET_ID));
    }
}
