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
import org.candlepin.policy.js.entitlement.Enforcer;

import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;



public class BindChainFactory {

    private final BindContextFactory bindContextFactory;
    private final PreEntitlementRulesCheckOpFactory rulesCheckOpFactory;
    private final HandleEntitlementsOp handleEntitlementsOp;
    private final PostBindBonusPoolsOp postBindBonusPoolsOp;
    private final CheckBonusPoolQuantitiesOp checkBonusPoolQuantitiesOp;
    private final HandleCertificatesOp handleCertificatesOp;
    private final ComplianceOp complianceOp;

    @Inject
    public BindChainFactory(BindContextFactory bindContextFactory,
        PreEntitlementRulesCheckOpFactory rulesCheckOpFactory, HandleEntitlementsOp handleEntitlementsOp,
        PostBindBonusPoolsOp postBindBonusPoolsOp, CheckBonusPoolQuantitiesOp checkBonusPoolQuantitiesOp,
        HandleCertificatesOp handleCertificatesOp, ComplianceOp complianceOp) {
        this.bindContextFactory = Objects.requireNonNull(bindContextFactory);
        this.rulesCheckOpFactory = Objects.requireNonNull(rulesCheckOpFactory);
        this.handleEntitlementsOp = Objects.requireNonNull(handleEntitlementsOp);
        this.postBindBonusPoolsOp = Objects.requireNonNull(postBindBonusPoolsOp);
        this.checkBonusPoolQuantitiesOp = Objects.requireNonNull(checkBonusPoolQuantitiesOp);
        this.handleCertificatesOp = Objects.requireNonNull(handleCertificatesOp);
        this.complianceOp = Objects.requireNonNull(complianceOp);
    }

    public BindChain create(Consumer consumer, Map<String, Integer> quantities, Enforcer.CallerType caller) {
        return new BindChain(
            this.bindContextFactory,
            this.rulesCheckOpFactory,
            this.handleEntitlementsOp,
            this.postBindBonusPoolsOp,
            this.checkBonusPoolQuantitiesOp,
            this.handleCertificatesOp,
            this.complianceOp,
            consumer, quantities, caller);
    }
}
