/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;



/**
 * DeletedConsumerCuratorTest
 */
public class DeletedConsumerCuratorTest extends DatabaseTestFixture {

    private Date twoResultsDate;
    private Date oneResultDate;
    private Owner owner;
    private ConsumerType ct;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        DeletedConsumer dc = new DeletedConsumer("abcde", "10", "key", "name");
        dc.setConsumerName("consumerName");
        deletedConsumerCurator.create(dc);
        try {
            Thread.sleep(5);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // save the current time, DCs created after this will have
        // a created timestamp after this time
        twoResultsDate = new Date();
        dc = new DeletedConsumer("fghij", "10", "key", "name");
        dc.setConsumerName("consumerName");
        deletedConsumerCurator.create(dc);
        try {
            Thread.sleep(5);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        oneResultDate = new Date();
        dc = new DeletedConsumer("klmno", "20", "key", "name");
        dc.setConsumerName("consumerName");
        deletedConsumerCurator.create(dc);

        this.owner = this.createOwner("test-owner", "Test Owner");
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
    }

    @Test
    public void byConsumerId() {
        DeletedConsumer found = deletedConsumerCurator.findByConsumerUuid("abcde");
        assertEquals("abcde", found.getConsumerUuid());
    }

    @Test
    public void byConsumer() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("abcde");
        DeletedConsumer found = deletedConsumerCurator.findByConsumer(c);
        assertEquals("abcde", found.getConsumerUuid());
    }

    @Test
    public void byOwnerId() {
        List<DeletedConsumer> found = deletedConsumerCurator.findByOwnerId("10").list();
        assertEquals(2, found.size());
    }

    @Test
    public void byOwner() {
        Owner o = mock(Owner.class);
        when(o.getId()).thenReturn("20");
        List<DeletedConsumer> found = deletedConsumerCurator.findByOwner(o).list();
        assertEquals(1, found.size());
    }

    @Test
    public void countByConsumerId() {
        assertEquals(1, deletedConsumerCurator.countByConsumerUuid("abcde"));
        assertEquals(0, deletedConsumerCurator.countByConsumerUuid("dontfind"));
        assertEquals(1, deletedConsumerCurator.countByConsumerUuid("fghij"));
    }

    @Test
    public void countByConsumer() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("abcde");
        assertEquals(1, deletedConsumerCurator.countByConsumer(c));

        c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("dontfind");
        assertEquals(0, deletedConsumerCurator.countByConsumer(c));
    }

    @Test
    public void findByDate() throws InterruptedException {
        assertEquals(2, deletedConsumerCurator.findByDate(twoResultsDate).list().size());
        assertEquals(1, deletedConsumerCurator.findByDate(oneResultDate).list().size());
        Thread.sleep(2000);
        assertEquals(0, deletedConsumerCurator.findByDate(new Date()).list().size());
    }

    @Test
    public void descOrderByDate() {
        DeletedConsumer newest = deletedConsumerCurator.findByDate(twoResultsDate).list().get(0);
        assertEquals("klmno", newest.getConsumerUuid());
    }

    @Test
    public void descOrderByOwnerId() {
        DeletedConsumer newest = deletedConsumerCurator.findByOwnerId("10").list().get(0);
        assertEquals("fghij", newest.getConsumerUuid());
    }

    @Test
    public void testCreateDeletedConsumersWithExistingConsumers() {
        Consumer consumer1 = new Consumer()
            .setName("consumer-1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer1 = consumerCurator.create(consumer1);
        Consumer consumer2 = new Consumer()
            .setName("consumer-2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer2 = consumerCurator.create(consumer2);

        int actual = deletedConsumerCurator.createDeletedConsumers(Arrays
            .asList(consumer1.getId(), consumer2.getId(), "unknown-id"));

        assertEquals(2, actual);
        DeletedConsumer deleteConsumer1 = deletedConsumerCurator.findByConsumerUuid(consumer1.getUuid());
        assertEquals(consumer1.getConsumer().getName(), deleteConsumer1.getConsumerName());
        assertEquals(consumer1.getConsumer().getUuid(), deleteConsumer1.getConsumerUuid());
        assertEquals(consumer1.getOwner().getId(), deleteConsumer1.getOwnerId());
        assertEquals(consumer1.getOwner().getDisplayName(), deleteConsumer1.getOwnerDisplayName());
        assertEquals(consumer1.getOwner().getKey(), deleteConsumer1.getOwnerKey());

        DeletedConsumer deleteConsumer2 = deletedConsumerCurator.findByConsumerUuid(consumer2.getUuid());
        assertEquals(consumer2.getConsumer().getName(), deleteConsumer2.getConsumerName());
        assertEquals(consumer2.getConsumer().getUuid(), deleteConsumer2.getConsumerUuid());
        assertEquals(consumer2.getOwner().getId(), deleteConsumer2.getOwnerId());
        assertEquals(consumer2.getOwner().getDisplayName(), deleteConsumer2.getOwnerDisplayName());
        assertEquals(consumer2.getOwner().getKey(), deleteConsumer2.getOwnerKey());
    }
}
