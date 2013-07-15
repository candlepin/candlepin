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

import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeletedConsumerCurator
 */
public class DeletedConsumerCurator extends
    AbstractHibernateCurator<DeletedConsumer> {

    public DeletedConsumerCurator() {
        super(DeletedConsumer.class);
    }

    public DeletedConsumer findByConsumer(Consumer c) {
        return findByConsumerUuid(c.getUuid());
    }

    public DeletedConsumer findByConsumerUuid(String uuid) {
        return (DeletedConsumer) currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.eq("consumerUuid", uuid))
            .uniqueResult();
    }

    public List<DeletedConsumer> findByOwner(Owner o) {
        return findByOwnerId(o.getId());
    }

    @SuppressWarnings("unchecked")
    public List<DeletedConsumer> findByOwnerId(String oid) {
        return currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.eq("ownerId", oid))
            .addOrder(Order.desc("created"))
            .list();
    }

    public int countByConsumer(Consumer c) {
        return countByConsumerUuid(c.getUuid());
    }

    public int countByConsumerUuid(String uuid) {
        return (Integer) currentSession().createCriteria(DeletedConsumer.class)
                        .add(Restrictions.eq("consumerUuid", uuid))
                        .setProjection(Projections.rowCount()).uniqueResult();

    }

    public Map<String, Integer> countByConsumerUuids(Collection<String> uuids) {
        Criteria c = currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.in("consumerUuid", uuids));
        ProjectionList projectionList = Projections.projectionList();
        projectionList.add(Projections.groupProperty("consumerUuid"));
        projectionList.add(Projections.rowCount());
        c.setProjection(projectionList);

        List results = c.list();
        Map<String, Integer> uuidMap = new HashMap<String, Integer>();
        for (int i = 0; i < results.size(); i++) {
            Object[] pair = (Object[]) results.get(i);
            uuidMap.put((String) pair[0], (Integer) pair[1]);
        }

        // If one of the uuids has a count of zero, the query will simply leave
        // that value out of the results.  So we put in a zero so people won't
        // be surprised by nulls.
        for (String uuid : uuids) {
            if (!uuidMap.containsKey(uuid)) {
                uuidMap.put(uuid, Integer.valueOf(0));
            }
        }

        return uuidMap;
    }

    @SuppressWarnings("unchecked")
    public List<DeletedConsumer> findByDate(Date date) {
        return currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.ge("created", date))
            .addOrder(Order.desc("created"))
            .list();
    }
}
