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
package org.candlepin.model.curator;


import java.util.List;

import org.candlepin.audit.Event;
import org.candlepin.auth.interceptor.EnforceAccessControl;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

/**
 * AttributeCurator
 */
public class EventCurator extends AbstractHibernateCurator<Event> {

    protected EventCurator() {
        super(Event.class);
    }

    /**
     * Query events, most recent first.
     *
     * @return List of events.
     */
    @SuppressWarnings("unchecked")
    public List<Event> listMostRecent(int limit) {
        Criteria crit = createEventCriteria(limit);
        return crit.list();
    }

    /**
     * @param limit
     * @return
     */
    private Criteria createEventCriteria(int limit) {
        return currentSession().createCriteria(Event.class)
            .setMaxResults(limit).addOrder(Order.desc("timestamp"));
    }

    @SuppressWarnings("unchecked")
    @EnforceAccessControl
    public List<Event> listMostRecent(int limit, Owner owner) {
        return createEventCriteria(limit).add(
            Restrictions.eq("ownerId", owner.getId())).list();
    }

    @SuppressWarnings("unchecked")
    @EnforceAccessControl
    public List<Event> listMostRecent(int limit, Consumer consumer) {
        return createEventCriteria(limit).add(
            Restrictions.eq("consumerId", consumer.getId())).list();
    }

}
