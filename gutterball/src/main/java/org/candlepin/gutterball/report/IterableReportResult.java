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

package org.candlepin.gutterball.report;

import java.util.Iterator;


/**
 * The IterableReportResult class represents an iterator to be used as a ReportResult.
 */
public class IterableReportResult<E> implements Iterator<E>, ReportResult {

    private Iterator<E> iterator;

    public IterableReportResult(Iterator<E> iterator) {
        if (iterator == null) {
            throw new IllegalArgumentException("iterator is null");
        }

        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public E next() {
        return this.iterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException(
            "The remove operation is not supported on IterableReportResult instances."
        );
    }

}
