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

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.pool.PoolHelper;

import com.google.inject.Inject;

/**
 * Determines which rules implementation to delegate to for a
 * given consumer. (manifest consumers have their own implementation)
 */
public class EnforcerDispatcher implements Enforcer {

    private ManifestEntitlementRules manifestEnforcer;
    private EntitlementRules jsEnforcer;

    @Inject
    public EnforcerDispatcher(EntitlementRules jsEnforcer,
        ManifestEntitlementRules candlepinEnforcer) {
        this.jsEnforcer = jsEnforcer;
        this.manifestEnforcer = candlepinEnforcer;
    }

    @Override
    public PoolHelper postEntitlement(Consumer consumer, PoolHelper postEntHelper,
        Entitlement ent) {
        if (consumer.getType().isManifest()) {
            return manifestEnforcer.postEntitlement(consumer, postEntHelper, ent);
        }
        return jsEnforcer.postEntitlement(consumer, postEntHelper, ent);
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool entitlementPool,
        Integer quantity) {

        if (consumer.getType().isManifest()) {
            return manifestEnforcer.preEntitlement(consumer, entitlementPool,
                quantity);
        }

        return jsEnforcer.preEntitlement(consumer, entitlementPool, quantity);
    }

    public PoolHelper postUnbind(Consumer consumer, PoolHelper postEntHelper,
                Entitlement ent) {
        if (consumer.getType().isManifest()) {
            return manifestEnforcer.postUnbind(consumer, postEntHelper, ent);
        }
        return jsEnforcer.postUnbind(consumer, postEntHelper, ent);
    }
}
