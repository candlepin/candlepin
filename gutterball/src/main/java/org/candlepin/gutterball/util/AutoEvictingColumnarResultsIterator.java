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

package org.candlepin.gutterball.util;

import org.hibernate.ScrollableResults;
import org.hibernate.Session;



/**
 * The AutoEvictingColumnarResultsIterator extends the ColumnarScrollableResultsIterator to add
 * automatic eviction of the elements returned by the "next" method.
 *
 * @param <E> The element type to be returned by this iterator's "next" method.
 */
public class AutoEvictingColumnarResultsIterator<E> extends ColumnarScrollableResultsIterator<E> {
    private Session session;
    private E prev;

    /**
     * Creates a new AutoEvictingColumnarResultsIterator to iterate over the results provided by the
     * given ScrollableResults instance, returning only the values in the column specified.
     *
     * @param session
     *  The Session to close upon completion of this iterator and from which to evict returned
     *  objects.
     *
     * @param results
     *  The ScrollableResults instance over which to iterate.
     *
     * @param column
     *  The column from which to read values to be returned.
     */
    public AutoEvictingColumnarResultsIterator(Session session, ScrollableResults results, int column) {
        super(results, column);

        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        this.prev = null;
    }

    @Override
    public E next() {
        if (this.prev != null) {
            // TODO:
            // This does not evict collections or any persistent objects contained by the to-be
            // evicted object. Should probably improve this at some point.
            this.session.evict(this.prev);
            this.prev = null;
        }

        this.prev = super.next();
        return this.prev;
    }

}
