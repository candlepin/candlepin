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

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.sql.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Singleton;



/**
 * OwnerCurator
 */
@Singleton
public class OwnerCurator extends AbstractHibernateCurator<Owner> {

    @Inject private CandlepinQueryFactory cpQueryFactory;
    private static Logger log = LoggerFactory.getLogger(OwnerCurator.class);

    public OwnerCurator() {
        super(Owner.class);
    }

    /**
     * Fetches the Owner for the specified ownerId. If the ownerId is null or owner was not found, this
     * method throws an exception.
     *
     * @param ownerId
     *  The ownerId for which to fetch a Owner object
     *
     * @throws IllegalArgumentException
     *  if ownerId is null
     *
     * @throws IllegalStateException
     *  if the owner was not found for the given id
     *
     * @return
     *  An Owner instance for the specified ownerId
     */
    public Owner findOwnerById(String ownerId) {
        if (StringUtils.isEmpty(ownerId)) {
            throw new IllegalArgumentException("ownerId is null");
        }

        Owner owner = this.get(ownerId);

        if (owner == null) {
            throw new IllegalStateException("owner not found for the id: " + ownerId);
        }

        return owner;
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
     * Fetches an owner by key securely by checking principal permissions.
     *
     * @param key owner's unique key to fetch.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner getByKeySecure(String key) {
        return (Owner) createSecureCriteria()
            .add(Restrictions.eq("key", key))
            .uniqueResult();
    }

    /**
     * Fetches and locks the owner by the owner key. If a matching owner could not be found, this
     * method returns null.
     *
     * @param key
     *  the key to use to lock and load the owner
     *
     * @return
     *  the locked owner, or null if a matching owner could not be found
     */
    public Owner lockAndLoadByKey(String key) {
        if (key != null && !key.isEmpty()) {
            return this.currentSession()
                .bySimpleNaturalId(Owner.class)
                .with(new LockOptions(LockMode.PESSIMISTIC_WRITE))
                .load(key);
        }

        return null;
    }

    /**
     * Fetches an owner by natural id (key). This method is non-secure.
     *
     * @param key owner's unique key to fetch.
     * @return the owner whose key matches the one given.
     */
    @Transactional
    public Owner getByKey(String key) {
        return this.currentSession().bySimpleNaturalId(Owner.class).load(key);
    }

    @Transactional
    public CandlepinQuery<Owner> getByKeys(Collection<String> keys) {
        DetachedCriteria criteria = this.createSecureDetachedCriteria()
            .add(CPRestrictions.in("key", keys));

        return this.cpQueryFactory.<Owner>buildQuery(this.currentSession(), criteria);
    }

    public Owner getByUpstreamUuid(String upstreamUuid) {
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
    public CandlepinQuery<Owner> getOwnersByActiveProduct(Collection<String> productIds) {
        // NOTE: only used by superadmin API calls, no permissions filtering needed here.
        DetachedCriteria poolIdQuery = DetachedCriteria.forClass(Pool.class, "pool")
            .createAlias("pool.product", "product")
            .createAlias("product.providedProducts", "providedProducts")
            .add(CPRestrictions.in("providedProducts.id", productIds))
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

        return this.cpQueryFactory.<Owner>buildQuery(this.currentSession(), criteria);
    }

    /**
     * Retrieves a list of owners which have pools referencing one or more of the specified product
     * IDs. If no owners are associated with the given products, this method returns an empty list.
     *
     * @param productIds
     *  The product IDs for which to retrieve owners
     *
     * @return
     *  a list of owners associated with the specified products
     */
    @SuppressWarnings("checkstyle:indentation")
    public CandlepinQuery<Owner> getOwnersWithProducts(Collection<String> productIds) {
        if (productIds != null && !productIds.isEmpty()) {
            Session session = this.currentSession();

            // Impl note:
            // We have to break this up into two queries for proper cursor and pagination support.
            // Hibernate currently has two nasty "features" which break these in their own special
            // way:
            // - Distinct, when applied in any way outside of direct SQL, happens in Hibernate
            //   *after* the results are pulled down, if and only if the results are fetched as a
            //   list. The filtering does not happen when the results are fetched with a cursor.
            // - Because result limiting (first+last result specifications) happens at the query
            //   level and distinct filtering does not, cursor-based pagination breaks due to
            //   potential results being removed after a page of results is fetched.
            Criteria idCriteria = session.createCriteria(Owner.class, "Owner")
                .setProjection(Projections.distinct(Projections.id()))
                .createAlias("Owner.pools", "Pool")
                .createAlias("Pool.product", "Prod", JoinType.LEFT_OUTER_JOIN)
                .createAlias("Pool.derivedProduct", "DProd", JoinType.LEFT_OUTER_JOIN)
                .createAlias("Prod.providedProducts", "PProd", JoinType.LEFT_OUTER_JOIN)
                .createAlias("DProd.providedProducts", "DPProd", JoinType.LEFT_OUTER_JOIN)
                .add(Restrictions.or(
                    CPRestrictions.in("Prod.id", productIds),
                    CPRestrictions.in("DProd.id", productIds),
                    CPRestrictions.in("PProd.id", productIds),
                    CPRestrictions.in("DPProd.id", productIds)
                ));

            List<String> ownerIds = idCriteria.list();

            if (ownerIds != null && !ownerIds.isEmpty()) {
                DetachedCriteria criteria = DetachedCriteria.forClass(Owner.class)
                    .add(CPRestrictions.in("id", ownerIds));

                return this.cpQueryFactory.<Owner>buildQuery(session, criteria);
            }
        }

        return this.cpQueryFactory.<Owner>buildQuery();
    }

    public CandlepinQuery<String> getConsumerIds(Owner owner) {
        return this.getConsumerIds(owner.getId());
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<String> getConsumerIds(String ownerId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Restrictions.eq("ownerId", ownerId))
            .setProjection(Property.forName("id"));

        return this.cpQueryFactory.<String>buildQuery(this.currentSession(), criteria);
    }

    public CandlepinQuery<String> getConsumerUuids(Owner owner) {
        return this.getConsumerUuids(owner.getId());
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<String> getConsumerUuids(String ownerId) {
        DetachedCriteria criteria = DetachedCriteria.forClass(Consumer.class)
            .add(Restrictions.eq("ownerId", ownerId))
            .setProjection(Property.forName("uuid"));

        return this.cpQueryFactory.<String>buildQuery(this.currentSession(), criteria);
    }
}

