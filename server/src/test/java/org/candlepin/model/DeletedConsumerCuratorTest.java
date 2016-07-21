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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

/**
 * DeletedConsumerCuratorTest
 */
public class DeletedConsumerCuratorTest extends DatabaseTestFixture {
    @Inject private DeletedConsumerCurator dcc;

    private Date twoResultsDate;
    private Date oneResultDate;


    @Before
    public void init() {
        super.init();
        DeletedConsumer dc = new DeletedConsumer("abcde", "10", "key", "name");
        dcc.create(dc);
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
        dcc.create(dc);
        try {
            Thread.sleep(5);
        }
        catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        oneResultDate = new Date();
        dc = new DeletedConsumer("klmno", "20", "key", "name");
        dcc.create(dc);
    }

    @Test
    public void byConsumerId() {
        DeletedConsumer found = dcc.findByConsumerUuid("abcde");
        assertEquals("abcde", found.getConsumerUuid());
    }

    @Test
    public void byConsumer() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("abcde");
        DeletedConsumer found = dcc.findByConsumer(c);
        assertEquals("abcde", found.getConsumerUuid());
    }

    @Test
    public void byOwnerId() {
        List<DeletedConsumer> found = dcc.findByOwnerId("10").list();
        assertEquals(2, found.size());
    }

    @Test
    public void byOwner() {
        Owner o = mock(Owner.class);
        when(o.getId()).thenReturn("20");
        List<DeletedConsumer> found = dcc.findByOwner(o).list();
        assertEquals(1, found.size());
    }

    @Test
    public void countByConsumerId() {
        assertEquals(1, dcc.countByConsumerUuid("abcde"));
        assertEquals(0, dcc.countByConsumerUuid("dontfind"));
        assertEquals(1, dcc.countByConsumerUuid("fghij"));
    }

    @Test
    public void countByConsumer() {
        Consumer c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("abcde");
        assertEquals(1, dcc.countByConsumer(c));

        c = mock(Consumer.class);
        when(c.getUuid()).thenReturn("dontfind");
        assertEquals(0, dcc.countByConsumer(c));
    }

    @Test
    public void findByDate() throws InterruptedException {
        assertEquals(2, dcc.findByDate(twoResultsDate).list().size());
        assertEquals(1, dcc.findByDate(oneResultDate).list().size());
        Thread.sleep(2000);
        assertEquals(0, dcc.findByDate(new Date()).list().size());
    }

    @Test
    public void descOrderByDate() {
        DeletedConsumer newest = dcc.findByDate(twoResultsDate).list().get(0);
        assertEquals("klmno", newest.getConsumerUuid());
    }

    @Test
    public void descOrderByOwnerId() {
        DeletedConsumer newest = dcc.findByOwnerId("10").list().get(0);
        assertEquals("fghij", newest.getConsumerUuid());
    }
}
