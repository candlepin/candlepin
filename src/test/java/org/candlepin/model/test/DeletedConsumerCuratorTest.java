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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * DeletedConsumerCuratorTest
 */
public class DeletedConsumerCuratorTest extends DatabaseTestFixture {

    private DeletedConsumerCurator dcc;

    @Before
    public void init() {
        super.init();
        dcc = injector.getInstance(DeletedConsumerCurator.class);

        DeletedConsumer dc = new DeletedConsumer("abcde", "10");
        dcc.create(dc);

        dc = new DeletedConsumer("fghij", "10");
        dcc.create(dc);

        dc = new DeletedConsumer("klmno", "20");
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
        List<DeletedConsumer> found = dcc.findByOwnerId("10");
        assertEquals(2, found.size());
    }

    @Test
    public void byOwner() {
        Owner o = mock(Owner.class);
        when(o.getId()).thenReturn("20");
        List<DeletedConsumer> found = dcc.findByOwner(o);
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
}
