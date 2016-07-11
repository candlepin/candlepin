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

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.hibernate.Criteria;
import org.hibernate.ReplicationMode;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.sql.JoinType;

import java.util.Collection;
import java.util.Date;



/**
 * OwnerCurator
 */
public class OwnerCurator extends AbstractHibernateCurator<Owner> {

    @Inject private CandlepinQueryFactory cpQueryFactory;

    public OwnerCurator() {
        super(Owner.class);
    }

    @Transactional
    public Owner replicate(Owner owner) {
        this.currentSession().replicate(owner, ReplicationMode.EXCEPTION);

        return owner;
    }

    @Transactional
    @Override
    public Owner create(Owner entity) {
        return super.create(entity);
    }

    /**
     * @param key owner's unique key to lookup.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner lookupByKey(String key) {
        return (Owner) createSecureCriteria()
            .add(Restrictions.eq("key", key))
            .uniqueResult();
    }

    @Transactional
    public CandlepinQuery<Owner> lookupByKeys(Collection<String> keys) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(CPRestrictions.in("key", keys));

        return this.cpQueryFactory.<Owner>buildCandlepinQuery(this.currentSession(), criteria);
    }

    public Owner lookupWithUpstreamUuid(String upstreamUuid) {
        return (Owner) createSecureCriteria()
            .createCriteria("upstreamConsumer")
            .add(Restrictions.eq("uuid", upstreamUuid))
            .uniqueResult();
    }

    /**
     * Note that this query looks up only provided products.
     * @param productIds
     * @return a list of owners
     */
    public CandlepinQuery<Owner> lookupOwnersByActiveProduct(Collection<String> productIds) {
        // NOTE: only used by superadmin API calls, no permissions filtering needed here.
        DetachedCriteria poolIdQuery = DetachedCriteria.forClass(Pool.class, "pool");
        poolIdQuery.createAlias("pool.providedProducts", "providedProducts");

        poolIdQuery.add(CPRestrictions.in("providedProducts.id", productIds))
            .setProjection(Property.forName("pool.id"));

        DetachedCriteria ownerIdQuery = DetachedCriteria.forClass(Entitlement.class, "e")
            .add(Subqueries.propertyIn("e.pool.id", poolIdQuery))
            .createCriteria("pool").add(Restrictions.gt("endDate", new Date()))
            .setProjection(Property.forName("e.owner.id"));

        DetachedCriteria distinctQuery = DetachedCriteria.forClass(Owner.class, "o2")
            .add(Subqueries.propertyIn("o2.id", ownerIdQuery))
            .setProjection(Projections.distinct(Projections.property("o2.key")));

        DetachedCriteria criteria = DetachedCriteria.forClass(Owner.class, "o")
            .add(Subqueries.propertyIn("o.key", distinctQuery));

        return this.cpQueryFactory.<Owner>buildCandlepinQuery(this.currentSession(), criteria);
    }

    /**
     * Retrieves a list of owners which have pools referencing the specified product IDs. If no
     * owners are associated with the given products, this method returns an empty list.
     *
     * @param productIds
     *  The product IDs for which to retrieve owners
     *
     * @return
     *  a list of owners associated with the specified products
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Owner> lookupOwnersWithProduct(Collection<String> productIds) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Owner.class, "Owner")
            .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY)
            .createAlias("Owner.pools", "Pool")
            .createAlias("Pool.product", "Prod", JoinType.LEFT_OUTER_JOIN)
            .createAlias("Pool.derivedProduct", "DProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("Pool.providedProducts", "PProd", JoinType.LEFT_OUTER_JOIN)
            .createAlias("Pool.derivedProvidedProducts", "DPProd", JoinType.LEFT_OUTER_JOIN)
            .add(Restrictions.or(
                CPRestrictions.in("Prod.id", productIds),
                CPRestrictions.in("DProd.id", productIds),
                CPRestrictions.in("PProd.id", productIds),
                CPRestrictions.in("DPProd.id", productIds)
            ));

        return this.cpQueryFactory.<Owner>buildCandlepinQuery(this.currentSession(), criteria);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<String> getConsumerUuids(String ownerKey) {
        DetachedCriteria ownerQuery = DetachedCriteria.forClass(Owner.class).add(
            Restrictions.eq("key", ownerKey)).setProjection(Property.forName("id"));

        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Subqueries.propertyEq("owner.id", ownerQuery))
            .setProjection(Property.forName("uuid"));

        return this.cpQueryFactory.<String>buildCandlepinQuery(this.currentSession(), criteria);
    }
}
