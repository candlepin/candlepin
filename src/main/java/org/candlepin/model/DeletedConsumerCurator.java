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

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

import java.util.Date;
import java.util.List;

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
        return ((Long) currentSession().createCriteria(DeletedConsumer.class)
                       .add(Restrictions.eq("consumerUuid", uuid))
                       .setProjection(Projections.rowCount()).uniqueResult()).intValue();

    }

    @SuppressWarnings("unchecked")
    public List<DeletedConsumer> findByDate(Date date) {
        return currentSession().createCriteria(DeletedConsumer.class)
            .add(Restrictions.ge("created", date))
            .addOrder(Order.desc("created"))
            .list();
    }
}
