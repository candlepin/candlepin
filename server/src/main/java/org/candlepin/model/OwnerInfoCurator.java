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
    private ConsumerCurator consumerCurator;
    private PoolCurator poolCurator;

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerCurator consumerCurator, ConsumerTypeCurator consumerTypeCurator,
        PoolCurator poolCurator) {
        this.entityManager = entityManager;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.poolCurator = poolCurator;
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

    @SuppressWarnings("unchecked")
    private void setConsumerGuestCounts(Owner owner, OwnerInfo info) {
        Criteria cr = consumerCurator.createSecureCriteria()
            .createAlias("facts", "f")
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.ilike("f.indices", "virt.is_guest"))
            .add(Restrictions.ilike("f.elements", "true"))
            .setProjection(Projections.count("id"));

        int guestCount = ((Long) cr.uniqueResult()).intValue();

        Criteria totalConsumersCriteria = consumerCurator.createSecureCriteria()
            .add(Restrictions.eq("owner", owner))
            .setProjection(Projections.count("id"));

        int totalConsumers = ((Long) totalConsumersCriteria.uniqueResult()).intValue();
        int physicalCount = totalConsumers - guestCount;

        info.setGuestCount(guestCount);
        info.setPhysicalCount(physicalCount);
    }

    private void setConsumerCountsByComplianceStatus(Owner owner, OwnerInfo info) {
        // We exclude the following types since they are fake/transparent consumers
        // and we do not want them included in the totals.
        String[] typesToFilter = new String[]{"uebercert"};

        Criteria countCriteria = consumerCurator.createSecureCriteria()
            .createAlias("type", "t")
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.isNotNull("entitlementStatus"))
            .add(Restrictions.not(Restrictions.in("t.label", typesToFilter)))
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

    private int getRequiresConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        PoolFilterBuilder filterBuilder = new PoolFilterBuilder();
        filterBuilder.addAttributeFilter("requires_consumer_type", type.getLabel());
        Criteria typeCountCrit = poolCurator.createSecureCriteria()
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date))
            .setProjection(Projections.count("id"));
        filterBuilder.applyTo(typeCountCrit);

        return ((Long) typeCountCrit.uniqueResult()).intValue();
    }

    private int getEnabledConsumerTypeCount(ConsumerType type, Owner owner, Date date) {
        PoolFilterBuilder filterBuilder = new PoolFilterBuilder();
        filterBuilder.addAttributeFilter("enabled_consumer_types", type.getLabel() + ",*");
        filterBuilder.addAttributeFilter("enabled_consumer_types", "*," + type.getLabel() + ",*");
        filterBuilder.addAttributeFilter("enabled_consumer_types", "*," + type.getLabel());
        filterBuilder.addAttributeFilter("enabled_consumer_types", type.getLabel());
        Criteria enabledCountCrit = poolCurator.createSecureCriteria()
            .add(Restrictions.eq("owner", owner))
            .add(Restrictions.le("startDate", date))
            .add(Restrictions.ge("endDate", date))
            .setProjection(Projections.countDistinct("id"));
        filterBuilder.applyTo(enabledCountCrit);

        return ((Long) enabledCountCrit.uniqueResult()).intValue();
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
