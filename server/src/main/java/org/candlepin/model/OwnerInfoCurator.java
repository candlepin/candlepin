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

import com.google.inject.Provider;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.LikeExpression;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.type.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

/**
 * OwnerInfoCurator
 */
@Component
public class OwnerInfoCurator {
    private static Logger log = LoggerFactory.getLogger(OwnerInfoCurator.class);

    private Provider<EntityManager> entityManager;
    private ConsumerTypeCurator consumerTypeCurator;
    private ConsumerCurator consumerCurator;
    private PoolCurator poolCurator;

    @Autowired
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator,
        PoolCurator poolCurator) {
        this.entityManager = entityManager;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.poolCurator = poolCurator;
    }

    public OwnerInfo getByOwner(Owner owner) {
        OwnerInfo info = new OwnerInfo();
        Date now = new Date();

        // TODO:
        // Make sure this doesn't choke on MySQL, since we're doing queries with the cursor open.

        List<ConsumerType> types = consumerTypeCurator.listAll().list();
        HashMap<String, ConsumerType> typeHash = new HashMap<>();
        for (ConsumerType type : types) {
            // Store off the type for later use
            typeHash.put(type.getLabel(), type);

            // Do the real work
            int consumers = consumerCurator.getConsumerCount(owner, type);
            int entCount = consumerCurator.getConsumerEntitlementCount(owner, type);
            info.addTypeTotal(type, consumers, entCount);

            int count = getRequiresConsumerTypeCount(type, owner, now);
            info.addToConsumerTypeCountByPool(type, count);

            count = getEnabledConsumerTypeCount(type, owner, now);
            if (count > 0) {
                info.addToEnabledConsumerTypeCountByPool(type, count);
            }
        }

        int activePools = getActivePoolCount(owner, now);
        info.addDefaultEnabledConsumerTypeCount(activePools);

        Collection<String> families = getProductFamilies(owner, now);

        for (String family : families) {
            int virtualCount = getProductFamilyCount(owner, now, family, true);
            int totalCount = getProductFamilyCount(owner, now, family, false);
            info.addToEntitlementsConsumedByFamily(family, totalCount - virtualCount, virtualCount);
        }

        int virtTotalEntitlements = getProductFamilyCount(owner, now, null, true);
        int totalEntitlements = getProductFamilyCount(owner, now, null, false);

        info.addDefaultEntitlementsConsumedByFamily(
            totalEntitlements - virtTotalEntitlements,
            virtTotalEntitlements);

        setConsumerGuestCounts(owner, info);
        setConsumerCountsByComplianceStatus(owner, info);

        return info;
    }

    @SuppressWarnings("unchecked")
    private void setConsumerGuestCounts(Owner owner, OwnerInfo info) {
        Criteria cr = consumerCurator.createSecureCriteria()
            .createAlias("facts", "f")
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.ilike("f.indices", "virt.is_guest"))
            .add(Restrictions.ilike("f.elements", "true"))
            .setProjection(Projections.count("id"));

        int guestCount = ((Long) cr.uniqueResult()).intValue();

        Criteria totalConsumersCriteria = consumerCurator.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .setProjection(Projections.count("id"));

        int totalConsumers = ((Long) totalConsumersCriteria.uniqueResult()).intValue();
        int physicalCount = totalConsumers - guestCount;

        info.setGuestCount(guestCount);
        info.setPhysicalCount(physicalCount);
    }

    @SuppressWarnings("checkstyle:indentation")
    private void setConsumerCountsByComplianceStatus(Owner owner, OwnerInfo info) {
        Criteria countCriteria = consumerCurator.createSecureCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()))
            .add(Restrictions.isNotNull("entitlementStatus"))
            .setProjection(Projections.projectionList()
                .add(Projections.groupProperty("entitlementStatus"))
                .add(Projections.count("id")));

        List<Object[]> results = countCriteria.list();
        for (Object[] row : results) {
            String status = (String) row[0];
            Integer count = ((Long) row[1]).intValue();
            info.setConsumerCountByComplianceStatus(status, count);
        }
    }

    private int getActivePoolCount(Owner owner, Date date) {
        Criteria activePoolCountCrit = poolCurator.createSecureCriteria()
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date))
            .setProjection(Projections.count("id"));
        return ((Long) activePoolCountCrit.uniqueResult()).intValue();
    }

    @SuppressWarnings("checkstyle:indentation")
    private int getRequiresConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        Criteria criteria = poolCurator.createSecureCriteria("Pool")
            .createAlias("product", "Product")
            .setProjection(Projections.countDistinct("Pool.id"));

        criteria.add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date))
            .add(this.addAttributeFilterSubquery(Pool.Attributes.REQUIRES_CONSUMER_TYPE, Arrays.asList(
                type.getLabel()
            )));

        return ((Long) criteria.uniqueResult()).intValue();
    }

    @SuppressWarnings("checkstyle:indentation")
    private int getEnabledConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        Criteria criteria = poolCurator.createSecureCriteria("Pool")
            .createAlias("product", "Product")
            .setProjection(Projections.countDistinct("Pool.id"));

        criteria.add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date))
            .add(this.addAttributeFilterSubquery(Pool.Attributes.ENABLED_CONSUMER_TYPES, Arrays.asList(
                type.getLabel() + ",*", "*," + type.getLabel(), "*," + type.getLabel() + ",*", type.getLabel()
            )));

        return ((Long) criteria.uniqueResult()).intValue();
    }

    @SuppressWarnings("checkstyle:indentation")
    private Criterion addAttributeFilterSubquery(String key, Collection<String> values) {
        key = this.sanitizeMatchesFilter(key);

        // Find all pools which have the given attribute (and values) on a product, unless the pool
        // defines that same attribute
        DetachedCriteria poolAttrSubquery = DetachedCriteria.forClass(Pool.class, "PoolI")
            .createAlias("PoolI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Pool.id").eqProperty("PoolI.id"))
            .add(new CPLikeExpression("attrib.indices", key, '!', false));

        // Impl note:
        // The SQL restriction below uses some Hibernate magic value to get the pool ID from the
        // outer-most query. We can't use {alias} here, since we're basing the subquery on Product
        // instead of Pool to save ourselves an unnecessary join. Similarly, we use an SQL
        // restriction here because we can query the information we need, hitting only one table
        // with direct SQL, whereas the matching criteria query would end up requiring a minimum of
        // one join to get from pool to pool attributes.
        DetachedCriteria prodAttrSubquery = DetachedCriteria.forClass(Product.class, "ProdI")
            .createAlias("ProdI.attributes", "attrib")
            .setProjection(Projections.id())
            .add(Property.forName("Product.uuid").eqProperty("ProdI.uuid"))
            .add(new CPLikeExpression("attrib.indices", key, '!', false))
            .add(Restrictions.sqlRestriction(
                "NOT EXISTS (SELECT poolattr.pool_id FROM cp_pool_attribute poolattr " +
                "WHERE poolattr.pool_id = this_.id AND LOWER(poolattr.name) LIKE LOWER(?) ESCAPE '!')",
                key, StringType.INSTANCE
            ));

        if (values != null && !values.isEmpty()) {
            Disjunction poolAttrValueDisjunction = Restrictions.disjunction();
            Disjunction prodAttrValueDisjunction = Restrictions.disjunction();

            for (String attrValue : values) {
                if (attrValue == null || attrValue.isEmpty()) {
                    poolAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));

                    prodAttrValueDisjunction.add(Restrictions.isNull("attrib.elements"))
                        .add(Restrictions.eq("attrib.elements", ""));
                }
                else {
                    attrValue = this.sanitizeMatchesFilter(attrValue);
                    poolAttrValueDisjunction.add(
                        new CPLikeExpression("attrib.elements", attrValue, '!', true));
                    prodAttrValueDisjunction.add(
                        new CPLikeExpression("attrib.elements", attrValue, '!', true));
                }
            }

            poolAttrSubquery.add(poolAttrValueDisjunction);
            prodAttrSubquery.add(prodAttrValueDisjunction);
        }

        return Restrictions.or(
            Subqueries.exists(poolAttrSubquery),
            Subqueries.exists(prodAttrSubquery)
        );
    }

    private String sanitizeMatchesFilter(String matches) {
        StringBuilder output = new StringBuilder();
        boolean escaped = false;

        for (int index = 0; index < matches.length(); ++index) {
            char c = matches.charAt(index);

            switch (c) {
                case '!':
                case '_':
                case '%':
                    output.append('!').append(c);
                    break;

                case '\\':
                    if (escaped) {
                        output.append(c);
                    }

                    escaped = !escaped;
                    break;

                case '*':
                case '?':
                    if (!escaped) {
                        output.append(c == '*' ? '%' : '_');
                        break;
                    }

                default:
                    output.append(c);
                    escaped = false;
            }
        }

        return output.toString();
    }

    // TODO: Move this to the CPRestrictions class (as a pair of .like and .ilike methods) once
    // this branch and the branch that contain it are merged together.
    private static class CPLikeExpression extends LikeExpression {
        public CPLikeExpression(String property, String value, char escape, boolean ignoreCase) {
            super(property, value, escape, ignoreCase);
        }
    }

    private Collection<String> getProductFamilies(Owner owner, Date date) {
        Set<String> families = new HashSet<>();

        String queryStr = "select distinct value(attr) from Pool p " +
            "join p.attributes as attr " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and key(attr) = :attribute";

        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date)
            .setParameter("attribute", Pool.Attributes.PRODUCT_FAMILY);

        Iterator iter = query.iterate();
        while (iter.hasNext()) {
            String family = (String) iter.next();
            families.add(family);
        }

        queryStr = "select distinct value(prod) from Pool p " +
            "join p.product.attributes as prod " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and key(prod) = :attribute " +
            "and p not in (SELECT DISTINCT p2 FROM Pool p2 JOIN p2.attributes AS attr2 " +
            "    WHERE key(attr2) = :attribute)";

        query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date)
            .setParameter("attribute", Pool.Attributes.PRODUCT_FAMILY);

        iter = query.iterate();
        while (iter.hasNext()) {
            String family = (String) iter.next();
            families.add(family);
        }

        return families;
    }

    /*
     * If called with virt = false, returns the total number of all entitlements in
     * that family for that owner. So you probably want to subtract  the result of
     * virt=true from that.
     *
     * use family = null to get counts for all pools.
     */
    private int getProductFamilyCount(Owner owner, Date date, String family, boolean virt) {
        Criteria criteria = poolCurator.createSecureCriteria("Pool")
            .createAlias("entitlements", "Ent")
            .createAlias("product", "Product")
            .setProjection(Projections.sum("Ent.quantity"));

        criteria.add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date));

        if (family != null) {
            criteria.add(this.addAttributeFilterSubquery(
                Pool.Attributes.PRODUCT_FAMILY, Arrays.asList(family)
            ));
        }

        if (virt) {
            criteria.add(this.addAttributeFilterSubquery(Pool.Attributes.VIRT_ONLY, Arrays.asList("true")));
        }

        Long res = (Long) criteria.uniqueResult();
        return res != null ? res.intValue() : 0;
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }
}
