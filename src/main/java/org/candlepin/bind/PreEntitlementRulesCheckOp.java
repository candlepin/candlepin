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

import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;



/**
 * This operation is responsible for validating if a bind request is permissible.
 * Uses rules implemented in both Java and Javascript.
 */
public class PreEntitlementRulesCheckOp implements BindOperation {

    private static final Logger log = LoggerFactory.getLogger(PreEntitlementRulesCheckOp.class);

    private final Enforcer enforcer;
    private final CallerType callerType;
    private Map<String, ValidationResult> results;

    @Inject
    public PreEntitlementRulesCheckOp(Enforcer enforcer, CallerType callerType) {
        this.enforcer = Objects.requireNonNull(enforcer);
        this.callerType = Objects.requireNonNull(callerType);
    }

    /**
     * The java script portion of the rules can be run before the locks have been placed.
     * if there is a failure, we stop the chain here.
     * @param context
     */
    @Override
    public boolean preProcess(BindContext context) {

        /* Whether or not we run pre ent rules check, this first call
         * is used to fetch the pools and enrich some context fields.
         */
        Map<String, PoolQuantity> poolQuantityMap = context.getPoolQuantities();
        if (context.isQuantityRequested()) {
            log.debug("Running pre-entitlement rules.");
            // XXX preEntitlement is run twice for new entitlement creation
            results = enforcer.preEntitlement(context.getConsumer(), poolQuantityMap.values(), callerType);

            EntitlementRefusedException exception = checkResults();
            if (exception != null) {
                context.setException(exception, Thread.currentThread().getStackTrace());
                return false;
            }
        }
        return true;
    }

    /**
     * The pool's quantity might have changed since we last fetched it,
     * so ensure that the pool still has enough quantity left.
     * @param context
     */
    @Override
    public boolean execute(BindContext context) {
        if (context.isQuantityRequested()) {
            for (PoolQuantity poolQuantity : context.getPoolQuantities().values()) {
                Pool pool = poolQuantity.getPool();
                enforcer.finishValidation(results.get(pool.getId()),
                    pool, context.getPoolQuantities().get(pool.getId()).getQuantity());
            }

            EntitlementRefusedException exception = checkResults();
            if (exception != null) {
                context.setException(exception, Thread.currentThread().getStackTrace());
                return false;
            }
        }

        return true;
    }

    private EntitlementRefusedException checkResults() {
        boolean success = true;
        for (Map.Entry<String, ValidationResult> entry : results.entrySet()) {
            ValidationResult result = entry.getValue();
            if (!result.isSuccessful()) {
                log.warn("Entitlement not granted: {} for pool: {}",
                    result.getErrors().toString(), entry.getKey());
                success = false;
            }
        }
        if (!success) {
            return new EntitlementRefusedException(results);
        }
        return null;
    }
}
