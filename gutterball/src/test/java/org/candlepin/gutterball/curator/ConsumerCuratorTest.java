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
    public void testGetDeletedConsumerUuids() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);

        Date creationDate = cal.getTime();
        Consumer toInsert = new Consumer("bbb-222", creationDate, createOwner("TO1", "Test Owner 1"));
        curator.insert(toInsert);

        cal.add(Calendar.MONTH, 2);
        Date targetDate = cal.getTime();
        assertTrue(curator.getDeletedUuids(targetDate, null, null).isEmpty());

        cal.add(Calendar.MONTH, -1);
        curator.setConsumerDeleted(toInsert.getUUID(), cal.getTime());

        List<String> deletedUuids = curator.getDeletedUuids(targetDate, null, null);
        assertEquals(deletedUuids.size(), 1);
        assertTrue(deletedUuids.contains(toInsert.getUUID()));
    }

    @Test
    public void testGetDeletedConsumerUuidsFilteredByOwner() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 15);

        Date creationDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date deletionDate = cal.getTime();

        cal.add(Calendar.MONTH, 1);
        Date targetDate = cal.getTime();

        String targetOwnerKey = "TO2";
        Consumer c1 = new Consumer("bbb-222", creationDate, createOwner("TO1", "Test Owner 1"));
        curator.insert(c1);
        curator.setConsumerDeleted(c1.getUUID(), deletionDate);

        Consumer c2 = new Consumer("ccc-333", creationDate, createOwner(targetOwnerKey, "Test Owner 2"));
        curator.insert(c2);
        curator.setConsumerDeleted(c2.getUUID(), deletionDate);

        List<String> deletedUuids = curator.getDeletedUuids(targetDate, Arrays.asList(targetOwnerKey), null);
        assertEquals(1, deletedUuids.size());
        assertTrue(deletedUuids.contains(c2.getUUID()));
    }

    @Test
    public void testGetDeletedConsumerUuidsFilteredByUuids() {
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
        curator.setConsumerDeleted(c1.getUUID(), deletionDate);

        Consumer c2 = new Consumer("ccc-333", creationDate, createOwner("TO2", "Test Owner 2"));
        curator.insert(c2);
        curator.setConsumerDeleted(c2.getUUID(), deletionDate);

        List<String> deletedUuids = curator.getDeletedUuids(targetDate, null, Arrays.asList(c1.getUUID()));
        assertEquals(1, deletedUuids.size());
        assertTrue(deletedUuids.contains(c1.getUUID()));
    }
}
