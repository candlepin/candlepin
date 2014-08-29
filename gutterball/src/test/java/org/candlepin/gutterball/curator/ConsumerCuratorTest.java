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

import static org.candlepin.gutterball.TestUtils.*;
import static org.junit.Assert.*;

import org.candlepin.gutterball.EmbeddedMongoRule;
import org.candlepin.gutterball.MongoCollectionCleanupRule;
import org.candlepin.gutterball.model.Consumer;

import com.mongodb.DBObject;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@RunWith(JukitoRunner.class)
public class ConsumerCuratorTest {

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    public static EmbeddedMongoRule serverRule = new EmbeddedMongoRule();

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public MongoCollectionCleanupRule mongoTest = new MongoCollectionCleanupRule(serverRule,
            ConsumerCurator.COLLECTION);

    private ConsumerCurator curator;

    @Before
    public void setUpTest() {
        curator = new ConsumerCurator(serverRule.getMongoConnection());
    }

    @Test
    public void testInsertAndFindByUUID() {
        Consumer c = new Consumer("abc-123", new Date(), createOwner("test-owner", "Test Owner"));
        curator.insert(c);

        Consumer found = curator.findByUuid("abc-123");
        assertNotNull(found);
        assertEquals(c.getUUID(), found.getUUID());
    }

    @Test
    public void testSetConsumerDeleted() {
        Consumer c = new Consumer("abc-123", new Date(), createOwner("test-owner", "Test Owner"));
        curator.insert(c);

        Date deletedOn = new Date();
        curator.setConsumerDeleted(c.getUUID(), deletedOn);

        Consumer found = curator.findByUuid("abc-123");
        assertEquals(deletedOn, found.getDeleted());
    }

    @Test
    public void testUuidIncludedWhenNotDeletedAndWasCreatedOnTargetDate() {
        Calendar cal = getPrimedCalendar();
        Consumer consumer = new Consumer("consumer1", cal.getTime(), createOwner("TO1", "Test Owner 1"));
        curator.insert(consumer);
        List<String> uuids = curator.getUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(consumer.getUUID()));
    }

    @Test
    public void testUuidIncludedWhenNotDeletedAndWasCreatedBeforeTargetDate() {
        Calendar cal = getPrimedCalendar();
        Consumer consumer = new Consumer("consumer1", cal.getTime(), createOwner("TO1", "Test Owner 1"));
        curator.insert(consumer);

        cal.add(Calendar.DAY_OF_MONTH, 1);
        List<String> uuids = curator.getUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(consumer.getUUID()));
    }

    @Test
    public void testUuidNotIncludedWhenDeletedBeforeTargetDate() {
        Calendar cal = getPrimedCalendar();
        Consumer consumer = new Consumer("consumer1", cal.getTime(), createOwner("TO1", "Test Owner 1"));
        curator.insert(consumer);

        cal.add(Calendar.DAY_OF_MONTH, 1);
        curator.setConsumerDeleted(consumer.getUUID(), cal.getTime());
        assertTrue(curator.getUuidsOnDate(cal.getTime(), null, null).isEmpty());
    }

    @Test
    public void testUuidIncludedWhenTargetDateIsBetweenConsumerCreateAndDeleteDates() {
        Calendar cal = getPrimedCalendar();
        Consumer consumer = new Consumer("consumer1", cal.getTime(), createOwner("TO1", "Test Owner 1"));
        curator.insert(consumer);

        cal.add(Calendar.MONTH, 1);
        curator.setConsumerDeleted(consumer.getUUID(), cal.getTime());

        cal.add(Calendar.DAY_OF_MONTH, -2);
        List<String> uuids = curator.getUuidsOnDate(cal.getTime(), null, null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(consumer.getUUID()));
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
        String targetOwnerKey = "TO2";
        Consumer c1 = new Consumer("bbb-222", creationDate, createOwner("TO1", "Test Owner 1"));
        curator.insert(c1);

        DBObject owner2 = createOwner(targetOwnerKey, "Test Owner 2");
        Consumer c2 = new Consumer("ccc-333", creationDate, owner2);
        curator.insert(c2);

        // Should not be returned as it is deleted
        Consumer c3 = new Consumer("ddd-444", creationDate, owner2);
        curator.insert(c3);
        curator.setConsumerDeleted(c3.getUUID(), deletionDate);

        List<String> uuids = curator.getUuidsOnDate(targetDate, Arrays.asList(targetOwnerKey), null);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(c2.getUUID()));
    }

    @Test
    public void testGetConsumerUuidsFilteredByUuids() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);

        Date creationDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date deletionDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date targetDate = cal.getTime();

        Consumer c1 = new Consumer("bbb-222", creationDate, createOwner("TO1", "Test Owner 1"));
        curator.insert(c1);

        Consumer c2 = new Consumer("ccc-333", creationDate, createOwner("TO2", "Test Owner 2"));
        curator.insert(c2);

        List<String> uuids = curator.getUuidsOnDate(targetDate, null, Arrays.asList(c1.getUUID()));
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(c1.getUUID()));
    }

    private Calendar getPrimedCalendar() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);
        return cal;
    }
}
