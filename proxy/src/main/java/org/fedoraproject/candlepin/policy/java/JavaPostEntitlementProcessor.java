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
package org.fedoraproject.candlepin.policy.java;

import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.policy.PostEntitlementProcessor;
import org.fedoraproject.candlepin.policy.actions.Action;
import org.fedoraproject.candlepin.policy.actions.CreateConsumerPoolAction;

import com.google.inject.Inject;

public class JavaPostEntitlementProcessor implements PostEntitlementProcessor {

    EntitlementPoolCurator epCurator;
    
    @Inject
    public JavaPostEntitlementProcessor(EntitlementPoolCurator epCuratorIn) {
        this.epCurator = epCuratorIn;
    }
    

    public void run(Entitlement ent) {
        Product prod = ent.getProduct();
        if (prod.getLabel().equals(JavaEnforcer.VIRTUALIZATION_HOST_PRODUCT)) {
//            new CreateConsumerPoolAction(epCurator, consumer, product, quantity, startDate, endDate)
        }
    }

}
