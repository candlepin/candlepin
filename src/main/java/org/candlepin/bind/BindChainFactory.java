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
import javax.inject.Provider;


public class BindChainFactory {

    private final BindContextFactory bindContextFactory;
    private final PreEntitlementRulesCheckOpFactory rulesCheckOpFactory;

    private final Provider<HandleEntitlementsOp> handleEntitlementsOpProvider;
    private final Provider<PostBindBonusPoolsOp> postBindBonusPoolsOpProvider;
    private final Provider<CheckBonusPoolQuantitiesOp> checkBonusPoolQuantitiesOpProvider;
    private final Provider<HandleCertificatesOp> handleCertificatesOpProvider;
    private final Provider<ComplianceOp> complianceOpProvider;

    @Inject
    public BindChainFactory(BindContextFactory bindContextFactory,
        PreEntitlementRulesCheckOpFactory rulesCheckOpFactory,
        Provider<HandleEntitlementsOp> handleEntitlementsOpProvider,
        Provider<PostBindBonusPoolsOp> postBindBonusPoolsOpProvider,
        Provider<CheckBonusPoolQuantitiesOp> checkBonusPoolQuantitiesOpProvider,
        Provider<HandleCertificatesOp> handleCertificatesOpProvider,
        Provider<ComplianceOp> complianceOpProvider) {
        this.bindContextFactory = Objects.requireNonNull(bindContextFactory);
        this.rulesCheckOpFactory = Objects.requireNonNull(rulesCheckOpFactory);
        this.handleEntitlementsOpProvider = Objects.requireNonNull(handleEntitlementsOpProvider);
        this.postBindBonusPoolsOpProvider = Objects.requireNonNull(postBindBonusPoolsOpProvider);
        this.checkBonusPoolQuantitiesOpProvider = Objects.requireNonNull(checkBonusPoolQuantitiesOpProvider);
        this.handleCertificatesOpProvider = Objects.requireNonNull(handleCertificatesOpProvider);
        this.complianceOpProvider = Objects.requireNonNull(complianceOpProvider);
    }

    public BindChain create(Consumer consumer, Map<String, Integer> quantities, Enforcer.CallerType caller) {
        return new BindChain(
            this.bindContextFactory,
            this.rulesCheckOpFactory,
            this.handleEntitlementsOpProvider.get(),
            this.postBindBonusPoolsOpProvider.get(),
            this.checkBonusPoolQuantitiesOpProvider.get(),
            this.handleCertificatesOpProvider.get(),
            this.complianceOpProvider.get(),
            consumer, quantities, caller);
    }
}
