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
package org.fedoraproject.candlepin.test;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.consumer.ConsumerDeleteHelper;
import org.fedoraproject.candlepin.policy.js.entitlement.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;

/**
 * EnforcerForTesting
 */
public class EnforcerForTesting implements Enforcer {

    @Override
    public PostEntHelper postEntitlement(
            Consumer consumer, PostEntHelper postEntHelper, Entitlement ent) {
        return postEntHelper;
    }

    @Override
    public PreEntHelper preEntitlement(
            Consumer consumer, Pool enitlementPool, Integer quantity) {
        return new PreEntHelper(new Integer(1));
    }

    @Override
    public Pool selectBestPool(Consumer consumer, String productId,
        List<Pool> pools) throws RuleExecutionException {
        if (pools.isEmpty()) {
            return null;
        }
        return pools.get(0);
    }

    @Override
    public ConsumerDeleteHelper onConsumerDelete(
        ConsumerDeleteHelper consumerDeleteHelper, Consumer consumer) {
        return null;
    }
}
