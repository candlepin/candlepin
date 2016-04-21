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

import java.util.NoSuchElementException;



/**
 * The RowResultIterator provides iteration on each row of a ScrollableResults object.
 *
 * ResultIterators should be closed after iteration to close the backing resources. Omitting this
 * step may leave database connections open longer than necessary.
 */
public class RowResultIterator implements ResultIterator<Object[]> {

    private final ScrollableResults cursor;

    private boolean stateCache;
    private boolean useStateCache;

    /**
     * Creates a new RowResultIterator to iterate over the results provided by the given
     * ScrollableResults instance.
     *
     * @param cursor
     *  The ScrollableResults instance over which to iterate
     */
    public RowResultIterator(ScrollableResults cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("cursor is null");
        }

        this.cursor = cursor;

        this.stateCache = false;
        this.useStateCache = false;
    }

    @Override
    public boolean hasNext() {
        if (this.useStateCache) {
            return this.stateCache;
        }

        this.useStateCache = true;
        this.stateCache = this.cursor.next();

        // Automatically close once we've run out of elements
        if (!this.stateCache) {
            this.close();
        }

        return this.stateCache;
    }

    @Override
    public Object[] next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        this.useStateCache = false;
        return this.cursor.get();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException(
            "The remove operation is not supported on ResultIterator instances."
        );
    }

    @Override
    public void close() {
        this.cursor.close();
    }
}
