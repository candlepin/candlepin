/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;



/**
 * Test suite for the CollectionView class
 */
public class CollectionViewTest {

    protected Collection source;
    protected CollectionView testobj;

    @Before
    public void init() {
        this.source = new LinkedList();
        this.testobj = new CollectionView(this.source);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAdd() {
        Object obj = new Object();
        this.testobj.add(obj);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddAll() {
        Collection collection = new LinkedList();
        collection.add(new Object());
        collection.add(new Object());
        collection.add(new Object());

        this.testobj.addAll(collection);
    }

    @Test
    public void testClear() {
        this.source.add(new Object());
        this.source.add(new Object());
        this.source.add(new Object());

        assertEquals(3, this.testobj.size());

        this.testobj.clear();
        assertEquals(0, this.testobj.size());
    }

    @Test
    public void testContains() {
        Object obj = new Object();

        assertFalse(this.testobj.contains(obj));

        this.source.add(new Object());
        this.source.add(new Object());

        assertFalse(this.testobj.contains(obj));

        this.source.add(obj);

        assertTrue(this.testobj.contains(obj));

        this.source.add(new Object());

        assertTrue(this.testobj.contains(obj));

        this.source.remove(obj);

        assertFalse(this.testobj.contains(obj));
    }

    @Test
    public void testContainsAll() {
        Object obj1 = new Object();
        Object obj2 = new Object();
        Object obj3 = new Object();

        Collection collection = new LinkedList();
        collection.add(obj1);
        collection.add(obj2);

        assertFalse(this.testobj.containsAll(collection));

        this.source.add(obj1);
        assertFalse(this.testobj.containsAll(collection));

        this.source.add(obj3);
        assertFalse(this.testobj.containsAll(collection));

        this.source.add(obj2);
        assertTrue(this.testobj.containsAll(collection));

        this.source.add(new Object());
        assertTrue(this.testobj.containsAll(collection));

        this.source.remove(obj3);
        assertTrue(this.testobj.containsAll(collection));

        this.source.remove(obj1);
        assertFalse(this.testobj.containsAll(collection));
    }

    @Test
    public void testIsEmpty() {
        Object obj = new Object();

        assertTrue(this.testobj.isEmpty());

        this.source.add(obj);
        assertFalse(this.testobj.isEmpty());

        this.source.add(new Object());
        assertFalse(this.testobj.isEmpty());

        this.source.remove(obj);
        assertFalse(this.testobj.isEmpty());

        this.source.clear();
        assertTrue(this.testobj.isEmpty());
    }

    @Test
    public void testIterator() {
        for (int i = 0; i < 3; ++i) {
            this.testIteratorImpl();

            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());

            this.testIteratorImpl();
        }
    }

    private void testIteratorImpl() {
        Iterator iterator = this.testobj.iterator();
        Collection found = new LinkedList();
        Collection initial = new LinkedList(this.source);

        while (iterator.hasNext()) {
            Object obj = iterator.next();
            iterator.remove();

            found.add(obj);
        }

        assertEquals(initial.size(), found.size());
        assertTrue(found.containsAll(initial));
        assertTrue(initial.containsAll(found));
        assertTrue(this.source.isEmpty());
    }

    @Test
    public void testRemove() {
        for (int i = 0; i < 5; ++i) {
            assertFalse(this.testobj.remove(String.valueOf(i)));
        }

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
        }

        for (int i = 0; i < 5; ++i) {
            assertTrue(this.testobj.remove(String.valueOf(i)));
        }

        for (int i = 0; i < 5; ++i) {
            assertFalse(this.testobj.remove(String.valueOf(i)));
        }
    }

    @Test
    public void testRemoveAll() {
        Collection remove = Arrays.asList("1", "2", "3");

        assertTrue(this.source.isEmpty());
        assertFalse(this.testobj.removeAll(remove));
        assertTrue(this.source.isEmpty());

        this.source.addAll(Arrays.asList("1", "2", "3"));
        assertTrue(this.testobj.removeAll(remove));
        assertTrue(this.source.isEmpty());

        this.source.addAll(Arrays.asList("3", "4", "5"));
        assertTrue(this.testobj.removeAll(remove));
        assertEquals(2, this.source.size());

        assertFalse(this.testobj.removeAll(remove));
        assertEquals(2, this.source.size());
    }

    @Test
    public void testRetainAll() {
        Collection retain = Arrays.asList("1", "2", "3");

        assertTrue(this.source.isEmpty());
        assertFalse(this.testobj.retainAll(retain));
        assertTrue(this.source.isEmpty());

        for (int i = 1; i <= 5; ++i) {
            this.source.add(String.valueOf(i));

            assertEquals(i > 3, this.testobj.retainAll(retain));
            assertEquals(i < 3 ? i : 3, this.source.size());

            if (i > 2) {
                assertTrue(this.source.containsAll(retain));
                assertTrue(retain.containsAll(this.source));
            }
        }

        for (int i = 4; i <= 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        assertTrue(this.testobj.retainAll(retain));
        assertEquals(3, this.testobj.size());
        assertTrue(this.source.containsAll(retain));
        assertTrue(retain.containsAll(this.source));

        assertTrue(this.testobj.retainAll(Collections.emptyList()));
        assertTrue(this.source.isEmpty());
    }

    @Test
    public void testSize() {
        for (int i = 0; i < 5; ++i) {
            assertEquals(i, this.testobj.size());
            assertEquals(this.source.size(), this.testobj.size());

            this.source.add(new Object());

            assertEquals(i + 1, this.testobj.size());
            assertEquals(this.source.size(), this.testobj.size());
        }
    }

    @Test
    public void testToArray() {
        for (int i = 0; i < 5; ++i) {
            assertEquals(this.source.toArray(), this.testobj.toArray());
            assertTrue(this.testobj.toArray() instanceof Object[]);
            assertEquals(i, this.testobj.toArray().length);

            this.source.add(new Object());

            assertEquals(this.source.toArray(), this.testobj.toArray());
            assertTrue(this.testobj.toArray() instanceof Object[]);
            assertEquals(i + 1, this.testobj.toArray().length);
        }
    }

    @Test
    public void testToArrayWithType() {
        String[] arrType = new String[] {};

        for (int i = 0; i < 5; ++i) {
            assertEquals(this.source.toArray(arrType), this.testobj.toArray(arrType));
            assertTrue(this.testobj.toArray(arrType) instanceof String[]);
            assertEquals(i, this.testobj.toArray(arrType).length);

            this.source.add(String.valueOf(i));

            assertEquals(this.source.toArray(arrType), this.testobj.toArray(arrType));
            assertTrue(this.testobj.toArray(arrType) instanceof String[]);
            assertEquals(i + 1, this.testobj.toArray(arrType).length);
        }
    }

}
