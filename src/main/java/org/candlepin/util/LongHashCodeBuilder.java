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



/**
 * A version/hash builder based on the design and principles of the Apache HashCodeBuilder (which
 * itself is based on principles outlined in Joshua Bloch's Effective Java). However, unlike the
 * HashCodeBuilder, this class provides no reflection or object-introspection functionality.
 *
 * The functionality is very similar, if not identical to the aforementioned HashCodeBuilder, except
 * the output is a 64-bit long instead of a 32-bit integer. This should, in theory, cut down on the
 * number of collisions.
 */
public class LongHashCodeBuilder {

    public static final long DEFAULT_INITIAL_VALUE = 97L;
    public static final long DEFAULT_MULTIPLIER = 757L;

    private final long multiplier;
    private long accumulator = 0;

    /**
     * Creates a new LongHashCodeBuilder with the default initial value and multiplier.
     */
    public LongHashCodeBuilder() {
        this(DEFAULT_INITIAL_VALUE, DEFAULT_MULTIPLIER);
    }

    /**
     * Creates a new LongHashCodeBuilder with the specified initial value and multiplier; both of
     * which must be odd numbers, and, preferably, prime numbers. While not strictly necessary, the
     * pair should also be unique on a per-class basis.
     *
     * @param initialValue
     *  the initial value for the entity version; must be an odd number
     *
     * @param multiplier
     *  the per-field multiplier; must be an odd number
     *
     * @throws IllegalArgumentException
     *  if either initialValue or multiplier are even numbers
     */
    public LongHashCodeBuilder(long initialValue, long multiplier) {
        if (initialValue % 2 == 0) {
            throw new IllegalArgumentException("initialValue is an even number");
        }

        if (multiplier % 2 == 0) {
            throw new IllegalArgumentException("multiplier is an even number");
        }

        this.accumulator = initialValue;
        this.multiplier = multiplier;
    }

    /**
     * Appends a boolean value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(boolean value) {
        this.accumulator = this.accumulator * this.multiplier + (value ? 0 : 1);
        return this;
    }

    /**
     * Appends an array of boolean values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(boolean[] array) {
        if (array != null) {
            for (boolean element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a byte value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(byte value) {
        this.accumulator = this.accumulator * this.multiplier + value;
        return this;
    }

    /**
     * Appends an array of byte values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(byte[] array) {
        if (array != null) {
            for (byte element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a char value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(char value) {
        this.accumulator = this.accumulator * this.multiplier + value;
        return this;
    }

    /**
     * Appends an array of char values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(char[] array) {
        if (array != null) {
            for (char element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a short value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(short value) {
        this.accumulator = this.accumulator * this.multiplier + value;
        return this;
    }

    /**
     * Appends an array of short values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(short[] array) {
        if (array != null) {
            for (short element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends an integer value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(int value) {
        this.accumulator = this.accumulator * this.multiplier + value;
        return this;
    }

    /**
     * Appends an array of integer values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(int[] array) {
        if (array != null) {
            for (int element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a long value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(long value) {
        this.accumulator = this.accumulator * this.multiplier + value;
        return this;
    }

    /**
     * Appends an array of long values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(long[] array) {
        if (array != null) {
            for (long element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a float value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(float value) {
        return this.append(Float.floatToIntBits(value));
    }

    /**
     * Appends an array of float values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(float[] array) {
        if (array != null) {
            for (float element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends a double value to this hash code builder.
     *
     * @param value
     *  the value to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(double value) {
        return this.append(Double.doubleToLongBits(value));
    }

    /**
     * Appends an array of double values to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(double[] array) {
        if (array != null) {
            for (double element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Appends an object to this hash code builder.
     *
     * @param value
     *  the object to add to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(Object value) {
        if (value == null) {
            this.accumulator *= this.multiplier;
        }
        else if (value.getClass().isArray()) {
            this.processObjectArray((Object[]) value);
        }
        else {
            this.accumulator = this.accumulator * this.multiplier + value.hashCode();
        }

        return this;
    }

    /**
     * Appends an array of objects to this hash code builder.
     *
     * @param array
     *  the array of values to append to the hash code
     *
     * @return
     *  a reference to this hash code builder
     */
    public LongHashCodeBuilder append(Object[] array) {
        if (array != null) {
            for (Object element : array) {
                this.append(element);
            }
        }
        else {
            this.append((Object) null);
        }

        return this;
    }

    /**
     * Determines the type of array we have, and directs the call to the proper handler. Acts like
     * a scuffed jump table.
     *
     * @param array
     *  the array to process
     */
    private void processObjectArray(Object array) {
        if (array instanceof byte[]) {
            this.append((byte[]) array);
        }
        else if (array instanceof int[]) {
            this.append((int[]) array);
        }
        else if (array instanceof long[]) {
            this.append((long[]) array);
        }
        else if (array instanceof double[]) {
            this.append((double[]) array);
        }
        else if (array instanceof char[]) {
            this.append((char[]) array);
        }
        else if (array instanceof float[]) {
            this.append((float[]) array);
        }
        else if (array instanceof short[]) {
            this.append((short[]) array);
        }
        else if (array instanceof boolean[]) {
            this.append((boolean[]) array);
        }
        else {
            this.append((Object[]) array);
        }
    }

    /**
     * Fetches the current hash code based on the values provided to this builder thus far.
     * Further calls to <tt>append</tt> will change the output of this method.
     *
     * @return
     *  the current entity version
     */
    public long toHashCode() {
        return this.accumulator;
    }

}
