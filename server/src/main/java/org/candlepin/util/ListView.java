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

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;



/**
 * The SetView is a pass-through collection which restricts adding elements to the backing set,
 * allowing only reading and removing elements.
 *
 * Unlike the CollectionView, the ListView class can only be used to provides views of lists.
 * However, this allows it to safely pass calls to .equals and .hashCode to its backing set.
 *
 * @param <E>
 *  The element type
 */
public class ListView<E> extends CollectionView<E> implements List<E> {

    /**
     * The ListViewIterator provides a restricted implementation of the ListIterator interface,
     * allowing elements to be read or removed, but not added to the backing list.
     * @param <E>
     *  The element type
     */
    private static class ListViewIterator<E> implements ListIterator<E> {
        protected final ListIterator<E> iterator;

        public ListViewIterator(ListIterator<E> iterator) {
            if (iterator == null) {
                throw new IllegalArgumentException("iterator is null");
            }

            this.iterator = iterator;
        }

        /**
         * Throws an UnsupportedOperationException
         *
         * @param element
         *
         * @throws UnsupportedOperationException
         */
        public void add(E element) {
            throw new UnsupportedOperationException();
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasPrevious() {
            return this.iterator.hasPrevious();
        }

        /**
         * {@inheritDoc}
         */
        public E next() {
            return this.iterator.next();
        }

        /**
         * {@inheritDoc}
         */
        public int nextIndex() {
            return this.iterator.nextIndex();
        }

        /**
         * {@inheritDoc}
         */
        public E previous() {
            return this.iterator.previous();
        }

        /**
         * {@inheritDoc}
         */
        public int previousIndex() {
            return this.iterator.previousIndex();
        }

        /**
         * {@inheritDoc}
         */
        public void remove() {
            this.iterator.remove();
        }

        /**
         * Throws an UnsupportedOperationException
         *
         * @param element
         *
         * @throws UnsupportedOperationException
         */
        public void set(E element) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Creates a new ListView instance backed by the provided list.
     *
     * @param list
     *  The list to use as the backing list
     *
     * @throws IllegalArgumentException
     *  if the provided list is null
     */
    public ListView(List<E> list) {
        super(list);
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param index
     * @param element
     *
     * @throws UnsupportedOperationException
     */
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param index
     * @param collection
     *
     * @throws UnsupportedOperationException
     *
     * @return boolean
     */
    public boolean addAll(int index, Collection<? extends E> collection) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        return this.collection.equals(obj);
    }

    /**
     * {@inheritDoc}
     */
    public E get(int index) {
        return ((List<E>) this.collection).get(index);
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return this.collection.hashCode();
    }


    /**
     * {@inheritDoc}
     */
    public int indexOf(Object obj) {
        return ((List<E>) this.collection).indexOf(obj);
    }

    /**
     * {@inheritDoc}
     */
    public int lastIndexOf(Object obj) {
        return ((List<E>) this.collection).lastIndexOf(obj);
    }

    /**
     * {@inheritDoc}
     */
    public ListIterator<E> listIterator() {
        return new ListViewIterator<>(((List<E>) this.collection).listIterator());
    }

    /**
     * {@inheritDoc}
     */
    public ListIterator<E> listIterator(int index) {
        return new ListViewIterator<>(((List<E>) this.collection).listIterator(index));
    }

    /**
     * {@inheritDoc}
     */
    public E remove(int index) {
        return ((List<E>) this.collection).remove(index);
    }

    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a view of the portion of this list between the specified fromIndex, inclusive, and
     * toIndex, exclusive (if fromIndex and toIndex are equal, the returned list is empty). The
     * returned list is backed by this list, so non-structural changes in the returned list are
     * reflected in this list, and vice-versa. The returned list supports all of the optional list
     * operations supported by this list.
     *
     * The list returned by this method will inherit the same write restrictions present on this
     * list. Reads and removals are allowed, but any addition will result in an
     * UnsupportedOperationException
     *
     * @param fromIndex
     *  low endpoint (inclusive) of the subList
     *
     * @param toIndex
     *  high endpoint (exclusive) of the subList
     *
     * @throws IndexOutOfBoundsException
     *  for an illegal endpoint index value (fromIndex < 0 || toIndex > size || fromIndex > toIndex)
     *
     * @return
     *  a view of the specified range within this list
     */
    public List<E> subList(int fromIndex, int toIndex) {
        return new ListView<>(((List<E>) this.collection).subList(fromIndex, toIndex));
    }

}
