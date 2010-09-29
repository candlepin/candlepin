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

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    private static Logger log = Logger.getLogger(PoolCurator.class);
    private ProductServiceAdapter productAdapter;
    private Enforcer enforcer;

    @Inject
    protected PoolCurator(ProductServiceAdapter productAdapter, Enforcer enforcer) {
        super(Pool.class);
        this.productAdapter = productAdapter;
        this.enforcer = enforcer;
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
                PreEntHelper helper = enforcer.preEntitlement(c, p, 1);
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
    
    @Transactional
    @EnforceAccessControl
    public List<Pool> listPoolsRestrictedToUser(String username) {
        return listByCriteria(
            DetachedCriteria.forClass(Pool.class)
                .add(Restrictions.eq("restrictedToUsername", username)));
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

    @SuppressWarnings("unchecked")
    public List<Entitlement> retrieveFreeEntitlementsOfPool(Pool existingPool,
        boolean lifo) {
        return criteriaToSelectEntitlementForPool(existingPool)
            .add(Restrictions.eq("isFree", false))
            .addOrder(lifo ? Order.desc("created") : Order.asc("created"))
            .list();
    }

    public void deactivatePool(Pool pool) {
        if (log.isDebugEnabled()) {
            log.info("Subscription disappeared for pool: " + pool);
        }
        pool.setActiveSubscription(Boolean.FALSE);
        merge(pool);
    }
    
    private Criteria criteriaToSelectEntitlementForPool(Pool entitlementPool) {
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
    
    public Pool lookupBySubscriptionId(String subId) {
        return (Pool) currentSession().createCriteria(Pool.class)
        .add(Restrictions.eq("subscriptionId", subId)).uniqueResult();
    }

    @Transactional
    @EnforceAccessControl
    public Pool create(Pool entity) {

        /* Ensure all referenced PoolAttributes are correctly pointing to
         * this pool. This is useful for pools being created from
         * incoming json.
         */
        for (PoolAttribute attr : entity.getAttributes()) {
            attr.setPool(entity);
        }

        return super.create(entity);
    }
}
