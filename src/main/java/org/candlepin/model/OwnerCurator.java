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

import com.google.inject.persist.Transactional;

import org.hibernate.ReplicationMode;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * OwnerCurator
 */
public class OwnerCurator extends AbstractHibernateCurator<Owner> {

    protected OwnerCurator() {
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
    public List<Owner> lookupByKeys(Collection<String> keys) {
        return listByCriteria(
            createSecureCriteria().add(Restrictions.in("key", keys)));
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
    public List<Owner> lookupOwnersByActiveProduct(List<String> productIds) {
        // NOTE: only used by superadmin API calls, no permissions filtering needed here.
        DetachedCriteria poolIdQuery =
            DetachedCriteria.forClass(ProvidedProduct.class, "pp");
        poolIdQuery.add(Restrictions.in("pp.productId", productIds))
            .setProjection(Property.forName("pp.pool.id"));

        DetachedCriteria ownerIdQuery = DetachedCriteria.forClass(Entitlement.class, "e")
            .add(Subqueries.propertyIn("e.pool.id", poolIdQuery))
            .createCriteria("pool")
                .add(Restrictions.gt("endDate", new Date()))
            .setProjection(Property.forName("e.owner.id"));

        DetachedCriteria distinctQuery = DetachedCriteria.forClass(Owner.class, "o2")
            .add(Subqueries.propertyIn("o2.id", ownerIdQuery))
            .setProjection(Projections.distinct(Projections.property("o2.key")));

        return currentSession().createCriteria(Owner.class, "o")
            .add(Subqueries.propertyIn("o.key", distinctQuery))
            .list();
    }

    @SuppressWarnings("unchecked")
    public List<String> getConsumerUuids(String ownerKey) {
        DetachedCriteria ownerQuery =
            DetachedCriteria.forClass(Owner.class)
                .add(Restrictions.eq("key", ownerKey))
                .setProjection(Property.forName("id"));

        return this.currentSession().createCriteria(Consumer.class)
            .add(Subqueries.propertyEq("owner.id", ownerQuery))
            .setProjection(Property.forName("uuid"))
            .list();
    }
}
