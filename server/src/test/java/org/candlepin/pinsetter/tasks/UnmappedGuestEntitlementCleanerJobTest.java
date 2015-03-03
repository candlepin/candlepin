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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class UnmappedGuestEntitlementCleanerJobTest {
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private PoolManager poolManager;

    @Test
    public void testToExecute() throws Exception {
        /* TODO This would be a more faithful test if we did it as a DatabaseFixtureTest.
         * Unfortunately, there is some strange problem with deleted entitlements not actually
         * being deleted from the in-memory DB. */
        Owner owner1 = new Owner("o1");
        Owner owner2 = new Owner("o2");

        Product product1 = TestUtil.createProduct(owner1);
        Product product2 = TestUtil.createProduct(owner2);

        Pool p1 = TestUtil.createPool(owner1, product1);
        Pool p2 = TestUtil.createPool(owner2, product2);

        p1.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));
        p2.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));

        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        Date twelveHoursAgo =  new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer c;

        c = TestUtil.createConsumer(owner1);
        c.setCreated(thirtySixHoursAgo);

        Entitlement e1 = TestUtil.createEntitlement(owner1, c, p1, null);
        Set<Entitlement> entitlementSet1 = new HashSet<Entitlement>();
        entitlementSet1.add(e1);

        p1.setEntitlements(entitlementSet1);

        c = TestUtil.createConsumer(owner2);
        c.setCreated(twelveHoursAgo);

        Entitlement e2 = TestUtil.createEntitlement(owner2, c, p2, null);
        Set<Entitlement> entitlementSet2 = new HashSet<Entitlement>();
        entitlementSet2.add(e2);

        p2.setEntitlements(entitlementSet2);

        when(entitlementCurator.findByPoolAttribute(eq("unmapped_guests_only"), eq("true")))
            .thenReturn(Arrays.asList(new Entitlement[] {e1,  e2}));

        new UnmappedGuestEntitlementCleanerJob(entitlementCurator, poolManager).execute(null);

        verify(poolManager).revokeEntitlement(e1);
    }
}
