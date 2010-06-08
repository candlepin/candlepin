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
package org.fedoraproject.candlepin.policy;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;

/**
 * Enforces the entitlement rules definitions.
 */
public interface Enforcer {
    
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
     * @return TODO
     */
    PreEntHelper pre(Consumer consumer, Pool entitlementPool, Integer quantity);

    /**
     * Run post-entitlement actions.
     *
     * @param ent The entitlement that was just granted.
     * @return post-entitlement processor
     */
    PostEntHelper post(Entitlement ent);

    /**
     * Select the best entitlement pool available for the given product ID.
     *
     * If no pools are available, null will be returned.
     *
     * Will throw RuleExecutionException if both pools and a rule exist, but no pool
     * is returned from the rule.
     *
     * @param productId Product ID
     * @param pools List of pools to select from.
     * @return best pool as determined by the rules.
     * @throws RuleExecutionException Thrown if both pools and a rule exist, but no
     * pool is returned.
     */
    Pool selectBestPool(Consumer consumer, String productId, List<Pool> pools)
        throws RuleExecutionException;

}
