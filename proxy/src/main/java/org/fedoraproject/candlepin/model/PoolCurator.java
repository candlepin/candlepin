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
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.hibernate.Criteria;
import org.hibernate.Filter;
import org.hibernate.LockMode;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.impl.FilterImpl;

import com.google.inject.Inject;
import com.wideplay.warp.persist.Transactional;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    private static Logger log = Logger.getLogger(PoolCurator.class);
    private Enforcer enforcer;

    @Inject
    protected PoolCurator(Enforcer enforcer) {
        super(Pool.class);
        this.enforcer = enforcer;
    }
    
    @Override
    @Transactional
    @EnforceAccessControl
    public Pool find(Serializable id) {
        Pool pool = super.find(id);
        return pool;
    }
    
    @Override
    @Transactional
    @EnforceAccessControl
    public List<Pool> listAll() {
        List<Pool> pools = super.listAll();
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
        return listAvailableEntitlementPools(null, o, (String) null, null, true, false);
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
        return listAvailableEntitlementPools(c, c.getOwner(), (String) null, null,
            true, false);
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
        return listAvailableEntitlementPools(null, owner, productId, null, false, false);
    }

    /**
     * List entitlement pools.
     * 
     * Pools will be refreshed from the underlying subscription service.
     * 
     * If a consumer is specified, a pass through the rules will be done for
     * each potentially usable pool.
     * 
     * @param c Consumer being entitled.
     * @param o Owner whose subscriptions should be inspected.
     * @param productId only entitlements which provide this product are included.
     * @param activeOn Indicates to return only pools valid on this date.
     *        Set to null for no date filtering.
     * @param activeOnly if true, only active entitlements are included.
     * @param includeWarnings When filtering by consumer, include pools that
     *        triggered a rule warning. (errors will still be excluded)
     * @return List of entitlement pools.
     */
    @SuppressWarnings("unchecked")
    @Transactional
    @EnforceAccessControl
    public List<Pool> listAvailableEntitlementPools(Consumer c, Owner o,
            String productId, Date activeOn, boolean activeOnly, boolean includeWarnings) {

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

        if (activeOn != null) {
            crit.add(Restrictions.le("startDate", activeOn));
            crit.add(Restrictions.ge("endDate", activeOn));
        }

        // FIXME: sort by enddate?
        List<Pool> results = crit.list();
        
        if (results == null) {
            log.debug("no results");
            return new ArrayList<Pool>();
        }

        log.debug("active pools for owner: " + results.size());

        // crit.add(Restrictions.or(Restrictions.eq("productId", productId),
        // Restrictions.in("", results)))
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
        
        // If querying for pools available to a specific consumer, we need
        // to do a rules pass to verify the entitlement will be granted.
        // Note that something could change between the time we list a pool as
        // available, and the consumer requests the actual entitlement, and the
        // request still could fail.
        if (c != null) {
            List<Pool> newResults = new LinkedList<Pool>();
            log.debug("Filtering pools for consumer");
            for (Pool p : results) {
                PreEntHelper helper = enforcer.preEntitlement(c, p, 1);
                if (helper.getResult().isSuccessful() && 
                        (!helper.getResult().hasWarnings() || includeWarnings)) {
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
    
    @SuppressWarnings("unchecked")
    public List<Entitlement> retrieveFreeEntitlementsOfPool(Pool existingPool,
        boolean lifo) {
        return criteriaToSelectEntitlementForPool(existingPool)
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
    public Pool replicate(Pool pool) {
        for (ProvidedProduct pp : pool.getProvidedProducts()) {
            pp.setPool(pool);
        }

        for (PoolAttribute pa : pool.getAttributes()) {
            pa.setPool(pool);
        }

        pool.setSourceEntitlement(null);

        this.currentSession().replicate(pool, ReplicationMode.EXCEPTION);

        return pool;
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
    
    private static final String CONSUMER_FILTER = "Entitlement_CONSUMER_FILTER";
    
    public int getNoOfDependentEntitlements(String entitlementId) {
        Filter consumerFilter = disableConsumerFilter();
        Integer result = (Integer)
            currentSession().createCriteria(Entitlement.class)
                .setProjection(Projections.rowCount())
                .createCriteria("pool")
                    .createCriteria("sourceEntitlement")
                        .add(Restrictions.idEq(entitlementId))
                .list().get(0);
        enableIfPrevEnabled(consumerFilter);
        return result;
    }

    // TODO: watch out for performance. Should we limit the certificates
    // retrieved?
    @SuppressWarnings("unchecked")
    public List<EntitlementCertificate> retrieveEntCertsOfPoolsWithSourceEntitlement(
        String entId) {
        return
            currentSession().createCriteria(EntitlementCertificate.class)
            .createCriteria("entitlement")
                .createCriteria("pool")
                    .createCriteria("sourceEntitlement").add(Restrictions.idEq(entId))
            .list();
    }
    

    /**
     * @param session
     * @param consumerFilter
     */
    private void enableIfPrevEnabled(Filter consumerFilter) {
        //if filter was previously enabled, restore it.
        if (consumerFilter != null) {
            FilterImpl filterImpl = (FilterImpl) consumerFilter;
            Filter filter = currentSession().enableFilter(CONSUMER_FILTER);
            filter.setParameter("consumer_id", filterImpl.getParameter("consumer_id"));
        }
    }

    public Filter disableConsumerFilter() {
        Filter consumerFilter = currentSession().getEnabledFilter(CONSUMER_FILTER);
        currentSession().disableFilter(CONSUMER_FILTER);
        return consumerFilter;
    }

    public Pool lockAndLoad(Pool pool) {
        currentSession().refresh(pool, LockMode.UPGRADE);
        getEntityManager().refresh(pool);
        return pool;
    }

}
