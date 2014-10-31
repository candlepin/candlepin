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

package org.candlepin.gutterball.curator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.model.ConsumerState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ConsumerStateCuratorTest extends DatabaseTestFixture {

    @Test
    public void testInsertAndFindByUUID() {
        ConsumerState state = new ConsumerState("abc-123", "test-owner", new Date());
        consumerStateCurator.create(state);

        ConsumerState found = consumerStateCurator.findByUuid("abc-123");
        assertNotNull(found);
        assertEquals(state.getUuid(), found.getUuid());
    }

    @Test
    public void testSetConsumerDeleted() {
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", new Date());
        consumerStateCurator.create(state);

        Date deletedOn = new Date();
        consumerStateCurator.setConsumerDeleted(state.getUuid(), deletedOn);

        ConsumerState found = consumerStateCurator.findByUuid(uuid);
        assertEquals(deletedOn, found.getDeleted());
    }

    @Test
    public void testUuidIncludedWhenNotDeletedAndWasCreatedOnTargetDate() {
        Calendar cal = getPrimedCalendar();
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", cal.getTime());
        consumerStateCurator.create(state);

        List<String> uuids = consumerStateCurator.getConsumerUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(state.getUuid()));
    }

    @Test
    public void testUuidIncludedWhenNotDeletedAndWasCreatedBeforeTargetDate() {
        Calendar cal = getPrimedCalendar();
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", cal.getTime());
        consumerStateCurator.create(state);

        cal.add(Calendar.DAY_OF_MONTH, 1);
        List<String> uuids = consumerStateCurator.getConsumerUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(state.getUuid()));
    }

    @Test
    public void testUuidNotIncludedWhenDeletedBeforeTargetDate() {
        Calendar cal = getPrimedCalendar();
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", cal.getTime());
        consumerStateCurator.create(state);

        cal.add(Calendar.DAY_OF_MONTH, 1);
        consumerStateCurator.setConsumerDeleted(uuid, cal.getTime());
        assertTrue(consumerStateCurator.getConsumerUuidsOnDate(cal.getTime(), null, null).isEmpty());
    }

    @Test
    public void testUuidIncludedWhenTargetDateIsBetweenConsumerCreateAndDeleteDates() {
        Calendar cal = getPrimedCalendar();
        String uuid = TestUtils.randomString("test-consumer-uuid");
        ConsumerState state = new ConsumerState(uuid, "test-owner", cal.getTime());
        consumerStateCurator.create(state);

        cal.add(Calendar.MONTH, 1);
        consumerStateCurator.setConsumerDeleted(uuid, cal.getTime());

        cal.add(Calendar.DAY_OF_MONTH, -2);
        List<String> uuids = consumerStateCurator.getConsumerUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(uuid));
    }

    @Test
    public void testConsumerUuidsFilteredByOwner() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);

        Date creationDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date deletionDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date targetDate = cal.getTime();

        // Should be filtered out due to different owner.
        String filteredUuid = TestUtils.randomString("filtered-consumer-uuid");
        consumerStateCurator.create(new ConsumerState(filteredUuid, "TO1", creationDate));

        String targetOwnerKey = "TO2";
        String includedUuid = TestUtils.randomString("included-consumer-uuid");
        consumerStateCurator.create(new ConsumerState(includedUuid, targetOwnerKey, creationDate));

        // Should not be returned as it is deleted
        String deletedUuid = TestUtils.randomString("deleted-consumer-uuid");
        consumerStateCurator.create(new ConsumerState(deletedUuid, targetOwnerKey, creationDate));
        consumerStateCurator.setConsumerDeleted(deletedUuid, deletionDate);

        List<String> uuids = consumerStateCurator.getConsumerUuidsOnDate(targetDate,
                Arrays.asList(targetOwnerKey), null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(includedUuid));
    }

    @Test
    public void testGetConsumerUuidsFilteredByUuids() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);

        Date creationDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date targetDate = cal.getTime();

        String ownerKey = TestUtils.randomString("owner-key");
        String expectedUuid = TestUtils.randomString("expected-consumer-uuid");

        consumerStateCurator.create(new ConsumerState(expectedUuid, ownerKey, creationDate));
        consumerStateCurator.create(new ConsumerState(TestUtils.randomString("not-inculded"), ownerKey,
                creationDate));
        List<String> uuids = consumerStateCurator.getConsumerUuidsOnDate(targetDate, null,
                Arrays.asList(expectedUuid));
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(expectedUuid));
    }

    private Calendar getPrimedCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);
        return cal;
    }

}
