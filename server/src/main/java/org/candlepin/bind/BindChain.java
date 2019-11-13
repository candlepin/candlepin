/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.common.exceptions.ServiceUnavailableException;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.entitlement.Enforcer;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Holds and represents the binding chain of responsibility.
 * Inspired by the servlet filter interfaces.
 */
public class BindChain {
    private BindContext context;
    private List<BindOperation> operations = new ArrayList<>();
    private static Logger log = LoggerFactory.getLogger(BindChain.class);

    @Inject
    public BindChain(
        BindContextFactory bindContextFactory,
        PreEntitlementRulesCheckOpFactory rulesCheckOpFactory,
        HandleEntitlementsOp handleEntitlementsOp,
        PostBindBonusPoolsOp postBindBonusPoolsOp,
        CheckBonusPoolQuantitiesOp checkBonusPoolQuantitiesOp,
        HandleCertificatesOp handleCertificatesOp,
        ComplianceOp complianceOp,
        @Assisted Consumer consumer,
        @Assisted Map<String, Integer> poolQuantityMap,
        @Assisted Enforcer.CallerType caller) {

        context = bindContextFactory.create(consumer, poolQuantityMap);
        operations.add(rulesCheckOpFactory.create(caller));
        operations.add(handleEntitlementsOp);
        operations.add(postBindBonusPoolsOp);
        operations.add(checkBonusPoolQuantitiesOp);
        operations.add(handleCertificatesOp);
        operations.add(complianceOp);
    }

    private boolean preProcess(BindContext context) {
        for (BindOperation operation : operations) {
            log.debug("Starting preprocess of {}", operation.getClass().getSimpleName());
            if (operation.preProcess(context)) {
                log.debug("Finished preprocess of {}", operation.getClass().getSimpleName());
            }
            else {
                log.error("Skipped chain in preprocess of operation {}",
                    operation.getClass().getSimpleName());
                return false;
            }
        }
        return true;
    }

    private void lock(BindContext context) {
        log.debug("Requesting locks");
        context.lockPools();
        log.debug("Successfully achieved locks");
    }

    private boolean execute(BindContext context) {
        for (BindOperation operation : operations) {
            log.debug("Starting execute of {}", operation.getClass().getSimpleName());
            try {
                if (operation.execute(context)) {
                    log.debug("Finished execute of {}", operation.getClass().getSimpleName());
                }
                else {
                    log.error("Skipped chain execute in operation {}", operation.getClass().getSimpleName());
                    return false;
                }
            }
            catch (ConstraintViolationException e) {
                throw new ServiceUnavailableException("Error during entitlement creation, potentially due " +
                    "to concurrent requests", e);
            }
        }
        return true;
    }

    public Collection<Entitlement> run() throws EntitlementRefusedException {
        if (preProcess(context)) {
            lock(context);
            if (execute(context)) {
                return context.getEntitlementMap().values();
            }
        }
        throw context.getException();
    }
}
