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



/**
 * The ResultProcessor interface is used for processing query results in a row-by-row manner.
 * <p/>
 * ResultProcessors can abort processing of a query's results at any time by returning false from
 * the process method. Doing so will close the backing cursor immediately, discarding the remaining
 * rows.
 *
 * @param <T>
 *  The type to be processed by this result processor
 */
public interface ResultProcessor<T> {

    /**
     * Processes a given row of a query. This will likely be called multiple times for a query
     *
     * @param row
     *  The row or result to process
     *
     * @return
     *  true if the query using this result processor should continue processing rows; false otherwise
     */
    boolean process(T row);

}
