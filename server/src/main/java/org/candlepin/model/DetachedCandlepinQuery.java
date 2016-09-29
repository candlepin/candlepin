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

import com.google.inject.persist.Transactional;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;

// Potentially temporary entries. Sort these into the above blob if we're keeping them.
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.loader.criteria.CriteriaQueryTranslator;
import org.hibernate.type.Type;

import java.util.Collections;
import java.util.List;



/**
 * The DetachedCandlepinQuery class represents a detached criteria and provides fluent-style methodsfor
 *  configuring how the criteria is to be executed and how the result should be processed.
 *
 * @param <T>
 *  The entity type to be returned by this criteria's result output methods
 */
public class DetachedCandlepinQuery<T> implements CandlepinQuery<T> {

    protected Session session;
    protected DetachedCriteria criteria;

    protected int offset;
    protected int limit;

    // TODO:
    // Add support for stateless sessions (which requires some workarounds because stateless sessions
    // and sessions don't have a common parent class)

    /**
     * Creates a new DetachedCandlepinQuery instance using the specified criteria and session.
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
    public DetachedCandlepinQuery(Session session, DetachedCriteria criteria) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }

        this.session = session;
        this.criteria = criteria;

        this.offset = -1;
        this.limit = -1;
    }

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
        //  - first results, order
        //  - fetch and cache mode

        if (this.offset > -1) {
            executable.setFirstResult(this.offset);
        }

        if (this.limit > -1) {
            executable.setMaxResults(this.limit);
        }

        return executable;
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
    @Override
    public CandlepinQuery<T> useSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }

        this.session = session;
        return this;
    }

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
    @Override
    public CandlepinQuery<T> setFirstResult(int offset) {
        this.offset = offset;
        return this;
    }

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
    @Override
    public CandlepinQuery<T> setMaxResults(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Adds the specified ordering when executing this query.
     *
     * @param order
     *  The ordering to apply when executing this query
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery<T> addOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order is null");
        }

        this.criteria.addOrder(order);
        return this;
    }

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
    @Override
    @SuppressWarnings("unchecked")
    public List<T> list() {
        Criteria executable = this.getExecutableCriteria();
        List<T> list = (List<T>) executable.list();

        return list != null ? list : Collections.<T>emptyList();
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
    @Override
    public int scroll(ResultProcessor<T> processor) {
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
    @Override
    public int scroll(int column, ResultProcessor<T> processor) {
        return this.scroll(column, false, processor);
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
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public int scroll(int column, boolean evict, ResultProcessor<T> processor) {
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
                while (cont && cursor.next()) {
                    T result = (T) cursor.get(column);

                    cont = processor.process(result);
                    this.session.evict(result);

                    ++count;
                }
            }
            else {
                while (cont && cursor.next()) {
                    cont = processor.process((T) cursor.get(column));
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
    @Override
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
     * Executes this query and iterates over the first column of the results. Other columns in
     * each row are silently discarded.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the first column of the results
     */
    @Override
    public ResultIterator<T> iterate() {
        return this.iterate(0, false);
    }

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
    @Override
    public ResultIterator<T> iterator() {
        return this.iterate(0, false);
    }

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
    @Override
    public ResultIterator<T> iterate(int column) {
        return this.iterate(column, false);
    }

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
    @Override
    public ResultIterator<T> iterate(int column, boolean evict) {
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
     * Executes this query and iterates over the rows of results.
     * <p></p>
     * WARNING: This method must be called from within a transaction, and the iterator must
     * remain within the bounds of that transaction.
     *
     * @return
     *  an iterator over the rows in the query results
     */
    @Override
    public ResultIterator<Object[]> iterateByRow() {
        Criteria executable = this.getExecutableCriteria();

        ScrollableResults cursor = executable.scroll(ScrollMode.FORWARD_ONLY);
        return new RowResultIterator(cursor);
    }

    /**
     * Executes this query and returns a single, unique entity. If no entities could be found,
     * this method returns null. If more than one entity is found, a runtime exception will be
     * thrown.
     *
     * @return
     *  a single entity, or null if no entities were found
     */
    @Override
    @SuppressWarnings("unchecked")
    public T uniqueResult() {
        Criteria executable = this.getExecutableCriteria();
        return (T) executable.uniqueResult();
    }

    /**
     * Executes this query and fetches the number of results. This operates by applying the
     * rowCount projection to the query and executing it. Depending on the query itself, and
     * whether or not it has existing projections, this may affect the results fetched.
     *
     * @return
     *  the number of results found by executing this query
     */
    @Override
    @SuppressWarnings("unchecked")
    public int getRowCount() {
        CriteriaImpl executable = (CriteriaImpl) this.getExecutableCriteria();
        Projection projection = executable.getProjection();

        if (projection != null && projection.isGrouped()) {
            // We have a projection that alters the grouping of the query. We need to rebuild the
            // projection such that it gets our row count and properly applies the group by
            // statement.
            // The logic for this block is largely derived from this Stack Overflow posting:
            // http://stackoverflow.com/
            //     questions/32498229/hibernate-row-count-on-criteria-with-already-set-projection
            //
            // A safer alternative may be to generate a query that uses the given criteria as a
            // subquery (SELECT count(*) FROM (<criteria SQL>)), but is probably less performant
            // than this hack.
            CriteriaQueryTranslator translator = new CriteriaQueryTranslator(
                (SessionFactoryImplementor) this.session.getSessionFactory(),
                executable,
                executable.getEntityOrClassName(),
                CriteriaQueryTranslator.ROOT_SQL_ALIAS
            );

            projection = Projections.projectionList()
                .add(Projections.rowCount())
                .add(Projections.sqlGroupProjection(
                    "count(count(1))",
                    translator.getGroupBy(),
                    new String[] {},
                    new Type[] {}
                ));
        }
        else {
            projection = Projections.rowCount();
        }

        executable.setProjection(projection);
        return ((Long) executable.uniqueResult()).intValue();
    }
}
