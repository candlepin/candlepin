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

import static org.junit.Assert.assertEquals;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;

public class CheckInCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer c1;
    private Consumer c2;
    private Consumer c3;

    @Inject private CheckInCurator checkInCurator;
    @Inject private ConsumerCurator consumerCurator;

    @Before
    public void setUp() {
        owner = createOwner();
        c1 = createConsumer(owner);
        c2 = createConsumer(owner);
        c3 = createConsumer(owner);
    }

    @Test
    public void cleanupOldCheckins() {
        Date mostRecent = new Date();
        Date fourHoursAgo = getDateHoursBefore(4);
        c1.addCheckIn(mostRecent);
        c1.addCheckIn(fourHoursAgo);
        c1.addCheckIn(getDateHoursBefore(8));
        consumerCurator.merge(c1);

        c2.addCheckIn(fourHoursAgo);
        c1.addCheckIn(getDateHoursBefore(12));
        c1.addCheckIn(getDateHoursBefore(16));
        consumerCurator.merge(c1);

        int deleted = checkInCurator.cleanupOldCheckIns();
        assertEquals(4, deleted);

        consumerCurator.evict(c1);
        c1 = consumerCurator.find(c1.getId());
        assertEquals(1, c1.getCheckIns().size());
        assertEquals(mostRecent, c1.getLastCheckin());

        consumerCurator.evict(c2);
        c2 = consumerCurator.find(c2.getId());
        assertEquals(1, c2.getCheckIns().size());
        assertEquals(fourHoursAgo, c2.getLastCheckin());

        // This consumer had no check-in times to begin with:
        consumerCurator.evict(c3);
        c3 = consumerCurator.find(c3.getId());
        assertEquals(0, c3.getCheckIns().size());
    }

    private Date getDateHoursBefore(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 0 - hours);
        return cal.getTime();

    }
}
