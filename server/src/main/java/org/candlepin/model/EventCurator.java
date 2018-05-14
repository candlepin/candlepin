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

import org.candlepin.audit.Event;

import com.google.inject.Inject;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import javax.inject.Singleton;



/**
 * AttributeCurator
 */
@Singleton
public class EventCurator extends AbstractHibernateCurator<Event> {

    @Inject private CandlepinQueryFactory cpQueryFactory;

    public EventCurator() {
        super(Event.class);
    }

    /**
     * @param limit
     * @return
     */
    private DetachedCriteria createEventCriteria() {
        return DetachedCriteria.forClass(Event.class)
            .addOrder(Order.desc("timestamp"))
            .addOrder(Order.asc("target"))
            .addOrder(Order.asc("type"))
            .addOrder(Order.asc("entityId"));
    }

    /**
     * Query events, most recent first.
     *
     * @return List of events.
     */
    @SuppressWarnings("unchecked")
    public CandlepinQuery<Event> listMostRecent(int limit) {
        DetachedCriteria criteria = this.createEventCriteria();

        return this.cpQueryFactory.<Event>buildQuery(this.currentSession(), criteria)
            .setMaxResults(limit);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Event> listMostRecent(int limit, Owner owner) {
        DetachedCriteria criteria = this.createEventCriteria()
            .add(Restrictions.eq("ownerId", owner.getId()));

        return this.cpQueryFactory.<Event>buildQuery(this.currentSession(), criteria)
            .setMaxResults(limit);
    }

    @SuppressWarnings("unchecked")
    public CandlepinQuery<Event> listMostRecent(int limit, Consumer consumer) {
        DetachedCriteria criteria = this.createEventCriteria()
            .add(Restrictions.eq("consumerUuid", consumer.getUuid()));

        return this.cpQueryFactory.<Event>buildQuery(this.currentSession(), criteria)
            .setMaxResults(limit);
    }

}
