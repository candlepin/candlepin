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
package org.candlepin.bind;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.pool.PoolRules;

import org.apache.commons.collections.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;



/**
 * This BindOperation will be replaced shortly in an upcoming PR, where we
 * will split the implementation into pre-process and execute steps.
 */
public class PostBindBonusPoolsOp implements BindOperation {

    private final PoolRules poolRules;
    private final ConsumerTypeCurator consumerTypeCurator;
    private final PoolCurator poolCurator;
    private final Enforcer enforcer;
    private final PoolOpProcessor poolOpProcessor;
    private final PoolOperations poolOperations = new PoolOperations();
    private List<Pool> subPoolsForStackIds = null;

    @Inject
    public PostBindBonusPoolsOp(PoolRules poolRules, ConsumerTypeCurator consumerTypeCurator,
        PoolCurator poolCurator, Enforcer enforcer, PoolOpProcessor poolOpProcessor) {

        this.poolRules = poolRules;
        this.consumerTypeCurator = consumerTypeCurator;
        this.poolCurator = poolCurator;
        this.enforcer = enforcer;
        this.poolOpProcessor = poolOpProcessor;
    }

    @Override
    public boolean preProcess(BindContext context) {
        Consumer consumer = context.getConsumer();
        Map<String, Entitlement> entitlements = context.getEntitlementMap();
        Map<String, PoolQuantity> poolQuantities = context.getPoolQuantities();

        Set<String> stackIds = new HashSet<>();

        for (PoolQuantity poolQuantity : poolQuantities.values()) {
            if (poolQuantity.getPool().isStacked()) {
                stackIds.add(poolQuantity.getPool().getStackId());
            }
        }

        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);

        // temporarily associate pools with entitlements for post bind bonus pool operations
        for (Entry<String, Entitlement> entitlementEntry : entitlements.entrySet()) {
            String poolId = entitlementEntry.getKey();
            Entitlement entitlement = entitlementEntry.getValue();
            entitlement.setPool(poolQuantities.get(poolId).getPool());
        }

        // Manifest consumers should not contribute to the sharing org's stack,
        // as these consumer types should not have created a stack derived pool in the first place.
        // Therefore, we do not need to check if any stack derived pools need updating
        if (!stackIds.isEmpty() && !ctype.isManifest()) {
            subPoolsForStackIds = poolCurator.getSubPoolsForStackIds(consumer, stackIds);
            if (CollectionUtils.isNotEmpty(subPoolsForStackIds)) {
                poolRules.updatePoolsFromStack(consumer, subPoolsForStackIds,
                    entitlements.values(), false);
            }
        }
        else {
            subPoolsForStackIds = new ArrayList<>();
        }

        poolOperations.append(enforcer.postEntitlement(
            consumer,
            entitlements,
            subPoolsForStackIds,
            false,
            poolQuantities));

        // un-associate pools with entitlements.
        for (Entitlement entitlement : entitlements.values()) {
            entitlement.setPool(null);
        }

        return true;
    }

    @Override
    public boolean execute(BindContext context) {

        poolCurator.mergeAll(subPoolsForStackIds, false);
        this.poolOpProcessor.process(poolOperations);

        return true;
    }

}
