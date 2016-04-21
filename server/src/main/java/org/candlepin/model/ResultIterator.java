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

import org.hibernate.ScrollableResults;
import org.hibernate.Session;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * The ResultIterator provides iteration on a ScrollableResults object, returning only the values
 * from a specific column in each row.
 *
 * ResultIterators should be closed after iteration to close the backing resources. Omitting this
 * step will prevent some elements from being evicted and will leave database connections open
 * longer than necessary.
 *
 * @param <E> The element type to be returned by this iterator's "next" method.
 */
public class ResultIterator<E> implements Closeable, Iterator<E> {

    private final Session session;
    private final ScrollableResults cursor;
    private final int column;
    private final boolean evict;

    private boolean stateCache;
    private boolean useStateCache;
    private E toEvict;

    /**
     * Creates a new ResultIterator to iterate over the results provided by the
     * given ScrollableResults instance, returning only the values in the column specified.
     *
     * @param session
     *  The session from which the results originate
     *
     * @param cursor
     *  The ScrollableResults instance over which to iterate
     *
     * @param column
     *  The zero-indexed offset of the column to process
     *
     * @param evict
     *  Whether or not to auto-evict queried objects after they've been processed
     */
    public ResultIterator(Session session, ScrollableResults cursor, int column, boolean evict) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        if (cursor == null) {
            throw new IllegalArgumentException("cursor is null");
        }

        this.session = session;
        this.cursor = cursor;
        this.column = column;
        this.evict = evict;

        this.stateCache = false;
        this.useStateCache = false;
        this.toEvict = null;
    }

    @Override
    public boolean hasNext() {
        if (this.toEvict != null) {
            this.session.evict(this.toEvict);
            this.toEvict = null;
        }

        if (this.useStateCache) {
            return this.stateCache;
        }

        this.useStateCache = true;
        this.stateCache = this.cursor.next();

        return this.stateCache;
    }

    @Override
    public E next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        this.useStateCache = false;
        E element = (E) this.cursor.get(this.column);

        if (this.evict) {
            this.toEvict = element;
        }

        return element;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException(
            "The remove operation is not supported on ResultIterator instances."
        );
    }

    @Override
    public void close() {
        if (this.toEvict != null) {
            this.session.evict(this.toEvict);
            this.toEvict = null;
        }

        this.cursor.close();
    }
}
