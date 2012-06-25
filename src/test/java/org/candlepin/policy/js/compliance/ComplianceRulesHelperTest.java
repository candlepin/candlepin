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
package org.candlepin.policy.js.compliance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.test.TestUtil;
import org.junit.Test;
import org.mockito.Mock;

/**
 * ComplianceRulesHelperTest
 */
public class ComplianceRulesHelperTest {

    @Mock
    private EntitlementCurator entCurator;

    @Test
    public void entitlementDatesContainNoDuplicates() {

        List<Entitlement> entitlements = Arrays.asList(
            createEntitlementEndingOn(2006, 4, 12),
            createEntitlementEndingOn(2006, 4, 12),
            createEntitlementEndingOn(2007, 4, 12)
        );

        ComplianceRulesHelper helper = new ComplianceRulesHelper(entCurator);
        List<Date> dateSet = helper.getSortedEndDatesFromEntitlements(entitlements);
        assertEquals(2, dateSet.size());
        assertTrue(dateSet.contains(entitlements.get(0).getEndDate()));
        assertTrue(dateSet.contains(entitlements.get(2).getEndDate()));
    }

    @Test
    public void entitlementDatesAreSortedAscending() {
        List<Entitlement> entitlements = Arrays.asList(
            createEntitlementEndingOn(2006, 4, 12),
            createEntitlementEndingOn(2006, 4, 8),
            createEntitlementEndingOn(2005, 7, 1),
            createEntitlementEndingOn(2011, 4, 12),
            createEntitlementEndingOn(2006, 2, 12)
        );

        Date[] expectedOrder = {
            entitlements.get(2).getEndDate(),
            entitlements.get(4).getEndDate(),
            entitlements.get(1).getEndDate(),
            entitlements.get(0).getEndDate(),
            entitlements.get(3).getEndDate(),

        };

        ComplianceRulesHelper helper = new ComplianceRulesHelper(entCurator);
        List<Date> sortedEndDates = helper.getSortedEndDatesFromEntitlements(entitlements);
        assertEquals(5, sortedEndDates.size());
        assertArrayEquals(expectedOrder, sortedEndDates.toArray());
    }

    private Entitlement createEntitlementEndingOn(int year, int month, int day) {
        Entitlement ent = TestUtil.createEntitlement();
        ent.setEndDate(TestUtil.createDate(year, month, day));
        return ent;
    }
}
