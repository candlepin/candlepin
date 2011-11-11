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
package org.fedoraproject.candlepin.policy.js.compliance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;

/**
 * Provides javascript functions allowing them to perform a specific set
 * of operations we support. We do not want to expose the curator in the JS.
 */
public class ComplianceRulesHelper {
    private final EntitlementCurator entCurator;

    public ComplianceRulesHelper(EntitlementCurator entCurator) {
        this.entCurator = entCurator;
    }

    public List<Entitlement> getEntitlementsOnDate(Consumer consumer, Date date) {
        return entCurator.listByConsumerAndDate(consumer, date);
    }

    public List<Date> getSortedEndDatesFromEntitlements(List<Entitlement> entitlements) {
        Set<Date> dates = new HashSet<Date>();
        for (Entitlement ent : entitlements) {
            dates.add(ent.getEndDate());
        }

        List<Date> toreturn = new ArrayList<Date>(dates);
        Collections.sort(toreturn);
        return toreturn;
    }

}
