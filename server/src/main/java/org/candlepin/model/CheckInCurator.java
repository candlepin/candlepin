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

import org.hibernate.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Curator for consumer check-ins.
 */
public class CheckInCurator extends AbstractHibernateCurator<CheckIn> {

    private static Logger log = LoggerFactory.getLogger(CheckInCurator.class);

    public static final int DELETE_BATCH_SIZE = 30000;

    public CheckInCurator() {
        super(CheckIn.class);
    }

    /**
     * Cleans up all but the most recent check-in times for every consumer.
     *
     * Very difficult to get a delete statement to do this that works across all databases.
     * Instead we use a select that will work, and batch the resulting IDs into chunks
     * that shouldn't exceed the maximum size for an IN subquery.
     *
     * @return Number of checkins deleted.
     */
    public int cleanupOldCheckIns() {

        String hql = "SELECT cc1.id FROM CheckIn cc1 WHERE cc1.checkInTime != (" +
                "SELECT MAX(cc2.checkInTime) " +
                "FROM CheckIn cc2 " +
                "WHERE cc2.consumer = cc1.consumer)";
        Query q = currentSession().createQuery(hql);

        // Due to size restrictions on an IN clause which we'll use in delete, and for
        // memory concerns, we're just going to look up one batch at a time and delete
        // them until we stop finding results.
        q.setMaxResults(DELETE_BATCH_SIZE);
        List<String> checkInIds = q.list();

        int totalDeleted = 0;
        int totalQueries = 1;
        String deleteHql = "DELETE from CheckIn where id in (:deleteIds)";
        Query deleteQuery = currentSession().createQuery(deleteHql);
        while (checkInIds.size() > 0) {
            log.debug("Found {} check-in IDs to be deleted.", checkInIds.size());
            deleteQuery.setParameterList("deleteIds", checkInIds);
            totalDeleted += deleteQuery.executeUpdate();

            checkInIds = q.list();
            totalQueries += 2; // one for the delete, one for the next query
        }
        log.info("Deleted a total of {} consumer check-in times with {} queries.",
                totalDeleted, totalQueries);
        return totalDeleted;
    }

}
