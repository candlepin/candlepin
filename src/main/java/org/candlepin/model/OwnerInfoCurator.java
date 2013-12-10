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
import com.google.inject.Provider;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

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

public class OwnerInfoCurator {
    private Provider<EntityManager> entityManager;
    private ConsumerTypeCurator consumerTypeCurator;

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerTypeCurator consumerTypeCurator) {
        this.entityManager = entityManager;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    public OwnerInfo lookupByOwner(Owner owner) {
        OwnerInfo info = new OwnerInfo();
        Date now = new Date();

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
            int consumers = ((Long) c.uniqueResult()).intValue();

            c = currentSession().createCriteria(Entitlement.class)
                .setProjection(Projections.sum("quantity"))
                .createCriteria("consumer")
                .add(Restrictions.eq("owner", owner))
                .add(Restrictions.eq("type", type));

            // If there's no rows summed, quantity returns null.
            Object result = c.uniqueResult();
            int entitlements = 0;
            if (result != null) {
                entitlements = ((Long) result).intValue();
            }
            info.addTypeTotal(type, consumers, entitlements);

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
            info.addToEntitlementsConsumedByFamily(family, totalCount - virtualCount,
                virtualCount);
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

    private int getActivePoolCount(Owner owner, Date date) {
        String queryStr = "select count (p) from Pool p " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date";
        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date);
        return ((Long) query.uniqueResult()).intValue();
    }

    private int getRequiresConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        String queryStr = "select count (p) from Pool p join p.attributes as attr " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and attr.name = 'requires_consumer_type'" +
            "and attr.value = :type";
        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("type", type.getLabel())
            .setParameter("date", date);
        int count = ((Long) query.uniqueResult()).intValue();

        queryStr = "select count (p) from Pool p join p.productAttributes as prod " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and prod.name = 'requires_consumer_type'" +
            "and prod.value = :type " +
            "and p not in (select distinct pa.pool from PoolAttribute pa " +
            "              where pa.name = 'requires_consumer_type')";
        query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("type", type.getLabel())
            .setParameter("date", date);

        return count + ((Long) query.uniqueResult()).intValue();
    }

    private int getEnabledConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        String queryStr = "select count(distinct p) from Pool p " +
            "join p.attributes as attr " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and attr.name = 'enabled_consumer_types' and (" +
            "             attr.value = :single " +
            "             or attr.value LIKE :begin " +
            "             or attr.value LIKE :middle " +
            "             or attr.value LIKE :end" +
            ")";
        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date)
            .setParameter("begin", type.getLabel() + ",%")
            .setParameter("middle", "%," + type.getLabel() + ",%")
            .setParameter("end", "%," + type.getLabel())
            .setParameter("single", type.getLabel());
        int count = ((Long) query.uniqueResult()).intValue();

        queryStr = "select count(distinct p) from Pool p " +
            "join p.productAttributes as prod " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and prod.name = 'enabled_consumer_types' and (" +
            "             prod.value = :single " +
            "             or prod.value LIKE :begin " +
            "             or prod.value LIKE :middle " +
            "             or prod.value LIKE :end" +
            ") " +
            "and p not in (select distinct pa.pool from PoolAttribute pa " +
            "               where pa.name = 'enabled_consumer_types')";
        query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date)
            .setParameter("begin", type.getLabel() + ",%")
            .setParameter("middle", "%," + type.getLabel() + ",%")
            .setParameter("end", "%," + type.getLabel())
            .setParameter("single", type.getLabel());

        return count + ((Long) query.uniqueResult()).intValue();
    }

    private Collection<String> getProductFamilies(Owner owner, Date date) {
        Set<String> families = new HashSet<String>();

        String queryStr = "select distinct attr.value from Pool p " +
            "join p.attributes as attr " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and attr.name = 'product_family'";
        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date);

        Iterator iter = query.iterate();
        while (iter.hasNext()) {
            String family = (String) iter.next();
            families.add(family);
        }

        queryStr = "select distinct prod.value from Pool p " +
            "join p.productAttributes as prod " +
            "where p.owner = :owner " +
            "and p.startDate < :date and p.endDate > :date " +
            "and prod.name = 'product_family' " +
            "and p not in (select distinct pa.pool from PoolAttribute pa" +
            "              where pa.name = 'product_family')";
        query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date);

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
        String queryStr = "select sum(ent.quantity) from Pool p" +
            "              join p.entitlements as ent " +
            "              where p.owner = :owner " +
            "              and p.startDate < :date and p.endDate > :date ";

        if (family != null) {
            queryStr +=
                "and (p in (select p from Pool p join p.attributes as attr " +
                "           where p.owner = :owner " +
                "           and attr.name = 'product_family' and attr.value = :family)" +
                "     or (p in (select p from Pool p join p.productAttributes as prod " +
                "              where p.owner = :owner " +
                "              and prod.name = 'product_family' " +
                "              and prod.value = :family) " +
                "         and p not in (select p from Pool p join p.attributes as attr " +
                "                       where p.owner = :owner " +
                "                       and attr.name = 'product_family')" +
                "        )" +
                ")";
        }

        if (virt) {
            queryStr += "and (p in (select p from Pool p join p.attributes as attr " +
                        "           where attr.name = 'virt_only' " +
                        "           and attr.value = 'true') " +
                        "     or (p in (select p from Pool p " +
                        "               join p.productAttributes as prod " +
                        "               where p.owner = :owner " +
                        "               and prod.name = 'virt_only' " +
                        "               and prod.value = 'true')" +
                        "         and p not in (select p from Pool p " +
                        "                       join p.attributes as attr " +
                        "                       where p.owner = :owner " +
                        "                       and attr.name = 'virt_only')" +
                        "     )" +
                        ")";
        }
        Query query = currentSession().createQuery(queryStr)
            .setEntity("owner", owner)
            .setParameter("date", date);
        if (family != null) {
            query.setParameter("family", family);
        }
        Long res = (Long) query.uniqueResult();
        if (res == null) {
            return 0;
        }

        return res.intValue();
    }

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }
}
