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

import org.candlepin.util.ElementTransformer;

import org.hibernate.Session;
import org.hibernate.criterion.Order;

import java.util.Iterator;
import java.util.List;

import javax.persistence.LockModeType;



/**
 * The CandlepinQuery interface defines a fluent-style criteria blueprint for configuring and
 * executing criteria, and methods for processing the results.
 *
 * @param <T>
 *  The element type to be returned by this query's result output methods
 */
public interface CandlepinQuery<T> extends Iterable<T> {

    // TODO:
    // Add support for stateless sessions (which requires some workarounds because stateless sessions
    // and sessions don't have a common parent class)

    /**
     * Sets the session to be used for executing this query
     *
     * @param session
     *  The session to use for executing this query
     *
     * @throws IllegalArgumentException
     *  if session is null
     *
     * @return
     *  this query instance
     */
    CandlepinQuery<T> useSession(Session session);

    /**
     * Sets the offset (first result) into a result set at which to begin fetching results.
     *
     * @param offset
     *  The offset at which to begin fetching results when executing this query. Negative values
     *  will clear any previously set offset.
     *
     * @return
     *  this query instance
     */
    CandlepinQuery<T> setFirstResult(int offset);

    /**
     * Sets the maximum results to be returned when executing this query.
     *
     * @param limit
     *  The maximum number of results to be returned when executing this query. Negative values
     *  will disable any previously set limits.
     *
     * @return
     *  this query instance
     */
    CandlepinQuery<T> setMaxResults(int limit);

    /**
     * Adds the specified ordering when executing this query.
     *
     * @param order
     *  The ordering to apply when executing this query
     *
     * @return
     *  this query instance
     */
    CandlepinQuery<T> addOrder(Order order);

    /**
     * Sets the locking mode for the query. The lock mode will be applied to the database rows
     * representing the entities returned by this query.
     *
     * @param lockMode
     *  The lock mode to apply when executing this query
     *
     * @return
     *  this query instance
     */
    CandlepinQuery<T> setLockMode(LockModeType lockMode);

    /**
     * Returns a CandlepinQuery instance that transforms the results using the given element
     * transformer.
     *
     * @param transformer
     *  The ElementTransformer to use for processing or transforming elements fetched by this
     *  query
     *
     * @return
     *  A transformed CandlepinQuery instance
     */
    <O> CandlepinQuery<O> transform(ElementTransformer<T, O> transformer);

    // TODO:
    // Add some other utility/passthrough methods as a need arises:
    //  - setReadOnly
    //  - setFetchMode
    //  - setCacheMode/setCacheable

    /**
     * Executes this query and returns the entities as a list. If no entities could be found,
     * this method returns an empty list.
     * <p></p>
     * <strong>Warning</strong>:
     * This method loads the entire result set into memory. As such, this method should not be used
     * with queries that can return extremely large data sets.
     *
     * @return
     *  a list containing the results of executing this criteria
     */
    List<T> list();

    /**
     * Steps through the results of a column of the given query row-by-row, rather than dumping the
     * entire query result into memory before processing it. This method will always pass the first
     * column of each row to the processor.
     *
     * @param processor
     *  A ResultProcessor instance to use for processing each result
     *
     * @return
     *  the number of rows processed and sent to the result processor
     */
    int forEach(ResultProcessor<T> processor);

    /**
     * Steps through the results of a column of the given query row-by-row, rather than dumping the
     * entire query result into memory before processing it.
     *
     * @param column
     *  The zero-indexed offset of the column to process
     *
     * @param processor
     *  A ResultProcessor instance to use for processing each result
     *
     * @return
     *  the number of rows processed and sent to the result processor
     */
    int forEach(int column, ResultProcessor<T> processor);

    /**
     * Steps through the results of a query row-by-row, rather than dumping the entire query result
     * into memory before processing it.
     * <p/>
     * If this method is called with eviction enabled, the result processor must manually persist
     * and flush each object that is changed, as any unflushed changes will be lost when the object
     * is evicted.
     *
     * @param evict
     *  Whether or not to auto-evict queried objects after they've been processed
     *
     * @param processor
     *  A ResultProcessor instance to use for processing each result
     *
     * @return
     *  the number of rows processed and sent to the result processor
     */
    int forEach(int column, boolean evict, ResultProcessor<T> processor);

    /**
     * Steps through the results of a query row-by-row, rather than dumping the entire query result
     * into memory before processing it. Unlike the standard forEach method, this method sends each
     * row to the processor without performing any preprocessing, transformation or cleanup.
     *
     * @param processor
     *  A ResultProcessor instance to use for processing each row
     *
     * @return
     *  the number of rows processed by the result processor
     */
    int forEachRow(ResultProcessor<Object[]> processor);

    /**
     * Executes this query and iterates over the first column of the results. Other columns in
     * each row are silently discarded. This method is functionally identical to iterate, and is
     * only provided for compatibility with the foreach construct.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the first column of the results
     */
    Iterator<T> iterator();

    /**
     * Executes this query and iterates over the first column of the results. Other columns in
     * each row are silently discarded.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the first column of the results
     */
    ResultIterator<T> iterate();

    /**
     * Executes this query and iterates over the specified column of the results. Other columns
     * in each row are silently discarded.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @param column
     *  The zero-indexed offset of the column to iterate
     *
     * @return
     *  an iterator over the specified column of the results
     */
    ResultIterator<T> iterate(int column);

    /**
     * Executes this query and iterates over the specified column of the results, optionally
     * automatically evicting returned entities after they are processed. Other columns in each row
     * are silently discarded.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @param column
     *  The zero-indexed offset of the column to iterate
     *
     * @param evict
     *  Whether or not to auto-evict queried objects after they've been processed
     *
     * @return
     *  an iterator over the specified column of the results
     */
    ResultIterator<T> iterate(int column, boolean evict);

    /**
     * Executes this query and iterates over the rows of results. Unlike the standard forEach
     * method, this method sends each row to the processor without performing any preprocessing,
     * transformation or cleanup.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the rows in the query results
     */
    ResultIterator<Object[]> iterateByRow();

    /**
     * Executes this query and returns a single, unique entity. If no entities could be found,
     * this method returns null. If more than one entity is found, a runtime exception will be
     * thrown.
     *
     * @return
     *  a single entity, or null if no entities were found
     */
    T uniqueResult();

    /**
     * Executes this query and fetches the number of results.
     *
     * @return
     *  the number of results found by executing this query
     */
    int getRowCount();

}
