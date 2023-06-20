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

import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import javax.inject.Inject;

public class RefresherFactory {

    private final OwnerManager ownerManager;
    private final CandlepinPoolManager poolManager;

    @Inject
    public RefresherFactory(OwnerManager ownerManager, CandlepinPoolManager poolManager) {
        this.ownerManager = ownerManager;
        this.poolManager = poolManager;
    }

    public Refresher getRefresher(SubscriptionServiceAdapter subAdapter, ProductServiceAdapter prodAdapter) {
        return new Refresher(this.poolManager, subAdapter, prodAdapter, this.ownerManager);
    }

}
