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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

/**
 * Test suite for the ListView class
 */
public class ListViewTest extends SetViewTest {
    @BeforeEach
    @Override
    public void init() {
        this.source = new LinkedList();
        this.testobj = new ListView((List) this.source);
    }

    @Test
    public void testAddFromIndex() {
        assertThrows(UnsupportedOperationException.class,
            () -> ((ListView) this.testobj).add(0, new Object()));
    }

    @Test
    public void testAddAllFromIndex() {
        List collection = new LinkedList();
        collection.add(new Object());
        collection.add(new Object());
        collection.add(new Object());

        assertThrows(UnsupportedOperationException.class,
            () -> ((ListView) this.testobj).addAll(0, collection));
    }

    @Test
    @Override
    public void testEquals() {
        List comp = new LinkedList();

        assertTrue(this.testobj.equals(comp));

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
            assertFalse(this.testobj.equals(comp));

            comp.add(String.valueOf(i));
            assertTrue(this.testobj.equals(comp));
        }

        this.source.clear();
        assertFalse(this.testobj.equals(comp));

        comp.clear();
        assertTrue(this.testobj.equals(comp));
    }

    @Test
    public void testGet() {
        for (int i = 0; i < 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        for (int i = 9; i >= 0; --i) {
            Object fetched = ((ListView) this.testobj).get(i);

            assertEquals(String.valueOf(i), fetched);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "4" })
    public void testGetWithBadIndexes(int index) {
        this.source.addAll(Arrays.asList("1", "2", "3"));
        assertThrows(IndexOutOfBoundsException.class, () -> ((ListView) this.testobj).get(index));
    }

    @Test
    @Override
    public void testHashCode() {
        List comp = new LinkedList();

        assertEquals(comp.hashCode(), this.testobj.hashCode());

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
            assertNotEquals(comp.hashCode(), this.testobj.hashCode());

            comp.add(String.valueOf(i));
            assertEquals(comp.hashCode(), this.testobj.hashCode());
        }

        this.source.clear();
        assertNotEquals(comp.hashCode(), this.testobj.hashCode());

        comp.clear();
        assertEquals(comp.hashCode(), this.testobj.hashCode());
    }

    @Test
    public void testIndexOf() {
        this.source.addAll(Arrays.asList("1", "1", "2", "2", "3", "3"));

        assertEquals(-1, ((ListView) this.testobj).indexOf("0"));
        assertEquals(0, ((ListView) this.testobj).indexOf("1"));
        assertEquals(2, ((ListView) this.testobj).indexOf("2"));
        assertEquals(4, ((ListView) this.testobj).indexOf("3"));
        assertEquals(-1, ((ListView) this.testobj).indexOf("4"));
    }

    @Test
    public void testLastIndexOf() {
        this.source.addAll(Arrays.asList("1", "1", "2", "2", "3", "3"));

        assertEquals(-1, ((ListView) this.testobj).lastIndexOf("0"));
        assertEquals(1, ((ListView) this.testobj).lastIndexOf("1"));
        assertEquals(3, ((ListView) this.testobj).lastIndexOf("2"));
        assertEquals(5, ((ListView) this.testobj).lastIndexOf("3"));
        assertEquals(-1, ((ListView) this.testobj).lastIndexOf("4"));
    }

    @Test
    public void testListIterator() {
        for (int i = 0; i < 3; ++i) {
            this.testListIteratorAscending(-1);
            this.testListIteratorDescending(-1);

            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());

            this.testListIteratorAscending(-1);
            this.testListIteratorDescending(-1);
        }
    }

    @Test
    public void testListIteratorWithOffset() {
        for (int i = 0; i < 3; ++i) {
            this.testListIteratorAscending(0);
            this.testListIteratorDescending(0);

            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());
            this.source.add(new Object());

            for (int j = 0; j < this.source.size(); ++j) {
                this.testListIteratorAscending(j);
                this.testListIteratorDescending(j);
            }
        }
    }

    private void testListIteratorAscending(int offset) {
        ListIterator iterator = offset > -1 ?
            ((ListView) this.testobj).listIterator(offset) :
            ((ListView) this.testobj).listIterator();

        int expectedRemoved = offset > -1 ? this.source.size() - offset : this.source.size();
        int expectedRemaining = this.source.size() - expectedRemoved;
        int expectedNextIndex = offset > -1 ? offset : 0;
        List found = new LinkedList();
        List expected = new LinkedList(
            ((List) this.source).subList(offset > -1 ? offset : 0, this.source.size())
        );

        while (iterator.hasNext()) {
            assertEquals(expectedNextIndex++, iterator.nextIndex());

            Object obj = iterator.next();
            found.add(obj);
        }

        iterator = offset > -1 ?
            ((ListView) this.testobj).listIterator(offset) :
            ((ListView) this.testobj).listIterator();

        expectedNextIndex = offset > -1 ? offset : 0;

        while (iterator.hasNext()) {
            assertEquals(expectedNextIndex, iterator.nextIndex());

            iterator.next();
            iterator.remove();
        }

        assertEquals(expectedRemaining, this.source.size());
        assertEquals(expectedRemoved, found.size());

        assertTrue(found.containsAll(expected));
        assertTrue(expected.containsAll(found));
    }

    private void testListIteratorDescending(int offset) {
        ListIterator iterator = offset > -1 ?
            ((ListView) this.testobj).listIterator(offset) :
            ((ListView) this.testobj).listIterator();

        int expectedRemoved = offset > -1 ? offset : 0;
        int expectedRemaining = this.source.size() - expectedRemoved;
        int expectedPrevIndex = offset > -1 ? offset - 1 : -1;
        List found = new LinkedList();
        List expected = offset > -1 ?
            new LinkedList(((List) this.source).subList(0, offset)) :
            new LinkedList(this.source);

        while (iterator.hasPrevious()) {
            assertEquals(expectedPrevIndex--, iterator.previousIndex());

            Object obj = iterator.previous();
            found.add(obj);
        }

        iterator = offset > -1 ?
            ((ListView) this.testobj).listIterator(offset) :
            ((ListView) this.testobj).listIterator();

        expectedPrevIndex = offset > -1 ? offset : 0;

        while (iterator.hasPrevious()) {
            assertEquals(expectedPrevIndex, iterator.previousIndex());

            iterator.previous();
            iterator.remove();
        }

        assertEquals(expectedRemaining, this.source.size());
        assertEquals(expectedRemoved, found.size());

        assertTrue(found.containsAll(expected));
        assertTrue(expected.containsAll(found));
    }

    @Test
    public void testListIteratorAddition() {
        this.source.addAll(Arrays.asList("1", "2", "3"));

        ListIterator iterator = ((ListView) this.testobj).listIterator();
        assertThrows(UnsupportedOperationException.class, () -> iterator.add("A"));
    }

    @Test
    public void testListIteratorReplace() {
        this.source.addAll(Arrays.asList("1", "2", "3"));

        ListIterator iterator = ((ListView) this.testobj).listIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, () -> iterator.set("A"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "4" })
    public void testListIteratorWithBadIndexes(int index) {
        this.source.addAll(Arrays.asList("1", "2", "3"));
        assertThrows(IndexOutOfBoundsException.class, () -> ((ListView) this.testobj).listIterator(index));
    }

    @Test
    public void testRemoveFromIndex() {
        // Ascending
        for (int i = 0; i < 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        for (int i = 0; i < 10; ++i) {
            Object fetched = ((ListView) this.testobj).remove(0);
            assertEquals(String.valueOf(i), fetched);
        }

        assertEquals(0, this.source.size());
        assertEquals(0, this.testobj.size());

        // Descending
        for (int i = 0; i < 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        for (int i = 9; i >= 0; --i) {
            Object fetched = ((ListView) this.testobj).remove(i);
            assertEquals(String.valueOf(i), fetched);
        }

        assertEquals(0, this.source.size());
        assertEquals(0, this.testobj.size());

        // Pseudo-random
        for (int i = 0; i < 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        assertEquals("0", ((ListView) this.testobj).remove(0));
        assertEquals("3", ((ListView) this.testobj).remove(2));
        assertEquals("6", ((ListView) this.testobj).remove(4));
        assertEquals("9", ((ListView) this.testobj).remove(6));

        assertEquals(6, this.source.size());
        assertEquals(6, this.testobj.size());
        assertEquals("1", ((List) this.source).get(0));
        assertEquals("2", ((List) this.source).get(1));
        assertEquals("4", ((List) this.source).get(2));
        assertEquals("5", ((List) this.source).get(3));
        assertEquals("7", ((List) this.source).get(4));
        assertEquals("8", ((List) this.source).get(5));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "4" })
    public void testRemoveWithBadIndexes(int index) {
        this.source.addAll(Arrays.asList("1", "2", "3"));
        assertThrows(IndexOutOfBoundsException.class, () -> ((ListView) this.testobj).remove(index));
    }

    @Test
    public void testSet() {
        this.source.add("1");

        try {
            ((ListView) this.testobj).set(0, "2");

            fail("Expected an UnsupportedOperationException, but no exception was raised.");
        }
        catch (UnsupportedOperationException e) {
            // This is what we want.
        }

        assertTrue(this.source.contains("1"));
        assertFalse(this.source.contains("2"));
    }

    @Test
    public void testSubList() {
        for (int i = 0; i < 10; ++i) {
            this.source.add(String.valueOf(i));
        }

        for (int i = 0; i < this.source.size() - 3; ++i) {
            List sublist = ((ListView) this.testobj).subList(i, i + 3);

            // We can get away with not testing every method on the sublist if we verify that it's
            // also a ListView instance (and, thus, already tested)
            assertTrue(sublist instanceof ListView);
            assertEquals(3, sublist.size());
            for (int j = i; j < i + 3; ++j) {
                assertTrue(sublist.contains(String.valueOf(j)));
            }
        }

        for (int i = 0; i < this.source.size(); ++i) {
            List sublist = ((ListView) this.testobj).subList(i, i);

            assertTrue(sublist instanceof ListView);
            assertEquals(0, sublist.size());
        }
    }

    protected static Stream<Object[]> paramsForSubListIndexOutOfBoundsTest() {
        return Stream.of(
            new Object[] { -1, 0, IndexOutOfBoundsException.class },
            new Object[] { 0, -1, IllegalArgumentException.class },
            new Object[] { 2, 1, IllegalArgumentException.class },
            new Object[] { 4, 5, IndexOutOfBoundsException.class },
            new Object[] { 1, 5, IndexOutOfBoundsException.class }
        );
    }

    @ParameterizedTest
    @MethodSource("paramsForSubListIndexOutOfBoundsTest")
    public void testSubListIndexOutOfBounds(int begin, int end, Class<? extends Throwable> expected) {
        this.source.addAll(Arrays.asList("1", "2", "3"));
        assertThrows(expected, () -> ((ListView) this.testobj).subList(begin, end));
    }

}
