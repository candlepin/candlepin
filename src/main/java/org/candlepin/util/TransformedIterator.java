/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.util;

import java.util.Iterator;



/**
 * The TransformedIterator decorator class applies element transformation to an existing Iterator
 * instance.
 *
 * @param <I>
 *  The element type returned by the base Iterator this instance is transforming
 *
 * @param <O>
 *  The element type to be returned by this iterators's output methods
 */
public class TransformedIterator<I, O> implements Iterator<O> {
    private Iterator<I> iterator;
    private ElementTransformer<I, O> transformer;

    /**
     * Creates a new TransformedIterator from the given iterator and transformer instances.
     *
     * @param iterator
     *  The iterator to transform
     *
     * @param transformer
     *  The transformer to apply to the elements of the given iterator
     *
     * @throws IllegalArgumentException
     *  if either the iterator or transformer are null
     */
    public TransformedIterator(Iterator<I> iterator, ElementTransformer<I, O> transformer) {
        if (iterator == null) {
            throw new IllegalArgumentException("iterator is null");
        }

        if (transformer == null) {
            throw new IllegalArgumentException("transformer is null");
        }

        this.iterator = iterator;
        this.transformer = transformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public O next() {
        return this.transformer.transform(this.iterator.next());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove() {
        this.iterator.remove();
    }
}
