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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.PreEntHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.util.Util;
import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
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
    private Config config;

    @Inject
    protected PoolCurator(SubscriptionServiceAdapter subAdapter, Enforcer enforcer, 
        ProductServiceAdapter productAdapter, EventSink sink, EventFactory eventFactory,
        Config cfg) {
        
        super(Pool.class);
        this.subAdapter = subAdapter;
        this.productAdapter = productAdapter;
        this.enforcer = enforcer;
        this.sink = sink;
        this.eventFactory = eventFactory;
        this.config = cfg; 
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
     * Return all pools referencing the given entitlement as their source entitlement.
     *
     * @param e Entitlement
     * @return Pools created as a result of this entitlement.
     */
    public List<Pool> listBySourceEntitlement(Entitlement e) {
        List<Pool> results = (List<Pool>) currentSession().createCriteria(Pool.class)
            .add(Restrictions.eq("sourceEntitlement", e)).list();
        if (results == null) {
            results = new LinkedList<Pool>();
        }
        return results;
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
    
    /**
     * List all entitlement pools for the given owner and product.
     * 
     * @param owner owner of the entitlement pool
     * @param productId product filter.
     * @return list of EntitlementPools
     */
    @Transactional
    @EnforceAccessControl
    public List<Pool> listByOwnerAndProduct(Owner owner,
            String productId) {  
        return listAvailableEntitlementPools(null, owner, productId, false);
    }

    /**
     * Check our underlying subscription service and update the pool data. Note
     * that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt
     * with separately from this event.
     *
     * @param owner Owner to be refreshed.
     * @return List of entitlements which need to be revoked
     */
    public List<Entitlement> refreshPools(Owner owner) {
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
        List<Entitlement> toRevoke = Util.newList();
        for (Subscription sub : subs) {
            if (!poolExistsForSubscription(subToPoolMap, sub.getId())) {
                createPoolForSubscription(sub);
                subToPoolMap.remove(sub.getId());
            }
            else {
                Pool existingPool = subToPoolMap.get(sub.getId());
                toRevoke.addAll(updatePoolForSubscription(existingPool, sub));
                subToPoolMap.remove(sub.getId());
            }
        }
    
        // de-activate pools whose subscription disappeared:
        for (Entry<Long, Pool> entry : subToPoolMap.entrySet()) {
            deactivatePool(entry.getValue());
        }
        return toRevoke;
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
     * @return List of entitlement pools.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            String productId, boolean activeOnly) {

        if (o == null && c != null) {
            o = c.getOwner();
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Listing available pools for:");
            log.debug("   consumer: " + c);
            log.debug("   owner: " + o);
            log.debug("   product: " + productId);
        }
        
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
        List<Pool> results = crit.list();
        log.debug("active pools for owner: " + results.size());
        
        if (results == null) {
            log.debug("no results");
            return new ArrayList<Pool>();
        }

        // Filter for product we want:
        if (productId != null) {

            List<Pool> newResults = new LinkedList<Pool>();
            for (Pool p : results) {
                // Provides will check if the products are a direct match, or if the
                // desired product is provided by the product this pool is for:
                if (p.provides(productId)) {
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
            String productId = pool.getProductId();            
            if (pool.getProductName() == null) { 
                HashMap<String, String> names = productAdapter.
                    getProductNamesByProductId(new String[] 
                    { productId });
                if (null != names) { 
                    pool.setProductName(names.get(productId));
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

    public List<Entitlement> updatePoolForSubscription(Pool existingPool,
        Subscription sub) {
        log.debug("Found existing pool for sub: " + sub.getId());
        // Modify the pool only if the values have changed
        if ((!sub.getQuantity().equals(existingPool.getQuantity())) ||
            (!sub.getStartDate().equals(existingPool.getStartDate())) ||
            (!sub.getEndDate().equals(existingPool.getEndDate())))  {      
             
            //TODO: Shoud have better events, one per type of change
            Event e = eventFactory.poolQuantityChangedFrom(existingPool);
            existingPool.setQuantity(sub.getQuantity());
            existingPool.setStartDate(sub.getStartDate());
            existingPool.setEndDate(sub.getEndDate());
            merge(existingPool);  
            
            eventFactory.poolQuantityChangedTo(e, existingPool);
            sink.sendEvent(e);
        }
        boolean lifo = !config
        .getBoolean(ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER);
    
        List<Entitlement> toRevoke = Util.newList();
        if (existingPool.isOverflowing()) {
            // if pool quantity has reduced, then start with revocation.
            @SuppressWarnings("unchecked")
            Iterator<Entitlement> iter = 
                criteriaToSelectEntitlementForPool(existingPool)
                    .add(Restrictions.eq("isFree", false))
                    .addOrder(lifo ? Order.desc("created") : Order.asc("created"))
                    .list().iterator();
            
            long consumed = existingPool.getConsumed();
            while ((consumed > existingPool.getQuantity()) && iter.hasNext()) {
                Entitlement e = iter.next();
                toRevoke.add(e);
                consumed -= e.getQuantity();
            }
        }
        return toRevoke;
    }

    public void createPoolForSubscription(Subscription sub) {
        log.debug("Creating new pool for new sub: " + sub.getId());

        Long quantity = sub.getQuantity() * sub.getProduct().getMultiplier();
        Set<String> productIds = new HashSet<String>();
        for (Product p : sub.getProvidedProducts()) {
            productIds.add(p.getId());
        }
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(), productIds,
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
    
    public Criteria criteriaToSelectEntitlementForPool(Pool entitlementPool) {
        return currentSession().createCriteria(Entitlement.class)
        .add(Restrictions.eq("pool", entitlementPool));
    }

    /**
     * @param entitlementPool entitlement pool to search.
     * @return entitlements in the given pool.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<Entitlement> entitlementsIn(Pool entitlementPool) {
        return criteriaToSelectEntitlementForPool(entitlementPool).list();
    }
    
    public Pool lookupBySubscriptionId(Long subId) {
        return (Pool) currentSession().createCriteria(Pool.class)
        .add(Restrictions.eq("subscriptionId", subId)).uniqueResult();
    }
}
