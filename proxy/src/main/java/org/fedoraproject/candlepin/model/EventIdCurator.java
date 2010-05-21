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

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

/**
 * EventIdCurator - Interface to request a unique event ID.
 */
public class EventIdCurator extends AbstractHibernateCurator<EventId> {

    private static Logger log = Logger.getLogger(EventIdCurator.class);

    protected EventIdCurator() {
        super(EventId.class);
    }

    /**
     * Get the next available serial number, delete the old entries from the db.
     * (we're really only interested in the most recent)
     *
     * @return next available serial number.
     */
    public Long getNextEventId() {
        EventId eventId = new EventId();
        create(eventId);

        Criteria crit = currentSession().createCriteria(EventId.class);
        crit.add(Restrictions.lt("id", eventId.getId()));
        List<EventId> toDelete = crit.list();
        for (EventId deleteThis : toDelete) {
            log.debug("Deleting old event ID: " + deleteThis);
            delete(deleteThis);
        }

        return eventId.getId();
    }

}
