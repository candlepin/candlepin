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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

/**
 * default SubscriptionAdapter implementation
 */
public class DefaultSubscriptionServiceAdapter implements
        SubscriptionServiceAdapter {
    
    private SubscriptionCurator subCurator;
    private ProductServiceAdapter prodAdapter;
    private static Logger log = Logger.getLogger(DefaultSubscriptionServiceAdapter.class);

    @Inject
    public DefaultSubscriptionServiceAdapter(SubscriptionCurator subCurator,
        ProductServiceAdapter prodAdapter) {
        this.subCurator = subCurator;
        this.prodAdapter = prodAdapter;
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner, String productId) {
        
        log.debug("Searching for subscriptions providing: " + productId);
        List<Subscription> subs = new LinkedList<Subscription>();
        
        // We need "fuzzy" product matching, so we need to list all subs for this owner
        // and then filter out products that do not match:
        for (Subscription sub : getSubscriptions(owner)) {

            // TODO: Performance hit here, needs to be addressed:
            Product subProduct = prodAdapter.getProductById(sub.getProduct().getId());
            if (sub.getProduct().getId().equals(productId)) {
                subs.add(sub);
                if (log.isDebugEnabled()) {
                    log.debug("   found: " + sub);
                }
                continue;
            }
            else if (subProduct.provides(productId)) {
                if (log.isDebugEnabled()) {
                    log.debug("   found provides: " + sub);
                }
                subs.add(sub);
            }
        }
        return subs;
    }

    @Override
    public Subscription getSubscription(Long subscriptionId) {
        return subCurator.lookupByOwnerAndId(subscriptionId);
    }

    @Override
    public List<Subscription> getSubscriptionForToken(Owner owner, String token) {
        return subCurator.listBySubscriptionTokenID(token);
    }

    @Override
    public List<Subscription> getSubscriptionsSince(Owner owner, Date sinceDate) {
        return subCurator.listByOwnerAndProductSince(owner, sinceDate);
    }

    @Override
    public List<Subscription> getSubscriptionsSince(Date sinceDate) {
        return subCurator.listSince(sinceDate);
    }

    @Override
    public List<Subscription> getSubscriptions(Owner owner) {
        return subCurator.listByOwner(owner);
    }
    
    @Override
    public List<Subscription> getSubscriptions() {
        List<Subscription> toReturn = subCurator.listAll();
        return toReturn == null ? new LinkedList<Subscription>() : toReturn;
    }

    @Override
    public boolean hasUnacceptedSubscriptionTerms(Owner owner) {
        return false;
    }

    @Override
    public void sendActivationEmailTo(Owner owner, String email,
        String emailLocale) {
    }
}
