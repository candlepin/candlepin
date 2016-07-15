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

import java.util.List;
import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * The DetachedCandlepinQuery class represents a detached criteria and provides fluent-style methodsfor
 *  configuring how the criteria is to be executed and how the result should be processed.
 *
 * @deprecated
 *  This class is intended to be a temporary workaround for a major shortcoming in Hibernate which
 *  disregards the DISTINCT specification when working with entities and cursors. If a proper,
 *  query-level workaround is found for every query using this method, it will be removed.
 *
 * @param <T>
 *  The entity type to be returned by this criteria's result output methods
 */
@Deprecated
public class DistinctCandlepinQuery<T> extends DetachedCandlepinQuery<T> {


    /**
     * Creates a new DistinctCandlepinQuery instance using the specified criteria and session.
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
    public DistinctCandlepinQuery(Session session, DetachedCriteria criteria) {
        super(session, criteria);
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

        // Impl note:
        // Hibernate has a broken cursor implementation which disregards the DISTINCT specification
        // when a query returns entities. Because of this issue, we have to do this the naive way
        // and throw everything in memory first, since we don't have a reliable way of determining
        // an identifying property of the values we're to iterate.
        Iterator<T> elements = this.list().iterator();

        int count = 0;
        boolean cont = true;

        if (evict) {
            while (cont && elements.hasNext()) {
                T result = (T) elements.next();

                cont = processor.process(result);
                this.session.evict(result);

                ++count;
            }
        }
        else {
            while (cont && elements.hasNext()) {
                cont = processor.process((T) elements.next());
                ++count;
            }
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

        // Impl note:
        // Hibernate has a broken cursor implementation which disregards the DISTINCT specification
        // when a query returns entities. Because of this issue, we have to do this the naive way
        // and throw everything in memory first, since we don't have a reliable way of determining
        // an identifying property of the values we're to iterate.
        Iterator<T> elements = this.list().iterator();

        int count = 0;
        boolean cont = true;

        while (cont && elements.hasNext()) {
            T element = elements.next();

            cont = processor.process(
                (element instanceof Object[] ? (Object[]) element : new Object[] { element })
            );

            ++count;
        }

        return count;
    }

    /**
     * Executes this criteria and iterates over the first column of the results. Other columns in
     * each row are silently discarded.
     *
     * @return
     *  an iterator over the first column of the results
     */
    @Override
    public ResultIterator<T> iterate() {
        return this.iterate(0, false);
    }

    /**
     * Executes this criteria and iterates over the first column of the results. Other columns in
     * each row are silently discarded. This method is functionally identical to iterate, and is
     * only provided for compatibility with the foreach construct.
     *
     * @return
     *  an iterator over the first column of the results
     */
    @Override
    public ResultIterator<T> iterator() {
        return this.iterate(0, false);
    }

    /**
     * Executes this criteria and iterates over the specified column of the results. Other columns
     * in each row are silently discarded.
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
     * Executes this criteria and iterates over the specified column of the results, optionally
     * automatically evicting returned entities after they are processed. Other columns in each row
     * are silently discarded.
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

        final Iterator<T> iterator = this.list().iterator();
        final Session session = this.session;
        final boolean autoEvict = evict;

        return new ResultIterator<T>() {
            private T toEvict;

            @Override
            public boolean hasNext() {
                if (this.toEvict != null) {
                    session.evict(this.toEvict);
                    this.toEvict = null;
                }

                return iterator.hasNext();
            }

            @Override
            public T next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }

                T element = iterator.next();

                if (autoEvict) {
                    this.toEvict = element;
                }

                return element;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                // Intentionally left empty
            }
        };
    }

    /**
     * Executes this criteria and iterates over the rows of results.
     *
     * @return
     *  an iterator over the rows in the query results
     */
    @Override
    public ResultIterator<Object[]> iterateByRow() {
        final Iterator iterator = this.list().iterator();
        final Session session = this.session;

        return new ResultIterator<Object[]>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object[] next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }

                Object element = iterator.next();
                return (element instanceof Object[] ? (Object[]) element : new Object[] { element });
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                // Intentionally left empty
            }
        };
    }

}
