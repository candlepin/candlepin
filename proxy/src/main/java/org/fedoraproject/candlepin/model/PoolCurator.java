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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    private static Logger log = Logger.getLogger(PoolCurator.class);

    private SubscriptionServiceAdapter subAdapter;
    private ProductServiceAdapter productAdapter;
    private Enforcer enforcer;
    private EventSink sink;
    private EventFactory eventFactory;
    

    @Inject
    protected PoolCurator(SubscriptionServiceAdapter subAdapter, Enforcer enforcer, 
        ProductServiceAdapter productAdapter, EventSink sink, EventFactory eventFactory) {
        
        super(Pool.class);
        this.subAdapter = subAdapter;
        this.productAdapter = productAdapter;
        this.enforcer = enforcer;
        this.sink = sink;
        this.eventFactory = eventFactory;
    }
    
    @Override
    @Transactional
    @EnforceAccessControl
    public Pool find(Serializable id) {
        Pool pool = super.find(id);
        addProductName(pool);
        
        return pool;
    }
    
    @Override
    @Transactional
    @EnforceAccessControl
    public List<Pool> listAll() {
        List<Pool> pools = super.listAll();
        
        addProductNames(pools);
        
        return pools;
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param o Owner to filter
     * @return pools owned by the given Owner.
     */
    @Transactional
    @EnforceAccessControl
    public List<Pool> listByOwner(Owner o) {
        return listAvailableEntitlementPools(null, o, (String) null, true);
    }
    
    
    /**
     * Returns list of pools available to the consumer.
     *
     * @param c Consumer to filter
     * @return pools available to the consumer.
     */
    @Transactional
    @EnforceAccessControl
    public List<Pool> listByConsumer(Consumer c) {
        return listAvailableEntitlementPools(c, c.getOwner(), (String) null, true);
    }
    
    @Transactional
    @EnforceAccessControl
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            Product p, boolean activeOnly) {
        String productId = (p == null) ? null : p.getId();
        Owner owner = o == null ? c.getOwner() : o;
        return listAvailableEntitlementPools(c, owner, productId, activeOnly);
    }
    
    /**
     * List all entitlement pools for the given owner and product.
     * 
     * @param owner owner of the entitlement pool
     * @param product product filter.
     * @return list of EntitlementPools
     */
    @Transactional
    @EnforceAccessControl
    public List<Pool> listByOwnerAndProduct(Owner owner,
            Product product) {  
        return listAvailableEntitlementPools(null, owner, product, false);
    }

    /**
     * Check our underlying subscription service and update the pool data. Note
     * that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt
     * with separately from this event.
     *
     * @param owner Owner to be refreshed.
     */
    public void refreshPools(Owner owner) {
        log.debug("Refreshing pools");
        
        List<Subscription> subs = subAdapter.getSubscriptions(owner);
        
        if (log.isDebugEnabled()) {
            log.debug("Found subscriptions: ");
            for (Subscription sub : subs) {
                log.debug("   " + sub);
            }
        }
        
        List<Pool> pools = listAvailableEntitlementPools(null, owner, (String) null, false);
    
        if (log.isDebugEnabled()) {
            log.debug("Found pools: ");
            for (Pool p : pools) {
                log.debug("   " + p);
            }
        }
        
        // Map all  pools for this owner/product that have a
        // subscription ID associated with them.
        Map<Long, Pool> subToPoolMap = new HashMap<Long, Pool>();
        for (Pool p : pools) {
            if (p.getSubscriptionId() != null) {
                subToPoolMap.put(p.getSubscriptionId(), p);
            }
        }
        
        for (Subscription sub : subs) {
            if (!poolExistsForSubscription(subToPoolMap, sub.getId())) {
                createPoolForSubscription(sub);
                subToPoolMap.remove(sub.getId());
            }
            else {
                Pool existingPool = subToPoolMap.get(sub.getId());
                updatePoolForSubscription(existingPool, sub);
                subToPoolMap.remove(sub.getId());
            }
        }
    
        // de-activate pools whose subscription disappeared:
        for (Entry<Long, Pool> entry : subToPoolMap.entrySet()) {
            deactivatePool(entry.getValue());
        }
    }

    /**
     * List entitlement pools.
     * 
     * Pools will be refreshed from the underlying subscription service.
     * 
     * If a consumer is specified, a pass through the rules will be done for each
     * potentially usable pool.
     * 
     * @param c
     * @param o
     * @param productId
     * @param activeOnly
     * @return
     */
    @SuppressWarnings("unchecked")
    @Transactional
    private List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            String productId, boolean activeOnly) {

        if (log.isDebugEnabled()) {
            log.debug("Listing available pools for:");
            log.debug("   consumer: " + c);
            log.debug("   owner: " + o);
            log.debug("   product: " + productId);
        }
        
        List<Pool> results = null;
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

        crit.add(Restrictions.lt("startDate", new Date())); // TODO: is this right?
        crit.add(Restrictions.gt("endDate", new Date())); // TODO: is this right?
        // FIXME: sort by enddate?
        results = crit.list();
        
        if (results == null) {
            log.debug("no results");
            return new ArrayList<Pool>();
        }

        // Filter for product we want:
        if (productId != null) {

            List<Pool> newResults = new LinkedList<Pool>();
            for (Pool p : results) {
                // TODO: Performance hit:
                Product poolProduct = productAdapter.getProductById(p.getProductId());
                
                // Provides will check if the products are a direct match, or if the
                // desired product is provided by the product this pool is for:
                if (poolProduct.provides(productId)) {
                    newResults.add(p);
                    if (log.isDebugEnabled()) {
                        log.debug("Pool provides " + productId +
                            ": " + p);
                    }
                }
            }
            results = newResults;
        }
        
        addProductNames(results);
        
        // If querying for pools available to a specific consumer, we need
        // to do a rules pass to verify the entitlement will be granted.
        // Note that something could change between the time we list a pool as
        // available, and the consumer requests the actual entitlement, and the
        // request still could fail.
        if (c != null) {
            List<Pool> newResults = new LinkedList<Pool>();
            for (Pool p : results) {
                PreEntHelper helper = enforcer.pre(c, p, 1);
                if (helper.getResult().isSuccessful() && 
                        !helper.getResult().hasWarnings()) {
                    newResults.add(p);
                }
                else {
                    log.info("Omitting pool due to failed rule check: " + p.getId());
                    log.info(helper.getResult().getErrors());
                    log.info(helper.getResult().getWarnings());
                }
            }
            results = newResults;
        }

        return results;
    }
    
    // set a single name, if its not already there
    private void addProductName(Pool pool) {
        if (pool != null) {
            if (pool.getProductName() == null) { 
                HashMap<String, String> names = this.productAdapter.
                    getProductNamesByProductId(new String[] 
                    { pool.getProductId() });
                if (null != names) { 
                    pool.setProductName(names.get(pool.getProductId()));
                }
            }
        }
    }
    
    // set a bunch of product names at once
    private void addProductNames(List<Pool> pools) { 
        if (pools != null && pools.size() > 0) { 
            String[] productIds = new String[pools.size()];
            int i = 0;
            for (Pool p : pools) {            
                // enrich with the product name
                productIds[i] = p.getProductId();
                i++;
            }
            
            // this will dramatically reduce response time for refreshing pools
            HashMap<String, String> productNames = this.productAdapter.
                getProductNamesByProductId(productIds);
            
            if (null != productNames) { 
                // set the product name
                for (Pool p : pools) { 
                    p.setProductName(productNames.get(p.getProductId()));
                } 
            }
        }
    }
    
    private boolean poolExistsForSubscription(Map<Long, Pool> subToPoolMap,
            Long id) {
        return subToPoolMap.containsKey(id);
    }

    public void updatePoolForSubscription(Pool existingPool, Subscription sub) {
        log.debug("Found existing pool for sub: " + sub.getId());
        
        Event e = eventFactory.poolQuantityChangedFrom(existingPool);
        // TODO: We're just updating the pool always now, would be much
        // better if we could check some kind of last modified date to
        // determine if a change has taken place:
        existingPool.setQuantity(sub.getQuantity());
        existingPool.setStartDate(sub.getStartDate());
        existingPool.setEndDate(sub.getEndDate());
        merge(existingPool);
        
        eventFactory.poolQuantityChangedTo(e, existingPool);
        sink.sendEvent(e);
    }

    public void createPoolForSubscription(Subscription sub) {
        log.debug("Creating new pool for new sub: " + sub.getId());
        Long quantity = sub.getQuantity() * sub.getMultiplier();
        
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(),
                quantity, sub.getStartDate(), sub.getEndDate());
        newPool.setSubscriptionId(sub.getId());
        create(newPool);
        log.debug("   new pool: " + newPool);
    }

    private void deactivatePool(Pool pool) {
        if (log.isDebugEnabled()) {
            log.info("Subscription disappeared for pool: " + pool);
        }
        pool.setActiveSubscription(Boolean.FALSE);
        merge(pool);
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
    
    public Pool lookupBySubscriptionId(Long subId) {
        return (Pool) currentSession().createCriteria(Pool.class)
        .add(Restrictions.eq("subscriptionId", subId)).uniqueResult();
    }
}
