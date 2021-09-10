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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;



public class DeletedConsumerCuratorTest extends DatabaseTestFixture {

    public static final String OWNER_ID_1 = "10";
    private static final String CONSUMER_UUID_1 = "abcde";
    private static final String CONSUMER_UUID_2 = "fghij";
    private static final String CONSUMER_UUID_3 = "klmno";
    private static final String UNKNOWN_UUID = "dontfind";
    private static final Date OLDEST_DATE = TestUtil.createDate(2019, 5, 20);
    private static final Date OLD_DATE = TestUtil.createDate(2020, 5, 20);

    @Inject
    private DeletedConsumerCurator dcc;

    @Test
    public void byConsumerId() {
        createDeletedConsumer(CONSUMER_UUID_1);

        DeletedConsumer found = dcc.findByConsumerUuid(CONSUMER_UUID_1);
        assertEquals(CONSUMER_UUID_1, found.getConsumerUuid());
    }

    @Test
    public void byConsumer() {
        createDeletedConsumer(CONSUMER_UUID_1);
        Consumer c = createConsumer(CONSUMER_UUID_1);

        DeletedConsumer found = dcc.findByConsumer(c);
        assertEquals(CONSUMER_UUID_1, found.getConsumerUuid());
    }

    @Test
    public void byOwnerId() {
        createDeletedConsumer(CONSUMER_UUID_1);
        createDeletedConsumer(CONSUMER_UUID_2);

        List<DeletedConsumer> found = dcc.findByOwnerId(OWNER_ID_1).list();
        assertEquals(2, found.size());
    }

    @Test
    public void byOwner() {
        createDeletedConsumer(CONSUMER_UUID_1);
        Owner o = createMockOwner(OWNER_ID_1);

        List<DeletedConsumer> found = dcc.findByOwner(o).list();
        assertEquals(1, found.size());
    }

    @Test
    public void countByConsumerId() {
        createDeletedConsumer(CONSUMER_UUID_1);
        createDeletedConsumer(CONSUMER_UUID_2);

        assertEquals(1, dcc.countByConsumerUuid(CONSUMER_UUID_1));
        assertEquals(1, dcc.countByConsumerUuid(CONSUMER_UUID_2));
        assertEquals(0, dcc.countByConsumerUuid(UNKNOWN_UUID));
    }

    @Test
    public void countByConsumer() {
        createDeletedConsumer(CONSUMER_UUID_1);
        Consumer consumer = createConsumer(CONSUMER_UUID_1);
        Consumer unknownConsumer = createConsumer(UNKNOWN_UUID);

        assertEquals(1, dcc.countByConsumer(consumer));
        assertEquals(0, dcc.countByConsumer(unknownConsumer));
    }

    @Test
    public void findByDate() {
        createDeletedConsumer(CONSUMER_UUID_1, OLDEST_DATE);
        createDeletedConsumer(CONSUMER_UUID_2, OLD_DATE);
        createDeletedConsumer(CONSUMER_UUID_3);

        assertEquals(3, dcc.findByDate(OLDEST_DATE).list().size());
        assertEquals(2, dcc.findByDate(OLD_DATE).list().size());
        assertEquals(0, dcc.findByDate(new Date()).list().size());
    }

    @Test
    public void descOrderByDate() {
        createDeletedConsumer(CONSUMER_UUID_1, OLDEST_DATE);
        createDeletedConsumer(CONSUMER_UUID_2, OLD_DATE);
        createDeletedConsumer(CONSUMER_UUID_3);

        DeletedConsumer newest = dcc.findByDate(OLDEST_DATE).list().get(0);
        assertEquals(CONSUMER_UUID_3, newest.getConsumerUuid());
    }

    @Test
    public void descOrderByOwnerId() {
        createDeletedConsumer(CONSUMER_UUID_1, OLD_DATE);
        createDeletedConsumer(CONSUMER_UUID_2);

        DeletedConsumer newest = dcc.findByOwnerId(OWNER_ID_1).list().get(0);
        assertEquals(CONSUMER_UUID_2, newest.getConsumerUuid());
    }

    @Test
    public void shouldFindExistingDeletedConsumer() {
        createDeletedConsumer(CONSUMER_UUID_1);
        createDeletedConsumer(CONSUMER_UUID_2);
        createDeletedConsumer(CONSUMER_UUID_3);

        List<DeletedConsumer> deletedConsumers = dcc.findByConsumerUuids(List.of(
            CONSUMER_UUID_1,
            CONSUMER_UUID_2,
            CONSUMER_UUID_3
        ));

        assertEquals(3, deletedConsumers.size());
    }

    @Test
    public void nothingToFind() {
        assertEquals(0, dcc.findByConsumerUuids(null).size());
        assertEquals(0, dcc.findByConsumerUuids(List.of()).size());
        assertEquals(0, dcc.findByConsumerUuids(List.of(UNKNOWN_UUID)).size());
    }

    private void createDeletedConsumer(String uuid, String ownerId, Date created) {
        DeletedConsumer dc = new DeletedConsumer(uuid, ownerId, "key", "name");
        dc.setCreated(created);
        dcc.create(dc);
    }

    private void createDeletedConsumer(String uuid) {
        createDeletedConsumer(uuid, OWNER_ID_1, new Date());
    }

    private void createDeletedConsumer(String uuid, Date created) {
        createDeletedConsumer(uuid, OWNER_ID_1, created);
    }

    private Consumer createConsumer(String uuid) {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn(uuid);
        return c;
    }

    private Owner createMockOwner(String ownerId) {
        Owner o = mock(Owner.class);
        when(o.getId()).thenReturn(ownerId);
        return o;
    }

}
