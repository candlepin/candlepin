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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;


/**
 * RegenEntitlementCertsJobTest
 */
public class RegenEntitlementCertsJobTest {

    @Test
    public void execute() throws Exception {
        // prep
        CandlepinPoolManager pm = mock(CandlepinPoolManager.class);
        JobExecutionContext jec = mock(JobExecutionContext.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        JobDetail detail = mock(JobDetail.class);
        JobDataMap jdm = mock(JobDataMap.class);
        SubscriptionServiceAdapter subService = mock(SubscriptionServiceAdapter.class);

        String ownerId = "foo_owner";
        String prodId = "bar_prod";
        boolean lazyRegen = true;

        Owner owner = new Owner(ownerId);

        when(oc.find(ownerId)).thenReturn(owner);

        when(jdm.getString(eq(RegenProductEntitlementCertsJob.OWNER_ID))).thenReturn(ownerId);
        when(jdm.getString(eq(RegenProductEntitlementCertsJob.PROD_ID))).thenReturn(prodId);
        when(jdm.getBoolean(eq(RegenProductEntitlementCertsJob.LAZY_REGEN))).thenReturn(lazyRegen);
        when(detail.getJobDataMap()).thenReturn(jdm);
        when(jec.getJobDetail()).thenReturn(detail);

        // test
        RegenProductEntitlementCertsJob recj = new RegenProductEntitlementCertsJob(pm, oc, subService);
        recj.execute(jec);

        // verification
        verify(pm).regenerateCertificatesOf(eq(subService), eq(owner), eq(prodId), eq(lazyRegen));
    }
}
