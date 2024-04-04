/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.inject.Singleton;


/**
 * GuestIdCurator
 */
@Singleton
public class GuestIdCurator extends AbstractHibernateCurator<GuestId> {

    private static Logger log = LoggerFactory.getLogger(GuestIdCurator.class);

    public GuestIdCurator() {
        super(GuestId.class);
    }

    public List<GuestId> listByConsumer(Consumer consumer) {
        String jpql = "SELECT g FROM GuestId g WHERE g.consumer = :consumer";

        return this.getEntityManager()
            .createQuery(jpql, GuestId.class)
            .setParameter("consumer", consumer)
            .getResultList();
    }

    public GuestId findByConsumerAndId(Consumer consumer, String guestId) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("guestIdLower", guestId.toLowerCase()))
            .setMaxResults(1)
            .uniqueResult();
    }

    public GuestId findByGuestIdAndOrg(String guestUuid, String ownerId) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("guestIdLower", guestUuid.toLowerCase()))
            .createAlias("consumer", "gconsumer")
            .add(Restrictions.eq("gconsumer.ownerId", ownerId))
            .setMaxResults(1)
            .uniqueResult();
    }
}
