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

public class GuestIdsCheckInCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer c1;
    private Consumer c2;
    private Consumer c3;

    @Inject private GuestIdsCheckInCurator guestIdsCheckInCurator;
    @Inject private ConsumerCurator consumerCurator;

    @Before
    public void setUp() {
        owner = createOwner();
        c1 = createConsumer(owner);
        c2 = createConsumer(owner);
        c3 = createConsumer(owner);
    }

    @Test
    public void cleanupOldCheckins() throws Exception {
        c1.addGuestIdCheckIn();
        consumerCurator.merge(c1);
        c1.addGuestIdCheckIn();
        consumerCurator.merge(c1);
        c1.addGuestIdCheckIn();
        consumerCurator.merge(c1);
        // find the keeper
        Date latest1 = new Date(0L);
        for (GuestIdsCheckIn checkIn : c1.getGuestIdCheckIns()) {
            if (checkIn.getUpdated().getTime() > latest1.getTime()) {
                latest1 = checkIn.getUpdated();
            }
        }

        c2.addGuestIdCheckIn();
        consumerCurator.merge(c2);
        c2.addGuestIdCheckIn();
        consumerCurator.merge(c2);
        c2.addGuestIdCheckIn();
        consumerCurator.merge(c2);
        c2.addGuestIdCheckIn();
        consumerCurator.merge(c2);
        c2.addGuestIdCheckIn();
        consumerCurator.merge(c2);
        // find the keeper
        Date latest2 = new Date(0L);
        for (GuestIdsCheckIn checkIn : c2.getGuestIdCheckIns()) {
            if (checkIn.getUpdated().getTime() > latest2.getTime()) {
                latest2 = checkIn.getUpdated();
            }
        }
        int deleted = guestIdsCheckInCurator.cleanupOldCheckIns();
        assertEquals(6, deleted);

        consumerCurator.evict(c1);
        c1 = consumerCurator.find(c1.getId());
        assertEquals(1, c1.getGuestIdCheckIns().size());
        assertEquals(latest1.getTime(), c1.getGuestIdCheckIns().iterator().next().getUpdated().getTime());

        consumerCurator.evict(c2);
        c2 = consumerCurator.find(c2.getId());
        assertEquals(1, c2.getGuestIdCheckIns().size());
        assertEquals(latest2.getTime(), c2.getGuestIdCheckIns().iterator().next().getUpdated().getTime());

        // This consumer had no check-in times to begin with:
        consumerCurator.evict(c3);
        c3 = consumerCurator.find(c3.getId());
        assertEquals(0, c3.getGuestIdCheckIns().size());
    }

    private Date getDateHoursBefore(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 0 - hours);
        return cal.getTime();

    }
}
