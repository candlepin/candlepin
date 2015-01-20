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

import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * The ScrollableResultsIterator provides iteration on a ScrollableResults object.
 */
public class ScrollableResultsIterator<E> implements Iterator<E> {

    private ScrollableResults results;

    private boolean cache;
    private boolean useCache;


    public ScrollableResultsIterator(ScrollableResults results) {
        if (results == null) {
            throw new IllegalArgumentException("results is null");
        }

        this.results = results;

        this.cache = false;
        this.useCache = false;
    }

    @Override
    public boolean hasNext() {
        if (this.useCache) {
            return this.cache;
        }

        this.useCache = true;
        this.cache = this.results.next();

        return this.cache;
    }

    @Override
    public E next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        this.useCache = false;
        return (E) this.results.get(0);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException(
            "The remove operation is not supported on ScrollableResultsIterator instances."
        );
    }

}
