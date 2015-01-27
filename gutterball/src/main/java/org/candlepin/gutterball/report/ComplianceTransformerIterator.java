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

import org.candlepin.gutterball.model.snapshot.Compliance;

import java.util.Iterator;


/**
 * An iterator that transforms Compliance records into objects of type T as we iterate through them.
 * This class wraps an iterator of Compliance objects that typically come from the DB.
 *
 * @param <T> the type of object this iterator converts a Compliance snapshot into.
 */
public abstract class ComplianceTransformerIterator<T> implements Iterator<T>, ReportResult {

    protected Iterator<Compliance> dbIterator;

    public ComplianceTransformerIterator(Iterator<Compliance> dbIterator) {
        this.dbIterator = dbIterator;
    }

    @Override
    public boolean hasNext() {
        return dbIterator.hasNext();
    }

    @Override
    public T next() {
        return convertDbObject(dbIterator.next());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("The remove operation is not supported on " +
                "IterableReportResult instances.");
    }

    abstract T convertDbObject(Compliance compliance);
}
