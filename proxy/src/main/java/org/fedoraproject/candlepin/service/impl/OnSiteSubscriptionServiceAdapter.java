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
package org.fedoraproject.candlepin.service.impl;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import java.util.List;

public class OnSiteSubscriptionServiceAdapter implements
        SubscriptionServiceAdapter {
    
    private SubscriptionCurator subCurator;
    
    @Inject
    public OnSiteSubscriptionServiceAdapter(SubscriptionCurator subCurator) {
        this.subCurator = subCurator;
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner, String productId) {
        return subCurator.listByOwnerAndProduct(owner, productId);
    }

    @Override
    public Subscription getSubscription(Owner owner, Long subscriptionId) {
        return subCurator.lookupByOwnerAndId(owner, subscriptionId);
    }

}
