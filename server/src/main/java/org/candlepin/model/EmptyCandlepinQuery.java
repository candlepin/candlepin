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

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import javax.persistence.LockModeType;



/**
 * The EmptyCandlepinQuery class represents a CandlepinQuery that is not backed by an actual
 * criteria, and, thus, has no values. This can be used in cases where input validation or state
 * checks determine the query can't be run, but still provide consistent output to the caller.
 *
 * @param <T>
 *  The entity type to be returned by this criteria's result output methods
 */
public class EmptyCandlepinQuery<T> implements CandlepinQuery<T> {

    /**
     * Empty version of the ResultIterator class
     */
    private static final ResultIterator EMPTY_RESULT_ITERATOR = new ResultIterator() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException();
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

    /**
     * Creates a new EmptyCandlepinQuery instance.
     */
    public EmptyCandlepinQuery() {
        // Intentionally left empty
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param session
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery useSession(Session session) {
        return this;
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param offset
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery<T> setFirstResult(int offset) {
        return this;
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param limit
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery<T> setMaxResults(int limit) {
        return this;
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param order
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery<T> addOrder(Order order) {
        return this;
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param lockMode
     *
     * @return
     *  this query instance
     */
    @Override
    public CandlepinQuery<T> setLockMode(LockModeType lockMode) {
        return this;
    }

    /**
     * Returns a reference to this CandlepinQuery instance.
     *
     * @param transformer
     *
     * @return
     *  this query instance
     */
    @Override
    public <O> CandlepinQuery<O> transform(ElementTransformer<T, O> transformer) {
        return (CandlepinQuery<O>) this;
    }

    /**
     * Returns an empty list.
     *
     * @return
     *  an empty list
     */
    @Override
    public List<T> list() {
        return Collections.<T>emptyList();
    }

    /**
     * Immediately returns zero without invoking any of the given processor's methods.
     *
     * @return
     *  zero
     */
    @Override
    public int forEach(ResultProcessor<T> processor) {
        return 0;
    }

    /**
     * Immediately returns zero without invoking any of the given processor's methods.
     *
     * @param column
     *
     * @return
     *  zero
     */
    @Override
    public int forEach(int column, ResultProcessor<T> processor) {
        return 0;
    }

    /**
     * Immediately returns zero without invoking any of the given processor's methods.
     *
     * @param column
     * @param evict
     *
     * @return
     *  zero
     */
    @Override
    public int forEach(int column, boolean evict, ResultProcessor<T> processor) {
        return 0;
    }

    /**
     * Immediately returns zero without invoking any of the given processor's methods.
     *
     * @return
     *  zero
     */
    @Override
    public int forEachRow(ResultProcessor<Object[]> processor) {
        return 0;
    }

    /**
     * Always returns an iterator with no elements.
     *
     * @return
     *  a ResultIterator containing no elements
     */
    @Override
    public ResultIterator<T> iterate() {
        return (ResultIterator<T>) EMPTY_RESULT_ITERATOR;
    }

    /**
     * Always returns an iterator with no elements.
     *
     * @return
     *  a ResultIterator containing no elements
     */
    @Override
    public ResultIterator<T> iterator() {
        return (ResultIterator<T>) EMPTY_RESULT_ITERATOR;
    }

    /**
     * Always returns an iterator with no elements.
     *
     * @param column
     *
     * @return
     *  a ResultIterator containing no elements
     */
    public ResultIterator<T> iterate(int column) {
        return (ResultIterator<T>) EMPTY_RESULT_ITERATOR;
    }

    /**
     * Always returns an iterator with no elements.
     *
     * @param column
     * @param evict
     *
     * @return
     *  a ResultIterator containing no elements
     */
    @Override
    public ResultIterator<T> iterate(int column, boolean evict) {
        return (ResultIterator<T>) EMPTY_RESULT_ITERATOR;
    }

    /**
     * Always returns an iterator with no elements.
     *
     * @return
     *  A ResultIterator containing no elements
     */
    @Override
    public ResultIterator<Object[]> iterateByRow() {
        return (ResultIterator<Object[]>) EMPTY_RESULT_ITERATOR;
    }

    /**
     * Always returns null.
     *
     * @return
     *  null
     */
    @Override
    public T uniqueResult() {
        return null;
    }

    /**
     * Always returns zero.
     *
     * @return
     *  zero
     */
    @Override
    public int getRowCount() {
        return 0;
    }
}
