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

import java.util.List;

import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GuestIdCurator
 */
public class GuestIdCurator extends AbstractHibernateCurator<GuestId> {

    private static Logger log = LoggerFactory.getLogger(GuestIdCurator.class);

    protected GuestIdCurator() {
        super(GuestId.class);
    }

    public Page<List<GuestId>> listByConsumer(Consumer consumer,
            PageRequest pageRequest) {
        Criteria criteria = this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("consumer", consumer));
        return listByCriteria(criteria, pageRequest);
    }

    public List<GuestId> listByConsumer(Consumer consumer) {
        return listByConsumer(consumer, null).getPageData();
    }

    public GuestId findByConsumerAndId(Consumer consumer, String guestId) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("consumer", consumer))
            .add(Restrictions.eq("guestId", guestId).ignoreCase())
            .setMaxResults(1)
            .uniqueResult();
    }

    public GuestId findByGuestIdAndOrg(String guestUuid, Owner owner) {
        return (GuestId) this.currentSession().createCriteria(GuestId.class)
            .add(Restrictions.eq("guestId", guestUuid).ignoreCase())
            .createAlias("consumer", "gconsumer")
            .add(Restrictions.eq("gconsumer.owner", owner))
            .setMaxResults(1)
            .uniqueResult();
    }
}
