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

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;

import org.candlepin.policy.js.ReadOnlyProduct;
import org.candlepin.policy.js.ReadOnlyProductCache;
import org.candlepin.service.ProductServiceAdapter;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * OwnerInfoCurator
 */

public class OwnerInfoCurator {
    private Provider<EntityManager> entityManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private ProductServiceAdapter productAdapter;
    private static final String DEFAULT_CONSUMER_TYPE = "system";
    private StatisticCuratorQueries statisticCuratorQueries;

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerTypeCurator consumerTypeCurator, ProductServiceAdapter psa,
        StatisticCuratorQueries statisticCuratorQueries) {
        this.entityManager = entityManager;
        this.consumerTypeCurator = consumerTypeCurator;
        this.productAdapter = psa;
        this.statisticCuratorQueries = statisticCuratorQueries;
    }

    public OwnerInfo lookupByOwner(Owner owner) {
        ReadOnlyProductCache productCache = new ReadOnlyProductCache(this.productAdapter);
        OwnerInfo info = new OwnerInfo();

        List<ConsumerType> types = consumerTypeCurator.listAll();
        HashMap<String, ConsumerType> typeHash = new HashMap<String, ConsumerType>();
        for (ConsumerType type : types) {
            // Store off the type for later use
            typeHash.put(type.getLabel(), type);

            // Do the real work
            Criteria c = currentSession().createCriteria(Consumer.class)
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("type", type));
            c.setProjection(Projections.rowCount());
            int consumers = (Integer) c.uniqueResult();

            c = currentSession().createCriteria(Entitlement.class)
                .setProjection(Projections.sum("quantity"))
                .createCriteria("consumer")
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("type", type));

            // If there's no rows summed, quantity returns null.
            Object result = c.uniqueResult();
            int entitlements = 0;
            if (result != null) {
                entitlements = (Integer) result;
            }

            info.addTypeTotal(type, consumers, entitlements);
        }

        Date now = new Date();
        info.setConsumerTypesByPool(types);
        List<Statistic> totalCount = statisticCuratorQueries.getStatisticsByOwner(owner,
            "TOTALSUBSCRIPTIONCOUNT", null, null, null, null);
        info.setTotalSubscriptionCount(totalCount);
        info.setTotalSubscriptionsConsumed(statisticCuratorQueries
            .getStatisticsByOwner(owner, "TOTALSUBSCRIPTIONSCONSUMED",
                   null, null, null, null));

        for (Pool pool : owner.getPools()) {
            String productId = pool.getProductId();

            // clients using the ownerinfo details are only concerned with pools
            // active *right now*
            if (now.before(pool.getStartDate()) || now.after(pool.getEndDate())) {
                continue;
            }

            if (info.getPoolNearestToExpiry() == null) {
                info.setPoolNearestToExpiry(pool);
            }
            else if (pool.getEndDate().before(info.getPoolNearestToExpiry()
                             .getEndDate())) {
                info.setPoolNearestToExpiry(pool);
            }

            // do consumerTypeCountByPool
            ReadOnlyProduct product = productCache.getProductById(productId);
            String consumerType = getAttribute(pool, product, "requires_consumer_type");
            if (consumerType == null || consumerType.trim().equals("")) {
                consumerType = DEFAULT_CONSUMER_TYPE;
            }
            ConsumerType ct = typeHash.get(consumerType);
            info.addToConsumerTypeCountByPool(ct);

            consumerType = getAccumulatedAttribute(pool, product, "enabled_consumer_types");
            if (consumerType != null && !consumerType.trim().equals("")) {
                for (String type : consumerType.split(",")) {
                    ct = typeHash.get(type);
                    if (ct != null) {
                        info.addToEnabledConsumerTypeCountByPool(ct);
                    }
                }
            }

            // now do entitlementsConsumedByFamily
            String productFamily = getAttribute(pool, product, "product_family");
            // default bucket for familyless entitlements
            if (productFamily == null || productFamily.trim().equals("")) {
                productFamily = "none";
            }

            int count = getEntitlementCountForPool(pool);

            if ("true".equals(getAttribute(pool, product, "virt_only"))) {
                info.addToEntitlementsConsumedByFamily(productFamily, 0, count);
            }
            else {
                info.addToEntitlementsConsumedByFamily(productFamily, count, 0);
            }
        }

        setConsumerGuestCounts(owner, info);
        setConsumerCountsByComplianceStatus(owner, info);

        return info;
    }

    /**
     * @param pool
     * @return
     */
    private String getAttribute(Pool pool, ReadOnlyProduct product, String attribute) {
        // XXX dealing with attributes in java. that's bad!
        String productFamily = pool.getAttributeValue(attribute);
        if (productFamily == null || productFamily.trim().equals("")) {
            if (product != null) {
                productFamily = product.getAttribute(attribute);
            }
        }
        return productFamily;
    }

    /**
     * @param pool
     * @return
     */
    private String getAccumulatedAttribute(Pool pool, ReadOnlyProduct product,
                                           String aType) {
        // XXX dealing with attributes in java. that's bad!
        String consumerTypes = pool.getAttributeValue(aType);
        if (product != null) {
            if (consumerTypes == null || consumerTypes.length() > 0) {
                consumerTypes += ",";
            }
            consumerTypes += product.getAttribute(aType);
        }
        return consumerTypes;
    }


    private void setConsumerGuestCounts(Owner owner, OwnerInfo info) {

        String guestQueryStr = "select count(c) from Consumer c join c.facts as fact " +
            "where c.owner = :owner and index(fact) = 'virt.is_guest' and fact = 'true'";
        Query guestQuery = currentSession().createQuery(guestQueryStr)
            .setEntity("owner", owner);
        Integer guestCount = ((Long) guestQuery.iterate().next()).intValue();
        info.setGuestCount(guestCount);

        /*
         * Harder to query for all consumers without this fact, or with fact set to false,
         * so we'll assume all owner consumers, minus the value above is the count of
         * non-guest consumers.
         *
         * This also assumes non-system consumers will be counted as physical. (i.e.
         * person/domain consumers who do not have this fact set at all)
         */
        String physicalQueryStr = "select count(c) from Consumer c where owner = :owner";
        Query physicalQuery = currentSession().createQuery(physicalQueryStr)
            .setEntity("owner", owner);
        Integer physicalCount = ((Long) physicalQuery.iterate().next()).intValue() -
            guestCount;
        info.setPhysicalCount(physicalCount);
    }

    private int getEntitlementCountForPool(Pool pool) {
        String queryStr = "select sum(e.quantity) from Entitlement e " +
            "where e.pool = :pool";
        Query query = currentSession().createQuery(queryStr)
            .setEntity("pool", pool);
        Long count = (Long) query.uniqueResult();
        if (count == null) {
            return 0;
        }
        else {
            return count.intValue();
        }
    }

    private void setConsumerCountsByComplianceStatus(Owner owner, OwnerInfo info) {
        String queryStr = "select c.entitlementStatus, count(c) from Consumer c where " +
            "c.owner = :owner and c.entitlementStatus is not null " +
            "and c.type.label not in (:typesToFilter) " +
            "group by c.entitlementStatus";

        // We exclude the following types since they are fake/transparent consumers
        // and we do not want them included in the totals.
        String[] typesToFilter = new String[]{"uebercert"};
        Query consumerQuery = currentSession().createQuery(queryStr)
            .setEntity("owner", owner);
        consumerQuery.setParameterList("typesToFilter", typesToFilter);

        Iterator iter = consumerQuery.iterate();
        while (iter.hasNext()) {
            Object[] object = (Object[]) iter.next();
            String status = (String) object[0];
            Integer count = ((Long) object[1]).intValue();
            info.setConsumerCountByComplianceStatus(status, count);
        }
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }
}
