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

import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    private SubscriptionServiceAdapter subAdapter;
    

    @Inject
    protected PoolCurator(SubscriptionServiceAdapter subAdapter) {
        super(Pool.class);
        this.subAdapter = subAdapter;
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param o Owner to filter
     * @return pools owned by the given Owner.
     */
    @SuppressWarnings("unchecked")
    public List<Pool> listByOwner(Owner o) {
        return listAvailableEntitlementPools(null, o, (String) null, true);
    }
    
    
    /**
     * Returns list of pools available for the consumer.
     *
     * @param c Consumer to filter
     * @return pools owned by the given Owner.
     */
    @SuppressWarnings("unchecked")
    public List<Pool> listAvailableEntitlementPools(Consumer c) {
        return listAvailableEntitlementPools(c, null, (String) null, true);
    }
    
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            Product p, boolean activeOnly) {
        String productId = (p == null) ? null : p.getId();
        return listAvailableEntitlementPools(c, o, productId, activeOnly);
    }
    
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            String productId, boolean activeOnly) {
        List<Pool> results = null ;
        Criteria crit = currentSession().createCriteria(Pool.class);
        if (activeOnly) {
            crit.add(Restrictions.eq("activeSubscription", Boolean.TRUE));
        }
        if (c != null) {
            crit.add(Restrictions.eq("owner", c.getOwner()));
        }  
        if (o != null) {
            crit.add(Restrictions.eq("owner", o));            
        }

        if (productId != null) {
            crit.add(Restrictions.eq("productId", productId));
        }
        crit.add(Restrictions.lt("startDate", new Date())); // TODO: is this right?
        crit.add(Restrictions.gt("endDate", new Date())); // TODO: is this right?
        // FIXME: sort by enddate?
        // FIXME: currentmembers < maxmembers
        // FIXME: do we need to run through rules for each of these? (expensive!)        
        results = (List<Pool>) crit.list();
        
        if (results == null) {
            results = new ArrayList<Pool>();
        }
        
        return results ;
    }
    
    /**
     * Before executing any entitlement pool query, check our underlying
     * subscription service and update the pool data. Must be careful to call
     * this before we do any pool query. Note that refreshing the pools doesn't
     * actually take any action, should a subscription be reduced, expired, or
     * revoked. Pre-existing entitlements will need to be dealt with separately
     * from this event.
     *
     * @param owner Owner to be refreshed.
     * @param productId Products to refresh.
     */
    private void refreshPools(Owner owner, String productId) {
        List<Subscription> subs = subAdapter.getSubscriptions(owner, 
                productId);
        List<Pool> pools = listByOwnerAndProductNoRefresh(owner, productId);
        
        // Map all entitlement pools for this owner/product that have a
        // subscription ID associated with them.
        Map<Long, Pool> subToPoolMap = new HashMap<Long, Pool>();
        for (Pool p : pools) {
            if (p.getSubscriptionId() != null) {
                subToPoolMap.put(p.getSubscriptionId(), p);
            }
        }
        
        for (Subscription sub : subs) {
            // No pool exists for this subscription, create one:
            if (!subToPoolMap.containsKey(sub.getId())) {
                Pool newPool = new Pool(owner, productId,
                        sub.getQuantity(), sub.getStartDate(), sub.getEndDate());
                newPool.setSubscriptionId(sub.getId());
                create(newPool);
                subToPoolMap.remove(sub.getId());
            }
            else {
                Pool existingPool = subToPoolMap.get(sub.getId());
                
                // TODO: We're just updating the pool always now, would be much
                // better if we could check some kind of last modified date to
                // determine if a change has taken place:
                existingPool.setMaxMembers(sub.getQuantity());
                existingPool.setStartDate(sub.getStartDate());
                existingPool.setEndDate(sub.getEndDate());
                merge(existingPool);
            }
        }

        // Iterate pools whose subscription disappeared:
        for (Entry<Long, Pool> entry : subToPoolMap.entrySet()) {
            entry.getValue().setActiveSubscription(Boolean.FALSE);
            merge(entry.getValue());
        }

    }
    
    /**
     * List all entitlement pools for the given product.
     * 
     * @param product product filter.
     * @return list of EntitlementPools
     */
    public List<Pool> listByProduct(Product product) {
        return listAvailableEntitlementPools(null, null, product, true);        
    }

    /**
     * List all entitlement pools for the given owner and product.
     *
     * @param owner owner of the entitlement pool
     * @param productId product filter.
     * @return list of EntitlementPools
     */
    public List<Pool> listByProductId(String productId) {
        return listAvailableEntitlementPools(null, null, productId, true);
    }    

    /**
     * List all entitlement pools for the given owner and product.
     * 
     * @param owner owner of the entitlement pool
     * @param product product filter.
     * @return list of EntitlementPools
     */
    public List<Pool> listByOwnerAndProduct(Owner owner,
            Product product) {  
        refreshPools(owner, product.getId());        
        return listAvailableEntitlementPools(null, owner, product, false);
    }

    /**
     * List all entitlement pools for the given owner and product.
     *
     * @param owner owner of the entitlement pool
     * @param productId product filter.
     * @return list of EntitlementPools
     */
    public List<Pool> listByOwnerAndProductId(Owner owner,
            String productId) {
        refreshPools(owner, productId);
        return listByOwnerAndProductNoRefresh(owner, productId);
    }
    
    @SuppressWarnings("unchecked")
    private List<Pool> listByOwnerAndProductNoRefresh(Owner owner,
        String productId) {
        return listAvailableEntitlementPools(null, owner, productId, false);
    }
    
    /**
     * @param entitlementPool entitlement pool to search.
     * @return entitlements in the given pool.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Entitlement> entitlementsIn(Pool entitlementPool) {
        return currentSession().createCriteria(Entitlement.class)
            .add(Restrictions.eq("pool", entitlementPool))
            .list();
    }
}
