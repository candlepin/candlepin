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
 * The AutoEvictingResultsIterator extends the ScrollableResultsIterator by automatically evicting
 * Hibernate objects previously returned each time a new object is returned.
 *
 * @param <E> The element type to be returned by this iterator's "next" method.
 */
public class AutoEvictingResultsIterator<E> extends ScrollableResultsIterator<E> {
    private Session session;
    private E prev;

    public AutoEvictingResultsIterator(Session session, ScrollableResults results) {
        super(results);

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
