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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;



/**
 * Test suite for the BatchSpliterator class
 */
public class BatchSpliteratorTest {

    @Test
    public void testStandardConstruction() {
        Spliterator source = Stream.empty()
            .spliterator();

        BatchSpliterator spliterator = new BatchSpliterator(source, 15);
    }

    @Test
    public void testSourceSpliteratorRequired() {
        assertThrows(IllegalArgumentException.class, () -> new BatchSpliterator(null, 15));
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -50, -1, 0 })
    public void testBatchSizeMustBePositiveInteger(int batchSize) {
        Spliterator source = Stream.empty()
            .spliterator();

        assertThrows(IllegalArgumentException.class, () -> new BatchSpliterator(source, batchSize));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 10, 500, 100000, Integer.MAX_VALUE })
    public void testBatchStream(int batchSize) {
        List<Integer> data = Stream.iterate(0, i -> i + 1)
            .limit(250)
            .toList();

        int expectedBatches = (int) Math.ceil((double) data.size() / (double) batchSize);

        Stream<List<Integer>> output = BatchSpliterator.batchStream(data.stream(), batchSize);
        assertNotNull(output);

        List<List<Integer>> batches = output.toList();
        assertThat(batches)
            .isNotNull()
            .hasSize(expectedBatches);

        Iterator<Integer> expected = data.iterator();
        for (List<Integer> batch : batches) {
            assertThat(batch)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(batchSize);

            for (int value : batch) {
                assertTrue(expected.hasNext());
                assertEquals(expected.next(), value);
            }
        }

        assertFalse(expected.hasNext());
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -50, -1, 0 })
    public void testBatchStreamBatchSizeMustBePositiveInteger(int batchSize) {
        Stream<Integer> source = Stream.empty();

        assertThrows(IllegalArgumentException.class, () -> BatchSpliterator.batchStream(source, batchSize));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 10, 500, 100000, Integer.MAX_VALUE })
    public void testBatchStreamWithEmptyStream(int batchSize) {
        Stream<Integer> source = Stream.of();

        Stream<List<Integer>> output = BatchSpliterator.batchStream(source, batchSize);
        assertNotNull(output);

        List<List<Integer>> batches = output.toList();
        assertThat(batches)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, Integer.MAX_VALUE })
    public void testBatchStreamPassesThroughNullStreams(int batchSize) {
        Stream<List<Object>> output = BatchSpliterator.batchStream(null, batchSize);
        assertNull(output);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 100000, Integer.MAX_VALUE })
    public void testBatchStreamRetainsParallelStreamFlag(int batchSize) {
        Stream<Integer> source = Stream.of(1, 2, 3, 4, 5)
            .parallel();

        assertTrue(source.isParallel());

        Stream<List<Integer>> output = BatchSpliterator.batchStream(source, batchSize);

        assertEquals(source.isParallel(), output.isParallel());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 100000, Integer.MAX_VALUE })
    public void testBatchStreamRetainsSequentialStreamFlag(int batchSize) {
        Stream<Integer> source = Stream.of(1, 2, 3, 4, 5)
            .sequential();

        assertFalse(source.isParallel());

        Stream<List<Integer>> output = BatchSpliterator.batchStream(source, batchSize);

        assertEquals(source.isParallel(), output.isParallel());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 100000, Integer.MAX_VALUE })
    public void testSpliteratorRetainsSourceCharacteristics(int batchSize) {
        Spliterator<Integer> source = Stream.of(1, 2, 3, 4, 5)
            .spliterator();

        BatchSpliterator<Integer> batched = new BatchSpliterator(source, batchSize);

        assertEquals(source.characteristics(), batched.characteristics());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 500, 10000, Integer.MAX_VALUE })
    public void testEstimateSizeWithFiniteSource(int batchSize) {
        List<Integer> data = Stream.iterate(0, i -> i + 1)
            .limit(250)
            .toList();

        long expectedSize = (long) Math.ceil((double) data.size() / (double) batchSize);

        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(data.spliterator(), batchSize);
        assertEquals(expectedSize, spliterator.estimateSize());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 500, 10000, Integer.MAX_VALUE })
    public void testEstimateSizeWithEmptySource(int batchSize) {
        List<Integer> data = List.of();

        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(data.spliterator(), batchSize);
        assertEquals(0, spliterator.estimateSize());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 500, 10000, Integer.MAX_VALUE })
    public void testEstimateSizeWithInfiniteSource(int batchSize) {
        Spliterator<Integer> source = Stream.iterate(0, i -> i + 1)
            .spliterator();

        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(source, batchSize);
        assertEquals(Long.MAX_VALUE, spliterator.estimateSize());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 500, 10000, Integer.MAX_VALUE })
    public void testTrySplitWithSplittableSpliterator(int batchSize) {
        List<Integer> data = Stream.iterate(0, i -> i + 1)
            .limit(250)
            .toList();

        BatchSpliterator<Integer> base = new BatchSpliterator<>(data.spliterator(), batchSize);
        BatchSpliterator<Integer> split = base.trySplit();
        assertNotNull(split);

        // verify our splits do not contain overlap
        List<Integer> baseValues = StreamSupport.stream(base, false)
            .flatMap(List::stream)
            .toList();

        List<Integer> splitValues = StreamSupport.stream(split, false)
            .flatMap(List::stream)
            .toList();

        assertThat(baseValues)
            .isNotEmpty()
            .doesNotContainAnyElementsOf(splitValues);

        assertThat(splitValues)
            .isNotEmpty()
            .doesNotContainAnyElementsOf(baseValues);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 500, 10000, Integer.MAX_VALUE })
    public void testTrySplitWithUnsplittableSpliterator(int batchSize) {
        List<Integer> data = Stream.iterate(0, i -> i + 1)
            .limit(250)
            .toList();

        Spliterator<Integer> source = spy(data.spliterator());
        doReturn(null).when(source).trySplit();

        BatchSpliterator<Integer> base = new BatchSpliterator<>(source, batchSize);
        BatchSpliterator<Integer> split = base.trySplit();
        assertNull(split);

        // even though we couldn't split, we should still have the same elements available from the base
        // spliterator
        List<Integer> baseValues = StreamSupport.stream(base, false)
            .flatMap(List::stream)
            .toList();

        assertThat(baseValues)
            .isNotEmpty()
            .containsExactlyElementsOf(data);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 10, 500, 100000, Integer.MAX_VALUE })
    public void testTryAdvance(int batchSize) {
        List<Integer> data = Stream.iterate(0, i -> i + 1)
            .limit(250)
            .toList();

        int expectedBatches = (int) Math.ceil((double) data.size() / (double) batchSize);

        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(data.spliterator(), batchSize);

        long estimateSize = spliterator.estimateSize();
        assertEquals(expectedBatches, estimateSize);

        Iterator<Integer> expected = data.iterator();
        Consumer<List<Integer>> consumer = batch -> {
            assertThat(batch)
                .isNotNull()
                .hasSizeLessThanOrEqualTo(batchSize);

            for (int value : batch) {
                assertTrue(expected.hasNext());
                assertEquals(expected.next(), value);
            }
        };

        while (spliterator.tryAdvance(consumer)) {
            // intentionally left empty
        }

        assertFalse(expected.hasNext());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 10, 500, 100000, Integer.MAX_VALUE })
    public void testTryAdvanceOnEmptySpliterator(int batchSize) {
        Spliterator<Integer> source = List.<Integer>of().spliterator();

        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(source, batchSize);

        Consumer<List<Integer>> consumer = batch -> fail("consumer should not be invoked");
        assertFalse(spliterator.tryAdvance(consumer));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 5, 10, 500, 100000, Integer.MAX_VALUE })
    public void testTryAdvanceFailsWithNullConsumer(int batchSize) {
        Spliterator<Integer> source = List.<Integer>of().spliterator();
        BatchSpliterator<Integer> spliterator = new BatchSpliterator<>(source, batchSize);

        assertThrows(IllegalArgumentException.class, () -> spliterator.tryAdvance(null));
    }

}
