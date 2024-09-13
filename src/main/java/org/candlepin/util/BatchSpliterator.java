/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;



/**
 * The BatchSpliterator provides lazy batch or buffered operations to an existing spliterator, or any
 * iterative processing that use spliterators. This spliterator will, on demand, batch as many elements
 * as it can into a list, passing as the next element returned from tryAdvance.
 * <p></p>
 * Note that while this class permits setting a batch size of one to create batches of single elements,
 * doing so will almost always be less efficient than using the map function provided by the streaming
 * API to build such lists directly.
 * <p></p>
 * This class also provides multiple ways of constructing or wrapping existing stream-like objects to
 * facilitate wrapping such operations.
 *
 * @param <T>
 *  the element type of this spliterator
 */
public class BatchSpliterator<T> implements Spliterator<List<T>> {

    /** The maximum size of the array we will preallocate before letting normal array resizing take place */
    private static final int MAX_ARRAY_PREALLOC_SIZE = 10000;

    private final Spliterator<T> source;
    private final int batchSize;

    /**
     * Creates a new BatchSpliterator using the given spliterator as its data source, and the provided
     * batch size to determine how large each block of elements should be.
     *
     * @param source
     *  the source spliterator to use as a data source
     *
     * @param batchSize
     *  the size of each batch of elements; must be a positive integer
     *
     * @throws IllegalArgumentException
     *  if the source spliterator is null, or batch size is not a positive integer
     */
    public BatchSpliterator(Spliterator<T> source, int batchSize) {
        if (source == null) {
            throw new IllegalArgumentException("source spliterator is null");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("batch size must be a positive integer");
        }

        this.source = source;
        this.batchSize = batchSize;
    }

    /**
     * Converts the given stream to a spliterator, and then wraps it in a BatchSpliterator for batch
     * processing. This method will attempt to retain as many properties of the source stream as possible:
     * if the source stream is ordered, parallel, or null, the output stream will also be ordered, parallel,
     * and/or null respectively.
     *
     * @param source
     *  the source stream to wrap
     *
     * @param batchSize
     *  the size of each batch of elements; must be a positive integer
     *
     * @throws IllegalArgumentException
     *  if batch size is not a positive integer
     *
     * @return
     *  a stream of lists of elements contained in the source stream
     */
    public static <T> Stream<List<T>> batchStream(Stream<T> source, int batchSize) {
        // TODO: It may be apt to move this to a "StreamUtils" type of class if we end up creating a
        // bunch of stream-oriented wrapping or utility functions.

        if (source == null) {
            return null;
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("batch size must be a positive integer");
        }

        BatchSpliterator<T> spliterator = new BatchSpliterator<>(source.spliterator(), batchSize);
        return StreamSupport.stream(spliterator, source.isParallel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int characteristics() {
        return this.source.characteristics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long estimateSize() {
        long size = this.source.estimateSize();

        // Handle the "unknown size" sentinel value, or any invalid sizes our source may return
        if (size >= Long.MAX_VALUE || size < 1) {
            return size;
        }

        return (long) Math.ceil((double) size / (double) this.batchSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BatchSpliterator<T> trySplit() {
        Spliterator<T> split = this.source.trySplit();
        return split != null ? new BatchSpliterator<>(split, this.batchSize) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryAdvance(Consumer<? super List<T>> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }

        List<T> buffer = new ArrayList<>(Math.min(this.batchSize, MAX_ARRAY_PREALLOC_SIZE));
        for (long i = 0; i < this.batchSize && this.source.tryAdvance(buffer::add); ++i) {
            // intentionally left empty
        }

        if (buffer.isEmpty()) {
            return false;
        }

        consumer.accept(buffer);
        return true;
    }

}
