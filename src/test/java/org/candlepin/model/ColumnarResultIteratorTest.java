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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;

import org.hibernate.Query;
import org.hibernate.ScrollMode;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;



public class ColumnarResultIteratorTest extends DatabaseTestFixture {

    private Session session;

    @BeforeEach
    public void setup() {
        this.session = (Session) this.getEntityManager().getDelegate();
    }

    @Test
    public void testHasNextWithElements() {
        this.createOwner();
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ColumnarResultIterator<Owner> iterator = new ColumnarResultIterator<>(
            this.session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0,
            false);

        try {
            assertTrue(iterator.hasNext());
        }
        finally {
            iterator.close();
        }
    }

    @Test
    public void testHasNextWithoutElements() {
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ColumnarResultIterator<Owner> iterator = new ColumnarResultIterator<>(
            this.session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0,
            false);

        try {
            assertFalse(iterator.hasNext());
        }
        finally {
            iterator.close();
        }
    }

    @Test
    public void testNextWithElements() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ColumnarResultIterator<Owner> iterator = new ColumnarResultIterator<>(
            this.session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0,
            false);

        try {
            List<Owner> owners = new LinkedList<>();

            // Note: Since we're testing everything in isolation here, we can't
            // be expecting .hasNext to be functional here. :)
            owners.add(iterator.next());
            owners.add(iterator.next());
            owners.add(iterator.next());

            assertTrue(owners.contains(owner1));
            assertTrue(owners.contains(owner2));
            assertTrue(owners.contains(owner3));
        }
        finally {
            iterator.close();
        }
    }

    @Test
    public void testNextWithoutElements() {
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ColumnarResultIterator<Owner> iterator = new ColumnarResultIterator<>(
            this.session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0,
            false);

        try {
            assertThrows(NoSuchElementException.class, () -> iterator.next());
        }
        finally {
            iterator.close();
        }
    }

    @Test
    public void testRemoveAlwaysFails() {
        this.createOwner();
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ColumnarResultIterator<Owner> iterator = new ColumnarResultIterator<>(
            this.session,
            query.scroll(ScrollMode.FORWARD_ONLY),
            0,
            false);

        try {
            iterator.next();
            assertThrows(UnsupportedOperationException.class, () -> iterator.remove());
        }
        finally {
            iterator.close();
        }
    }

}
