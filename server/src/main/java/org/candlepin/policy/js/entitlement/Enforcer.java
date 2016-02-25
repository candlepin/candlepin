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
package org.candlepin.policy.js.entitlement;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.ValidationResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Enforces the entitlement rules definitions.
 */
public interface Enforcer {

    /**
     * Enum representing context with which the rules are being called.
     * Currently being used by preEntitlement - 2013-05-06
     */
    enum CallerType {
        BIND("bind"),
        UNKNOWN("unknown"),
        LIST_POOLS("list_pools"),
        BEST_POOLS("best_pools");

        private final String label;

        CallerType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }

        public String toString() {
            return getLabel();
        }
    }

    /**
     * Run pre-entitlement checks.
     *
     * Ensures sufficient entitlements remain, but also verifies all attributes
     * on the product and relevant entitlement pool pass using the current
     * policy.
     *
     * This is run prior to granting an entitlement.
     *
     * @param consumer Consumer who wishes to consume an entitlement.
     * @param entitlementPool Entitlement pool to consume from.
     * @param quantity number of entitlements to consume.
     * @return {@link ValidationResult} a validation result from the pre-entitlement run.
     */
    ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity);

    /**
     * Run pre-entitlement checks.
     *
     * Ensures sufficient entitlements remain, but also verifies all attributes
     * on the product and relevant entitlement pool pass using the current
     * policy.
     *
     * This is run prior to granting an entitlement.
     *
     * @param consumer Consumer who wishes to consume an entitlement.
     * @param entitlementPool Entitlement pool to consume from.
     * @param quantity number of entitlements to consume.
     * @param caller the context calling the rules.
     * @return {@link ValidationResult} a validation result from the pre-entitlement run.
     */
    ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity, CallerType caller);

    /**
     * Run pre-entitlement checks on a batch of pools.
     * Ensures sufficient entitlements remain, but also verifies all attributes
     * on the product and relevant entitlement pool pass using the current
     * policy.
     * This is run prior to granting an entitlement.
     *
     * @param consumer Consumer who wishes to consume an entitlement.
     * @param entitlementPoolQuantities Entitlement pools to consume from, and
     *        the respective number of entitlements to consume.
     * @param caller the context calling the rules.
     * @return {@link ValidationResult} a validation result from the
     *         pre-entitlement run.
     */
    Map<String, ValidationResult> preEntitlement(Consumer consumer,
            Collection<PoolQuantity> entitlementPoolQuantities,
            CallerType caller);

    /**
     * Run pre-entitlement checks on a batch of pools.
     * Ensures sufficient entitlements remain, but also verifies all attributes
     * on the product and relevant entitlement pool pass using the current
     * policy.
     * This is run prior to granting an entitlement.
     *
     * @param consumer Consumer who wishes to consume an entitlement.
     * @param entitlementPoolQuantities Entitlement pools to consume from, and
     *        the respective number of entitlements to consume.
     * @param caller the context calling the rules.
     * @return {@link ValidationResult} a validation result from the
     *         pre-entitlement run.
     */
    Map<String, ValidationResult> preEntitlement(Consumer consumer, Consumer host,
            Collection<PoolQuantity> entitlementPoolQuantities, CallerType caller);

    /**
     * @param consumer Consumer who wishes to consume an entitlement.
     * @param pools Entitlement pools to potentially consume from.
     * @param showAll if true, allows pools with warnings
     * @return list of valid pools for the given consumer
     */
    List<Pool> filterPools(Consumer consumer, List<Pool> pools, boolean showAll);

    /**
     * Run post-entitlement actions.
     *
     * @param c consumer
     * @param subPoolsForStackIds
     * @param ents The entitlement that was just granted.
     */
    void postEntitlement(PoolManager poolManager, Consumer c, Map<String, Entitlement> ents,
            List<Pool> subPoolsForStackIds);

    /**
     * Run post-entitlement actions.
     *
     * @param c the consumer to unbind on
     * @param poolManager
     * @param ent The entitlement that needs to be revoked
     */
    void postUnbind(Consumer c, PoolManager poolManager, Entitlement ent);

}
