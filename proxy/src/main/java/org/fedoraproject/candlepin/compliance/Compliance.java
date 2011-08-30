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
package org.fedoraproject.candlepin.compliance;

import java.util.Date;
import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInstalledProduct;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;

import com.google.inject.Inject;

/**
 * Compliance
 * 
 * A class used to check consumer compliance status.
 */
public class Compliance {
    
    private EntitlementCurator entCurator;
    
    @Inject
    public Compliance(EntitlementCurator entCurator) {
        this.entCurator = entCurator;
    }

    /**
     * Check compliance status for a consumer on a specific date.
     * 
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date) {
        List<Entitlement> ents = entCurator.listByConsumerAndDate(c, date);
        
        ComplianceStatus status = new ComplianceStatus(date);
        
        for (ConsumerInstalledProduct installedProd : c.getInstalledProducts()) {
            String installedPid = installedProd.getProductId();
            for (Entitlement e : ents) {
                if (e.getPool().provides(installedPid)) {
                    // TODO: check stacking validity here
                    status.addCompliantProduct(installedPid, e);
                }
            }
            // Not compliant if we didn't find any entitlements for this product:
            if (!status.getCompliantProducts().containsKey(installedPid)) {
                status.addNonCompliantProduct(installedPid);
            }
        }
        
        return status;
    }
    
}
