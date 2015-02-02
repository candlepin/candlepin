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


/**
 * Curator for consumer check-ins.
 */
public class CheckInCurator extends AbstractHibernateCurator<CheckIn> {

    public CheckInCurator() {
        super(CheckIn.class);
    }

    /**
     * Cleans up all but the most recent check-in times for every consumer.
     *
     * @return Number of checkins deleted.
     */
    public int cleanupOldCheckIns() {
        String hql = "DELETE FROM CheckIn cc1 WHERE cc1.checkInTime != (" +
                "SELECT MAX(cc2.checkInTime) " +
                "FROM CheckIn cc2 " +
                "WHERE cc2.consumer = cc1.consumer)";
        Query q = currentSession().createQuery(hql);
        int count = q.executeUpdate();
        return count;
    }

}
