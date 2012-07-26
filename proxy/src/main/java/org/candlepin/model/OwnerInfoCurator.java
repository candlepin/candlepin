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
package org.candlepin.model;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;

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

    @Inject
    public OwnerInfoCurator(Provider<EntityManager> entityManager,
        ConsumerTypeCurator consumerTypeCurator) {
        this.entityManager = entityManager;
        this.consumerTypeCurator = consumerTypeCurator;
    }

    public OwnerInfo lookupByOwner(Owner owner) {
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

    protected Session currentSession() {
        Session sess = (Session) entityManager.get().getDelegate();
        return sess;
    }
}
