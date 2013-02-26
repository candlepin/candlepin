/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.criteria.CriteriaRules;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.hibernate.Criteria;
import org.hibernate.Filter;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.impl.FilterImpl;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.Transactional;

/**
 * EntitlementPoolCurator
 */
public class PoolCurator extends AbstractHibernateCurator<Pool> {

    private static Logger log = Logger.getLogger(PoolCurator.class);
    private Enforcer enforcer;
    private CriteriaRules poolCriteria;
    @Inject
    protected Injector injector;

    @Inject
    protected ProductCache productCache;

    @Inject
    protected PoolCurator(Enforcer enforcer, CriteriaRules poolCriteria) {
        super(Pool.class);
        this.enforcer = enforcer;
        this.poolCriteria = poolCriteria;
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
        return listByOwner(o, null);
    }

    /**
     * Returns list of pools owned by the given Owner.
     * @param o Owner to filter
     * @param activeOn only include pools active on the given date.
     * @return pools owned by the given Owner.
     */
    @Transactional
    @EnforceAccessControl
    public List<Pool> listByOwner(Owner o, Date activeOn) {
        return listAvailableEntitlementPools(null, o, null, activeOn, true, false);
    }

    /**
     * Return all pools referencing the given entitlement as their source entitlement.
     *
     * @param e Entitlement
     * @return Pools created as a result of this entitlement.
     */
    public List<Pool> listBySourceEntitlement(Entitlement e) {
        List<Pool> results = currentSession().createCriteria(Pool.class)
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

            // Ask the rules for any business logic criteria to filter with for
            // this consumer
            List<Criterion> filterCriteria = poolCriteria.availableEntitlementCriteria(
                c);

            if (log.isDebugEnabled()) {
                log.debug("Got " + filterCriteria.size() +
                    "  query filters from database.");
            }

            for (Criterion rulesCriteria : filterCriteria) {
                crit.add(rulesCriteria);
            }
        }
        if (o != null) {
            crit.add(Restrictions.eq("owner", o));
            crit.add(Restrictions.ne("productName", Product.ueberProductNameForOwner(o)));
        }
        if (activeOn != null) {
            crit.add(Restrictions.le("startDate", activeOn));
            crit.add(Restrictions.ge("endDate", activeOn));
        }

        // FIXME: sort by enddate?
        List<Pool> results = crit.list();

        if (log.isDebugEnabled()) {
            log.debug("Loaded " + results.size() + " pools from database.");
        }

        if (results == null) {
            log.debug("no results");
            return new ArrayList<Pool>();
        }

        if (log.isDebugEnabled()) {
            log.debug("active pools for owner: " + results.size());
        }

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
                        log.debug("Pool provides " + productId + ": " + p);
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
            for (Pool p : results) {
                ValidationResult result = enforcer.preEntitlement(c, p, 1);
                if (result.isSuccessful() && (!result.hasWarnings() || includeWarnings)) {
                    newResults.add(p);
                }
                else {
                    log.info("Omitting pool due to failed rule check: " + p.getId());
                    if (result.hasErrors()) {
                        log.info("\tErrors: " + result.getErrors());
                    }
                    if (result.hasWarnings()) {
                        log.info("\tWarnings: " + result.getWarnings());
                    }
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

    @Transactional
    @EnforceAccessControl
    public Pool findUeberPool(Owner o) {
        return (Pool) currentSession()
            .createCriteria(Pool.class)
            .add(Restrictions.eq("owner", o))
            .add(Restrictions.eq("productName", Product.ueberProductNameForOwner(o)))
            .uniqueResult();
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

    public List<Pool> lookupBySubscriptionId(String subId) {
        return currentSession().createCriteria(Pool.class)
        .add(Restrictions.eq("subscriptionId", subId)).list();
    }

    /**
     * Attempts to find pools which are oversubscribed after the creation or modification
     * of the given entitlement.
     *
     * To do this we search for only the pools related to the subscription ID which
     * could have changed, the two cases where this can happen are:
     *
     * 1. Bonus pool (not derived from any entitlement) after a bind. (in cases such as
     * exporting to downstream)
     * 2. A derived pool whose source entitlment just had it's quantity reduced.
     *
     * This has to be done carefully to avoid potential performance problems with
     * virt_bonus on-site subscriptions where one pool is created per physical
     * entitlement.
     *
     * @param subId Subscription ID of the pool.
     * @param ent Entitlement just created or modified.
     * @return Pools with too many entitlements for their new quantity.
     */
    public List<Pool> lookupOversubscribedBySubscriptionId(String subId, Entitlement ent) {
        String queryString = "from Pool as pool " +
            "where pool.subscriptionId = :subId AND " +
            "(pool.sourceEntitlement = null OR pool.sourceEntitlement = :ent) AND " +
            "pool.quantity >= 0 AND " +
            "pool.consumed > pool.quantity ";
        Query query = currentSession().createQuery(queryString);
        query.setString("subId", subId);
        query.setEntity("ent", ent);
        return query.list();
    }

    @Transactional
    public Pool replicate(Pool pool) {
        for (ProvidedProduct pp : pool.getProvidedProducts()) {
            pp.setPool(pool);
        }

        for (PoolAttribute pa : pool.getAttributes()) {
            pa.setPool(pool);
        }

        for (ProductPoolAttribute ppa : pool.getProductAttributes()) {
            ppa.setPool(pool);
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

    public List<ActivationKey> getActivationKeysForPool(Pool p) {
        List<ActivationKey> activationKeys = new ArrayList<ActivationKey>();
        List<ActivationKeyPool> activationKeyPools = currentSession().createCriteria(
            ActivationKeyPool.class).add(Restrictions.eq("pool", p)).list();

        for (ActivationKeyPool akp : activationKeyPools) {
            activationKeys.add(akp.getKey());
        }

        return activationKeys;
    }

    /**
     * Method to compile service/support level lists. One is the available levels for
     *  consumers for this owner. The second is the level names that are exempt. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param owner The owner that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    public Set<String> retrieveServiceLevelsForOwner(Owner owner, boolean exempt) {
        String stmt = "select distinct a.name, a.value, a.productId " +
                        "from ProductPoolAttribute as a " +
                        "inner join a.pool as p " +
                        "where p.owner.id = :owner_id and " +
                        "(a.name = 'support_level' or a.name='support_level_exempt') " +
                        "order by a.name DESC";

        Query q = currentSession().createQuery(stmt);
        q.setParameter("owner_id", owner.getId());
        List<Object[]> results = q.list();

        // Use case insensitive comparison here, since we treat
        // Premium the same as PREMIUM or premium, to make it easier for users to specify
        // a level on the cli. However, use the original case, since Premium is more
        // attractive than PREMIUM.
        Set<String> slaSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        Set<String> exemptSlaSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        Set<String> exemptProductIds = new HashSet<String>();

        for (Object[] result : results) {
            String name = (String) result[0];
            String value = (String) result[1];
            String productId = (String) result[2];

            if ("support_level_exempt".equals(name) && "true".equalsIgnoreCase(value)) {
                exemptProductIds.add(productId);
            }
            else if ("support_level".equalsIgnoreCase(name) &&
                (value != null && !value.trim().equals(""))) {
                if (exemptProductIds.contains(productId)) {
                    exemptSlaSet.add(value);
                }
            }
        }

        for (Object[] result : results) {
            String name = (String) result[0];
            String value = (String) result[1];

            if (!"support_level_exempt".equals(name)) {
                if (!exemptSlaSet.contains(value)) {
                    slaSet.add(value);
                }
            }
        }

        if (exempt) {
            return exemptSlaSet;
        }
        return slaSet;
    }

    /**
     * There is an issue with attributes in the database. Duplicate attribute
     *  name/value pairs were showing up from outside the Candlepin code.
     *  Only one was getting pulled into the HashSet and the other ignored.
     *  Uses explicit lookup and deletion instead of the Hibernate cascade which
     *  relies on the data structure in memory.
     *
     * @param entity pool to be deleted.
     */
    @Transactional
    @EnforceAccessControl
    public void delete(Pool entity) {
        Pool toDelete = find(entity.getId());

        ProductPoolAttributeCurator ppac = injector
                                   .getInstance(ProductPoolAttributeCurator.class);
        List<ProductPoolAttribute> ppa = currentSession()
                                          .createCriteria(ProductPoolAttribute.class)
                                          .add(Restrictions.eq("pool", entity)).list();
        for (ProductPoolAttribute att : ppa) {
            ppac.delete(att);

        }
        entity.getProductAttributes().clear();

        PoolAttributeCurator pac = injector.getInstance(PoolAttributeCurator.class);
        List<PoolAttribute> pa = currentSession()
                                          .createCriteria(PoolAttribute.class)
                                          .add(Restrictions.eq("pool", entity)).list();
        for (PoolAttribute att : pa) {
            pac.delete(att);
        }
        entity.getAttributes().clear();

        currentSession().delete(toDelete);
    }
}
