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
import java.util.LinkedList;
import java.util.List;

import javax.persistence.LockModeType;



/**
 * The TransformedCandlepinQuery class is a decorator for standard CandlepinQuery implementations,
 * providing element transformation from one type to another using an ElementTransformer instance.
 * <p></p>
 * Typically, this class does not need to be instantiated directly, and can be obtained by calling
 * the <tt>.transform</tt> method on the base CandlepinQuery instance.
 *
 * @param <I>
 *  The element type returned by the base CandlepinQuery this instance is transforming
 *
 * @param <O>
 *  The element type to be returned by this query's result output methods
 */
public class TransformedCandlepinQuery<I, O> implements CandlepinQuery<O> {

    /**
     * The TransformedResultIterator class applies element transformation to an existing
     * ResultIterator instance.
     *
     * @param <I>
     *  The element type returned by the base ResultIterator this instance is transforming
     *
     * @param <O>
     *  The element type to be returned by this iterators's output methods
     */
    private static class TransformedResultIterator<I, O> implements ResultIterator<O> {
        private ResultIterator<I> iterator;
        private ElementTransformer<I, O> transformer;

        public TransformedResultIterator(ResultIterator<I> iterator, ElementTransformer<I, O> transformer) {
            if (iterator == null) {
                throw new IllegalArgumentException("iterator is null");
            }

            if (transformer == null) {
                throw new IllegalArgumentException("transformer is null");
            }

            this.iterator = iterator;
            this.transformer = transformer;
        }

        @Override
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        public O next() {
            return this.transformer.transform(this.iterator.next());
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }

        @Override
        public void close() {
            this.iterator.close();
        }
    }

    private CandlepinQuery<I> query;
    private ElementTransformer<I, O> transformer;

    /**
     * Creates a new TransformedCandlepinQuery instance from the given query and element
     * transformer.
     *
     * @param query
     *  The CandlepinQuery to be transformed by the given element transformer
     *
     * @param transformer
     *  The ElementTransformer to apply to the results of the provided query
     *
     * @throws IllegalArgumentException
     *  if either the query or transformer are null
     */
    public TransformedCandlepinQuery(CandlepinQuery<I> query, ElementTransformer<I, O> transformer) {
        if (query == null) {
            throw new IllegalArgumentException("query is null");
        }

        if (transformer == null) {
            throw new IllegalArgumentException("transformer is null");
        }

        this.query = query;
        this.transformer = transformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<O> useSession(Session session) {
        this.query.useSession(session);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<O> setFirstResult(int offset) {
        this.query.setFirstResult(offset);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<O> setMaxResults(int limit) {
        this.query.setMaxResults(limit);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<O> addOrder(Order order) {
        this.query.addOrder(order);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CandlepinQuery<O> setLockMode(LockModeType lockMode) {
        this.query.setLockMode(lockMode);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> CandlepinQuery<T> transform(ElementTransformer<O, T> transformer) {
        return new TransformedCandlepinQuery(this, transformer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<O> list() {
        List<O> output = new LinkedList<>();
        ResultIterator<O> iterator = this.iterate(); // Should we auto-evict here?

        try {
            while (iterator.hasNext()) {
                output.add(iterator.next());
            }
        }
        finally {
            iterator.close();
        }

        return output;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEach(ResultProcessor<O> processor) {
        return this.forEach(0, false, processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEach(int column, ResultProcessor<O> processor) {
        return this.forEach(column, false, processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEach(int column, boolean evict, ResultProcessor<O> processor) {
        if (processor == null) {
            throw new IllegalArgumentException("processor is null");
        }

        final ResultProcessor<O> wrapped = processor;
        final ElementTransformer<I, O> transformer = this.transformer;

        return this.query.forEach(column, evict, new ResultProcessor<I>() {
            @Override
            public boolean process(I element) {
                return wrapped.process(transformer.transform(element));
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int forEachRow(ResultProcessor<Object[]> processor) {
        return this.query.forEachRow(processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<O> iterate() {
        return this.iterate(0, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<O> iterator() {
        return this.iterate(0, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<O> iterate(int column) {
        return this.iterate(column, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<O> iterate(int column, boolean evict) {
        ResultIterator<I> iterator = this.query.iterate(column, evict);
        return new TransformedResultIterator<>(iterator, this.transformer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResultIterator<Object[]> iterateByRow() {
        return this.query.iterateByRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public O uniqueResult() {
        return this.transformer.transform(this.query.uniqueResult());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRowCount() {
        return this.query.getRowCount();
    }

}
