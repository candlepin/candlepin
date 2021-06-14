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

import java.io.Closeable;
import java.util.Iterator;



/**
 * The ResultIterator provides iteration on each row of a ResultSet or ScrollableResults object.
 *
 * ResultIterators should be closed after iteration to close the backing resources. Omitting this
 * step may leave database connections open longer than necessary.
 *
 * @param <T>
 *  The element type to be returned by this iterators's next method
 */
public interface ResultIterator<T> extends Closeable, Iterator<T> {

    boolean hasNext();
    T next();
    void remove();

    /**
     * Closes this ResultIterator and frees its backing database resources.
     */
    void close();

}
