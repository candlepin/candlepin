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
import java.util.Iterator;



/**
 * The CollectionView is a pass-through collection which restricts adding elements to the
 * backing collection, allowing only reading and removing elements.
 *
 * The CollectionView object does not implement .equals or .hashCode, nor does it pass these calls
 * through to its backing collection. This is done to maintain consistency when comparing Lists and
 * Sets, and is in line with the contract on both methods, and implementations provided by the
 * Collections.unmodifiableCollection function.
 *
 * @param <E>
 *  The element type
 */
public class CollectionView<E> implements Collection<E> {

    protected final Collection<E> collection;

    /**
     * Creates a new CollectionView instance backed by the provided collection.
     *
     * @param collection
     *  The collection to use as the backing collection
     *
     * @throws IllegalArgumentException
     *  if the provided collection is null
     */
    public CollectionView(Collection<E> collection) {
        if (collection == null) {
            throw new IllegalArgumentException("collection is null");
        }

        this.collection = collection;
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param element
     *
     * @throws UnsupportedOperationException
     *
     * @return boolean
     */
    public boolean add(E element) {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws an UnsupportedOperationException
     *
     * @param collection
     *
     * @throws UnsupportedOperationException
     *
     * @return boolean
     */
    public boolean addAll(Collection<? extends E> collection) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        this.collection.clear();
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(Object o) {
        return this.collection.contains(o);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsAll(Collection<?> c) {
        return this.collection.containsAll(c);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty() {
        return this.collection.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<E> iterator() {
        return this.collection.iterator();
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(Object o) {
        return this.collection.remove(o);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(Collection<?> c) {
        return this.collection.removeAll(c);
    }

    /**
     * {@inheritDoc}
     */
    public boolean retainAll(Collection<?> c) {
        return this.collection.retainAll(c);
    }

    /**
     * {@inheritDoc}
     */
    public int size() {
        return this.collection.size();
    }

    /**
     * {@inheritDoc}
     */
    public Object[] toArray() {
        return this.collection.toArray();
    }

    /**
     * {@inheritDoc}
     */
    public <T> T[] toArray(T[] a) {
        return this.collection.toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().getName());

        builder.append(" [");
        Iterator<E> iterator = this.collection.iterator();
        while (iterator.hasNext()) {
            builder.append(iterator.next());

            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(']');

        return builder.toString();
    }

}
