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

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;

import java.util.Map;

import javax.inject.Inject;

/**
 * This BindOperation will be replaced shortly in an upcoming PR, where we
 * will split the implementation into pre-process and execute steps.
 */
public class CheckBonusPoolQuantitiesOp implements BindOperation {

    private PoolManager poolManager;

    @Inject
    public CheckBonusPoolQuantitiesOp(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    @Override
    public boolean preProcess(BindContext context) {
        return true;
    }

    @Override
    public boolean execute(BindContext context) {
        Consumer consumer = context.getLockedConsumer();
        Map<String, Entitlement> entitlements = context.getEntitlementMap();

        // we might have changed the bonus pool quantities, lets revoke ents if needed.
        poolManager.checkBonusPoolQuantities(consumer.getOwnerId(), entitlements);
        return true;
    }

}
