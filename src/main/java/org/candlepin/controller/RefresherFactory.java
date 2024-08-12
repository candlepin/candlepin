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
package org.candlepin.controller;

import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import java.util.Objects;

import javax.inject.Inject;



public class RefresherFactory {

    private final OwnerCurator ownerCurator;
    private final PoolCurator poolCurator;
    private final PoolManager poolManager;
    private final PoolConverter poolConverter;

    @Inject
    public RefresherFactory(OwnerCurator ownerCurator, PoolManager poolManager,
        PoolCurator poolCurator, PoolConverter poolConverter) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.poolManager = Objects.requireNonNull(poolManager);
        this.poolConverter = Objects.requireNonNull(poolConverter);
    }

    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter) {
        return new Refresher(this.poolManager, subAdapter, this.ownerCurator, this.poolCurator,
            this.poolConverter);
    }

}
