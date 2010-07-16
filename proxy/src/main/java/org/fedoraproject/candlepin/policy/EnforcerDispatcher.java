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
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.policy.js.PostEntHelper;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.policy.js.RuleExecutionException;
import org.fedoraproject.candlepin.policy.js.JavascriptEnforcer;

import com.google.inject.Inject;

/**
 * EnforcerDispatcher
 */
public class EnforcerDispatcher implements Enforcer {
    
    private CandlepinConsumerTypeEnforcer candlepinEnforcer;
    private JavascriptEnforcer jsEnforcer;

    @Inject
    public EnforcerDispatcher(JavascriptEnforcer jsEnforcer, 
        CandlepinConsumerTypeEnforcer candlepinEnforcer) {
        this.jsEnforcer = jsEnforcer;
        this.candlepinEnforcer = candlepinEnforcer;
    }

    @Override
    public PostEntHelper post(Consumer consumer, PostEntHelper postEntHelper,
        Entitlement ent) {
        if (isCandlepinConsumer(consumer)) {
            return candlepinEnforcer.post(consumer, postEntHelper, ent);
        }
        return jsEnforcer.post(consumer, postEntHelper, ent);
    }

    @Override
    public PreEntHelper pre(Consumer consumer, Pool entitlementPool, Integer quantity) {
        if (isCandlepinConsumer(consumer)) {
            return candlepinEnforcer.pre(consumer, entitlementPool, quantity);
        }
        return jsEnforcer.pre(consumer, entitlementPool, quantity);
    }

    private boolean isCandlepinConsumer(Consumer consumer) {
        return consumer.getType().isType(ConsumerTypeEnum.CANDLEPIN);
    }

    @Override
    public Pool selectBestPool(Consumer consumer, String productId, List<Pool> pools)
        throws RuleExecutionException {
        if (isCandlepinConsumer(consumer)) {
            return candlepinEnforcer.selectBestPool(consumer, productId, pools);
        }
        return jsEnforcer.selectBestPool(consumer, productId, pools);
    }

}
