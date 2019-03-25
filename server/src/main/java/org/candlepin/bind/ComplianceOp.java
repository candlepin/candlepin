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
package org.candlepin.bind;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.google.inject.Inject;

import java.util.Map;

/**
 * This bind operation pre-computes the compliance of a consumer,
 * to be applied once all entities are associated with each other.
 */
public class ComplianceOp implements BindOperation {

    ComplianceRules complianceRules;
    ComplianceStatus status;
    SystemPurposeComplianceRules systemPurposeComplianceRules;
    SystemPurposeComplianceStatus systemPurposeComplianceStatus;


    @Inject
    public ComplianceOp(ComplianceRules rules,
        SystemPurposeComplianceRules systemPurposeComplianceRules) {
        this.complianceRules = rules;
        this.systemPurposeComplianceRules = systemPurposeComplianceRules;
    }

    /**
     * Computes the compliance status implemented in java script.
     * does not compute the hash or apply the status.
     * requires only the consumer to be locked.
     * @param context
     */
    @Override
    public boolean preProcess(BindContext context) {
        Consumer consumer = context.getLockedConsumer();
        Map<String, Entitlement> entitlementMap = context.getEntitlementMap();
        Map<String, PoolQuantity> poolQuantityMap = context.getPoolQuantities();
        for (Map.Entry<String, Entitlement> entry: entitlementMap.entrySet()) {
            PoolQuantity pq = poolQuantityMap.get(entry.getKey());
            entry.getValue().setPool(pq.getPool());
        }
        status = complianceRules.getStatus(consumer,
            entitlementMap.values(),
            null,
            false,
            false,
            false,
            false);

        systemPurposeComplianceStatus = systemPurposeComplianceRules.getStatus(
            consumer,
            consumer.getEntitlements(),
            entitlementMap.values(),
            false);

        for (Map.Entry<String, Entitlement> entry: entitlementMap.entrySet()) {
            PoolQuantity pq = poolQuantityMap.get(entry.getKey());
            entry.getValue().setPool(null);
        }
        return true;
    }

    /**
     * compute the compliance hash from a compliance status and updates the consumer.
     * @param context
     */
    @Override
    public boolean execute(BindContext context) {
        Consumer consumer = context.getLockedConsumer();
        ConsumerType ctype = context.getConsumerType();

        complianceRules.updateEntsOnStart(consumer);

        if (!ctype.isManifest()) {
            complianceRules.applyStatus(consumer, status, false);
            systemPurposeComplianceRules.applyStatus(consumer, systemPurposeComplianceStatus, false);
        }

        return true;
    }
}
