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

import static org.junit.Assert.*;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.hibernate.ScrollMode;
import org.hibernate.Session;
import org.hibernate.Query;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;



public class ResultIteratorTest extends DatabaseTestFixture {

    private Session session;

    @Before
    public void setup() {
        this.session = (Session) this.entityManager().getDelegate();
    }

    @Test
    public void testHasNextWithElements() {
        this.ownerCurator.create(TestUtil.createOwner());
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ResultIterator<Owner> iterator = new ResultIterator<Owner>(
            this.session, query.scroll(ScrollMode.FORWARD_ONLY), 0, false
        );

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

        ResultIterator<Owner> iterator = new ResultIterator<Owner>(
            this.session, query.scroll(ScrollMode.FORWARD_ONLY), 0, false
        );

        try {
            assertFalse(iterator.hasNext());
        }
        finally {
            iterator.close();
        }
    }

    @Test
    public void testNextWithElements() {
        Owner owner1 = TestUtil.createOwner();
        Owner owner2 = TestUtil.createOwner();
        Owner owner3 = TestUtil.createOwner();
        this.ownerCurator.create(owner1);
        this.ownerCurator.create(owner2);
        this.ownerCurator.create(owner3);

        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ResultIterator<Owner> iterator = new ResultIterator<Owner>(
            this.session, query.scroll(ScrollMode.FORWARD_ONLY), 0, false
        );

        try {
            List<Owner> owners = new LinkedList<Owner>();

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

    @Test(expected = NoSuchElementException.class)
    public void testNextWithoutElements() {
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ResultIterator<Owner> iterator = new ResultIterator<Owner>(
            this.session, query.scroll(ScrollMode.FORWARD_ONLY), 0, false
        );

        try {
            iterator.next(); // Kaboom
        }
        finally {
            iterator.close();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemoveAlwaysFails() {
        this.ownerCurator.create(TestUtil.createOwner());
        Query query = this.session.createQuery("SELECT o FROM Owner o");

        ResultIterator<Owner> iterator = new ResultIterator<Owner>(
            this.session, query.scroll(ScrollMode.FORWARD_ONLY), 0, false
        );

        try {
            iterator.next();
            iterator.remove();
        }
        finally {
            iterator.close();
        }
    }


}
