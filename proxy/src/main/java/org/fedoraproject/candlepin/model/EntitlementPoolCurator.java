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
package org.fedoraproject.candlepin.model;

import java.util.LinkedList;
import java.util.List;

import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

public class EntitlementPoolCurator extends AbstractHibernateCurator<EntitlementPool> {

    private SubscriptionServiceAdapter subAdapter;
    

    @Inject
    protected EntitlementPoolCurator(SubscriptionServiceAdapter subAdapter) {
        super(EntitlementPool.class);
        this.subAdapter = subAdapter;
    }

    @SuppressWarnings("unchecked")
    public List<EntitlementPool> listByOwner(Owner o) {
        List<EntitlementPool> results = (List<EntitlementPool>) currentSession()
            .createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("owner", o)).list();
        if (results == null) {
            return new LinkedList<EntitlementPool>();
        }
        else {
            return results;
        }
    }
    
    /**
     * Before executing any entitlement pool query, check our underlying subscription service
     * and update the pool data. Must be careful to call this before we do any pool query.
     * Note that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt with
     * separately from this event.
     *
     * @param owner
     * @param product
     */
    private void refreshPools(Owner owner, Product product) {
        List<Subscription> subs = subAdapter.getSubscriptions(owner, product.getId().toString());
    }

    /**
     * Look for an entitlement pool for the given owner, consumer, product.
     *
     * Note that consumer can (and often will be null). This method first
     * checks for a consumer specific method
     * @param owner
     * @param consumer
     * @param product
     * @return
     */
    public EntitlementPool lookupByOwnerAndProduct(Owner owner,
            Consumer consumer, Product product) {

        refreshPools(owner, product);

        // If we were given a specific consumer, and a pool exists for that
        // specific consumer, return this pool instead.
        if (consumer != null) {
            EntitlementPool result = (EntitlementPool)
                currentSession().createCriteria(EntitlementPool.class)
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("product", product))
                .add(Restrictions.eq("consumer", consumer))
                .uniqueResult();
            if (result != null) {
                return result;
            }
        }

        return (EntitlementPool) currentSession().createCriteria(EntitlementPool.class)
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.eq("product", product))
            .uniqueResult();
    }
    
    @Transactional
    public EntitlementPool create(EntitlementPool entity) {
        
        // Make sure there isn't already a pool for this product. Ideally we'd catch
        // this with a database constraint but I don't see how to do that just yet.
        EntitlementPool existing = lookupByOwnerAndProduct(entity.getOwner(), 
                entity.getConsumer(), entity.getProduct());

        if (existing != null) {
            if (entity.getConsumer() == null) {
                throw new RuntimeException("Already an entitlement pool for owner " +
                        entity.getOwner().getName() + " and product " +
                        entity.getProduct().getLabel());

            }
            else if (existing.getConsumer() != null) {
                throw new RuntimeException("Already an entitlement pool for owner " +
                        entity.getOwner().getName() + " and product " +
                        entity.getProduct().getLabel());
            }
        }
        
        return super.create(entity);
    }
    
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Entitlement> entitlementsIn(EntitlementPool entitlementPool) {
        return currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool", entitlementPool))
            .list();
    }
}
