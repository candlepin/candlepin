/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;



/**
 * Test suite for the LongHashCodeBuilder class
 */
public class LongHashCodeBuilderTest {

    // A safe initial value that can be used in tests where we aren't testing the initial value
    private static final long SAFE_INITIAL_VALUE = 7L;

    // A safe multiplier that can be used in tests where aren't testing the multiplier
    private static final long SAFE_MULTIPLIER = 37L;


    @Test
    public void testDefaultInitialValue() {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();

        // With no constructor arguments, the initial value should be the default initial value
        assertEquals(LongHashCodeBuilder.DEFAULT_INITIAL_VALUE, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(longs = { Long.MIN_VALUE + 1, -15001L, 1, 15001L, Long.MAX_VALUE })
    public void testProvidedInitialValue(long initialValue) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder(initialValue, SAFE_MULTIPLIER);

        assertEquals(initialValue, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(longs = { Long.MIN_VALUE, -2, 0, 2, Long.MAX_VALUE - 1 })
    public void testProvidedInitialValueMustBeOdd(long initialValue) {
        assertThrows(IllegalArgumentException.class,
            () -> new LongHashCodeBuilder(initialValue, SAFE_MULTIPLIER));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(booleans = { true, false })
    public void testAppendBooleanChangesResult(boolean input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(booleans = { true, false })
    public void testRepeatedAppendBooleanChangesResult(boolean input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(booleans = { true, false })
    public void testAppendBooleanIsDeterministic(boolean input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendBooleanArrayChangesResult() {
        boolean[] input = new boolean[] { true, false };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendBooleanArrayChangesResult() {
        boolean[] input = new boolean[] { true, false };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendBooleanArrayIsDeterministic() {
        boolean[] input = new boolean[] { true, false };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(bytes = { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE })
    public void testAppendByteChangesResult(byte input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(bytes = { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE })
    public void testRepeatedAppendByteChangesResult(byte input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(bytes = { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE })
    public void testAppendByteIsDeterministic(byte input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendByteArrayChangesResult() {
        byte[] input = new byte[] { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendByteArrayChangesResult() {
        byte[] input = new byte[] { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendByteArrayIsDeterministic() {
        byte[] input = new byte[] { Byte.MIN_VALUE, -0x10, 0, 0x10, Byte.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }


    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(chars = { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE })
    public void testAppendCharChangesResult(char input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(chars = { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE })
    public void testRepeatedAppendCharChangesResult(char input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(chars = { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE })
    public void testAppendCharIsDeterministic(char input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendCharArrayChangesResult() {
        char[] input = new char[] { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendCharArrayChangesResult() {
        char[] input = new char[] { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendCharArrayIsDeterministic() {
        char[] input = new char[] { Character.MIN_VALUE, 'a', 0, 'z', Character.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(shorts = { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE })
    public void testAppendShortChangesResult(short input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(shorts = { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE })
    public void testRepeatedAppendShortChangesResult(short input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(shorts = { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE })
    public void testAppendShortIsDeterministic(short input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendShortArrayChangesResult() {
        short[] input = new short[] { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendShortArrayChangesResult() {
        short[] input = new short[] { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendShortArrayIsDeterministic() {
        short[] input = new short[] { Short.MIN_VALUE, -0x10, 0, 0x10, Short.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(ints = { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE })
    public void testAppendIntChangesResult(int input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(ints = { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE })
    public void testRepeatedAppendIntChangesResult(int input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(ints = { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE })
    public void testAppendIntIsDeterministic(int input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendIntArrayChangesResult() {
        int[] input = new int[] { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendIntArrayChangesResult() {
        int[] input = new int[] { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendIntArrayIsDeterministic() {
        int[] input = new int[] { Integer.MIN_VALUE, -0x10, 0, 0x10, Integer.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(longs = { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE })
    public void testAppendLongChangesResult(long input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(longs = { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE })
    public void testRepeatedAppendLongChangesResult(long input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(longs = { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE })
    public void testAppendLongIsDeterministic(long input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendLongArrayChangesResult() {
        long[] input = new long[] { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendLongArrayChangesResult() {
        long[] input = new long[] { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendLongArrayIsDeterministic() {
        long[] input = new long[] { Long.MIN_VALUE, -0x10, 0, 0x10, Long.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(floats = { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE })
    public void testAppendFloatChangesResult(float input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(floats = { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE })
    public void testRepeatedAppendFloatChangesResult(float input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(floats = { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE })
    public void testAppendFloatIsDeterministic(float input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendFloatArrayChangesResult() {
        float[] input = new float[] { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendFloatArrayChangesResult() {
        float[] input = new float[] { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendFloatArrayIsDeterministic() {
        float[] input = new float[] { Float.MIN_VALUE, -10.20F, 0, 10.20F, Float.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(doubles = { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE })
    public void testAppendDoubleChangesResult(double input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(doubles = { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE })
    public void testRepeatedAppendDoubleChangesResult(double input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @ValueSource(doubles = { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE })
    public void testAppendDoubleIsDeterministic(double input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendDoubleArrayChangesResult() {
        double[] input = new double[] { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendDoubleArrayChangesResult() {
        double[] input = new double[] { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE };

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendDoubleArrayIsDeterministic() {
        double[] input = new double[] { Double.MIN_VALUE, -10.20, 0, 10.20, Double.MAX_VALUE };

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    public static Stream<Arguments> buildTestObjects() {
        Object myObject = new Object() {
            @Override
            @SuppressWarnings("EqualsHashCode")
            public int hashCode() {
                return 42;
            }
        };

        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(new Object()),
            Arguments.of("test_string"),
            Arguments.of(myObject)
        );
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buildTestObjects")
    public void testAppendObjectChangesResult(Object input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buildTestObjects")
    public void testRepeatedAppendObjectChangesResult(Object input) {
        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @ParameterizedTest(name = "{displayName} [{index}]: {0}")
    @MethodSource("buildTestObjects")
    public void testAppendObjectIsDeterministic(Object input) {
        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

    @Test
    public void testAppendObjectArrayChangesResult() {
        Object[] input = buildTestObjects().toArray();

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long initialHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(initialHash, builder.toHashCode());
    }

    @Test
    public void testRepeatedAppendObjectArrayChangesResult() {
        Object[] input = buildTestObjects().toArray();

        LongHashCodeBuilder builder = new LongHashCodeBuilder();
        long previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
        previousHash = builder.toHashCode();

        builder.append(input);

        assertNotEquals(previousHash, builder.toHashCode());
    }

    @Test
    public void testAppendObjectArrayIsDeterministic() {
        Object[] input = buildTestObjects().toArray();

        LongHashCodeBuilder builder1 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);
        LongHashCodeBuilder builder2 = new LongHashCodeBuilder(SAFE_INITIAL_VALUE, SAFE_MULTIPLIER);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        long initialHash = builder1.toHashCode();

        builder1.append(input);
        builder2.append(input);

        assertEquals(builder1.toHashCode(), builder2.toHashCode());
        assertNotEquals(initialHash, builder1.toHashCode());
        assertNotEquals(initialHash, builder2.toHashCode());
    }

}
