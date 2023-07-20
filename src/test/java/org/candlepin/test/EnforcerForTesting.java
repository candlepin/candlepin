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
package org.candlepin.test;

import org.candlepin.bind.PoolOperations;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.Enforcer;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class EnforcerForTesting implements Enforcer {

    @Override
    public PoolOperations postEntitlement(Consumer consumer,
        Map<String, Entitlement> ent, List<Pool> subPoolsForStackIds, boolean isUpdate,
        Map<String, PoolQuantity> poolQuantityMap) {
        return new PoolOperations();
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool enitlementPool,
        Integer quantity) {
        return new ValidationResult();
    }

    @Override
    public ValidationResult preEntitlement(Consumer consumer, Pool enitlementPool,
        Integer quantity, CallerType caller) {
        return new ValidationResult();
    }

    @Override
    public ValidationResult update(Consumer consumer, Entitlement entitlement, Integer change) {
        return new ValidationResult();
    }

    @Override
    public void finishValidation(ValidationResult result, Pool pool, Integer quantity) {
    }

    @Override
    public List<Pool> filterPools(Consumer consumer, List<Pool> pools,
        boolean showAll) {
        return pools;
    }

    @Override
    public Map<String, ValidationResult> preEntitlement(Consumer consumer,
        Collection<PoolQuantity> entitlementPoolQuantities, CallerType caller) {
        Map<String, ValidationResult> result = new HashMap<>();
        for (PoolQuantity pool : entitlementPoolQuantities) {
            result.put(pool.getPool().getId(), preEntitlement(consumer, pool.getPool(), pool.getQuantity()));
        }
        return result;
    }

    @Override
    public Map<String, ValidationResult> preEntitlement(Consumer consumer, Consumer host,
        Collection<PoolQuantity> entitlementPoolQuantities, CallerType caller) {
        return preEntitlement(consumer, entitlementPoolQuantities, caller);
    }
}
