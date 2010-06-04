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
package org.fedoraproject.candlepin.model;


import java.util.List;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.auth.interceptor.EnforceAccessControl;
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

    
    // TODO: Pass in actual Owner object for better overloading:
    @SuppressWarnings("unchecked")
    @EnforceAccessControl
    public List<Event> listMostRecent(int limit, long ownerId) {
        return createEventCriteria(limit).add(
            Restrictions.eq("ownerId", ownerId)).list();
    }

    @SuppressWarnings("unchecked")
    public List<Event> listMostRecent(int limit, Consumer consumer) {
        return createEventCriteria(limit).add(
            Restrictions.eq("entityId", consumer.getId())).list();
    }

}
