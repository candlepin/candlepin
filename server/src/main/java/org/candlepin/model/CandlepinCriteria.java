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

import org.hibernate.Session;
import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;

import java.util.List;



/**
 * The CandlepinCriteria class represents a criteria and provides fluent-style methods for
 * configuring how the criteria is to be executed and how the result should be processed.
 *
 * @param <T>
 *  The entity type to be returned by this criteria's result output methods
 */
public class CandlepinCriteria<T> {

    protected DetachedCriteria criteria;
    protected Session session;

    // TODO:
    // Add support for stateless sessions (which requires some workarounds because stateless sessions
    // and sessions don't have a common parent class)

    /**
     * Creates a new CandlepinCriteria instance using the specified criteria and session.
     *
     * @param criteria
     *  The detached criteria to execute
     *
     * @param session
     *  The session to use to execute the given criteria
     *
     * @throws IllegalArgumentException
     *  if either criteria or session are null
     */
    public CandlepinCriteria(DetachedCriteria criteria, Session session) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }

        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.criteria = criteria;
        this.session = session;
    }

    /**
     * Sets the session to be used for executing this criteria
     *
     * @param session
     *  The session to use for executing this criteria
     *
     * @throws IllegalArgumentException
     *  if session is null
     *
     * @return
     *  this criteria instance
     */
    public CandlepinCriteria<T> useSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        return this;
    }

    // TODO:
    // Add some other utility/passthrough methods as a need arises:
    //  - setReadOnly
    //  - setFirstResults
    //  - setMaxResults
    //  - setOrder
    //  - setFetchMode
    //  - setCacheMode/setCacheable

    /**
     * Retreives an executable criteria and configures it to be ready to run the criteria with the
     * configuration set by this criteria instance.
     *
     * @return
     *  a fully configured, executable criteria
     */
    protected Criteria getExecutableCriteria() {
        Criteria executable = this.criteria.getExecutableCriteria(this.session);

        // TODO:
        // Apply pending changes to the executable criteria:
        //  - read only
        //  - first/max results, order
        //  - fetch and cache mode

        return executable;
    }

    /**
     * Executes this criteria and returns the entities as a list. If no entities could be found,
     * this method returns an empty list.
     * <p></p>
     * <strong>Warning</strong>:
     * This method loads the entire result set into memory. As such, this method should not be used
     * with queries that can return extremely large data sets.
     *
     * @return
     *  a list containing the results of executing this criteria
     */
    @SuppressWarnings("unchecked")
    public List<T> list() {
        Criteria executable = this.getExecutableCriteria();
        return (List<T>) executable.list();
    }

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
    public int scroll(ResultProcessor<E> processor) {
        return this.scroll(0, false, processor);
    }

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
    public int scroll(Criteria query, int column, ResultProcessor<E> processor) {
        return this.scroll(query, column, false, processor);
    }

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
    @Transactional
    @SuppressWarnings("unchecked")
    public int scroll(int column, boolean evict, ResultProcessor<E> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor is null");
        }

        Criteria executable = this.getExecutableCriteria();

        // We always override the cache mode here to ensure we don't evict things that may be in
        // cache from another request.
        if (evict) {
            executable.setCacheMode(CacheMode.GET);
        }

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;

        try {
            boolean cont = true;

            if (evict) {
                Session session = this.currentSession();

                while (cont && cursor.next()) {
                    E result = (E) cursor.get(column);

                    cont = processor.process(result);
                    session.evict(result);

                    ++count;
                }
            }
            else {
                while (cont && cursor.next()) {
                    cont = processor.process((E) cursor.get(column));
                    ++count;
                }
            }
        }
        finally {
            cursor.close();
        }

        return count;
    }

    /**
     * Steps through the results of a query row-by-row, rather than dumping the entire query result
     * into memory before processing it. Unlike the base scroll method, this method sends each row
     * to the processor without performing any preprocessing or cleanup.
     *
     * @param processor
     *  A ResultProcessor instance to use for processing each row
     *
     * @return
     *  the number of rows processed by the result processor
     */
    @Transactional
    public int scrollByRow(ResultProcessor<Object[]> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor is null");
        }

        Criteria executable = this.getExecutableCriteria();
        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        int count = 0;

        try {
            boolean cont = true;

            while (cont && cursor.next()) {
                cont = processor.process(cursor.get());
                ++count;
            }
        }
        finally {
            cursor.close();
        }

        return count;
    }

    /**
     * Executes this criteria and iterates over the first column of the results. Other columns in
     * each row are silently discarded.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *
     */
    public ColumnarResultIterator<T> iterate() {
        return this.iterate(0, false);
    }

    /**
     * Executes this criteria and iterates over the specified column of the results. Other columns
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
    public ColumnarResultIterator<T> iterate(int column) {
        return this.iterate(column, false);
    }

    /**
     * Executes this criteria and iterates over the specified column of the results, optionally
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
    public ColumnarResultIterator<T> iterate(int column, boolean evict) {
        Criteria executable = this.getExecutableCriteria();

        // We always override the cache mode here to ensure we don't evict things that may be in
        // cache from another request.
        if (evict) {
            executable.setCacheMode(CacheMode.GET);
        }

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        return new ColumnarResultIterator<T>(this.session, cursor, column, evict);
    }

    /**
     * Executes this criteria and iterates over the rows of results.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the rows in the query results
     */
    public ResultIterator iterateByRow() {
        Criteria executable = this.getExecutableCriteria();

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        return new ResultIterator(cursor);
    }

    /**
     * Executes this criteria and returns a single, unique entity. If no entities could be found,
     * this method returns null. If more than one entity is found, a runtime exception will be
     * thrown.
     *
     * @return
     *  a single entity, or null if no entities were found
     */
    @SuppressWarnings("unchecked")
    public T uniqueResult() {
        Criteria executable = this.getExecutableCriteria();
        return (T) executable.uniqueResult();
    }

}
