/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Base test suite for the CandlepinDTO subclasses
 */
// Ordinarily methodSources are static, but ours relies on pulling values from an instance, so we need this
// special annotation
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractDTOTest<T extends CandlepinDTO<T>> {
    protected static Logger log = LoggerFactory.getLogger(AbstractDTOTest.class);

    public static final Pattern ACCESSOR_NAME_REGEX = Pattern.compile("(?:get|is)([a-zA-Z0-9]+)");

    protected final Class<T> dtoClass;
    protected final Map<String, Method[]> fields;
    protected final Constructor<T> copyConstructor;

    public AbstractDTOTest(Class<T> dtoClass) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass is null");
        }

        this.dtoClass = dtoClass;
        this.fields = new HashMap<>();

        // Scan for accessor/mutator pairs to test
        for (Method method : this.dtoClass.getMethods()) {
            try {
                Matcher matcher = ACCESSOR_NAME_REGEX.matcher(method.getName());
                if (matcher.matches() && method.getParameterTypes().length == 0) {
                    String fieldName = matcher.group(1);
                    Method mutator = this.dtoClass.getMethod("set" + fieldName, method.getReturnType());
                    fields.put(fieldName, new Method[] { method, mutator });
                }
            }
            catch (NoSuchMethodException e) {
                // Nothing too out of the ordinary here, just asymmetric accessors/mutators.
                // Continue looking for matches
            }
        }

        // Check if we have a copy constructor for this DTO
        Constructor constructor = null;
        try {
            constructor = this.dtoClass.getConstructor(this.dtoClass);
        }
        catch (NoSuchMethodException e) {
            // No copy constructor here; we'll be skipping those tests
        }

        this.copyConstructor = constructor;
    }

    public T getDTOInstance() {
        try {
            return this.dtoClass.newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to instantiate DTO test instance", e);
        }
    }

    public T getPopulatedDTOInstance() {
        try {
            T dto = this.getDTOInstance();

            for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
                String field = entry.getKey();
                Method[] methods = entry.getValue();

                Object input = this.getInputValueForMutator(field);
                methods[1].invoke(dto, input);
            }

            return dto;
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to instantiate DTO test instance", e);
        }
    }

    public T getDTOInstance(T source) {
        if (this.copyConstructor == null) {
            throw new IllegalStateException(
                "DTO class \"" + this.dtoClass.getSimpleName() + "\" does not define a copy constructor.");
        }

        try {
            return this.copyConstructor.newInstance(source);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to instantiate copied DTO test instance", e);
        }
    }

    public Stream<Object> getFieldNames() {
        return Stream.of(this.fields.keySet().toArray());
    }

    /**
     * Fetches the value that should be returned for the given field. This value will be passed,
     * unmodified, to the mutator for the given field.
     *
     * @param field
     *  The name of the field being tested. The capitialization of the field name will follow that
     *  of the accessor/mutator for the field. For instance, if the mutator is named setOwnerName,
     *  the field name will be "OwnerName".
     *
     * @return
     *  the value to pass to the mutator
     */
    protected abstract Object getInputValueForMutator(String field);

    /**
     * Fetches the value that should be returned by the accessor for the given field. In general,
     * this will be the input value passed to the mutator unmodified. However, in cases where the
     * output would be modified or filtered, the value returned by this method should reflect that
     * expected change.
     *
     * @param field
     *  The name of the field being tested. The capitialization of the field name will follow that
     *  of the accessor/mutator for the field. For instance, if the mutator is named setOwnerName,
     *  the field name will be "OwnerName".
     *
     * @param input
     *  The value input to the matching mutator. This value may be null when testing the base state
     *  for the accessor.
     *
     * @return
     *  the value expected to be output by the accessor for the given field when the field is set
     *  to the specified input value
     */
    protected abstract Object getOutputValueForAccessor(String field, Object input);

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testAccessorsAndMutators(String field) throws Exception {
        Method[] methods = this.fields.get(field);

        T dto = this.getDTOInstance();

        Object expected = this.getOutputValueForAccessor(field, null);
        Object actual = methods[0].invoke(dto);

        if (expected instanceof Collection) {
            // The collectionsAreEquals method does a more accurate comparison than assertEquals,
            // but it's still only a binary check. We'll fall back to assertEquals if it fails to
            // try to get some info as to what doesn't match.
            if (!Util.collectionsAreEqual((Collection) expected, (Collection) actual)) {
                // This should fail, but will hopefully give us some indication as to why it failed
                assertEquals(expected, actual);
            }
        }
        else {
            assertEquals(expected, actual);
        }

        Object input = this.getInputValueForMutator(field);
        Object output = methods[1].invoke(dto, input);

        // In the general case, we don't care about the output from the mutator. However, if it's
        // not a void type or isn't returning an instance of the DTO itself for chaining, then we
        // should probably be verifying the output is correct in an explicit test.
        Class returnType = methods[1].getReturnType();
        if (CandlepinDTO.class.isAssignableFrom(returnType)) {
            assertSame(output, dto);
        }
        else if (!Void.TYPE.equals(returnType)) {
            log.warn("The method \"set" + field + "\" probably requires explicit testing.");
        }

        expected = this.getOutputValueForAccessor(field, input);
        actual = methods[0].invoke(dto);

        if (expected instanceof Collection) {
            // The collectionsAreEquals method does a more accurate comparison than assertEquals,
            // but it's still only a binary check. We'll fall back to assertEquals if it fails to
            // try to get some info as to what doesn't match.
            if (!Util.collectionsAreEqual((Collection) expected, (Collection) actual)) {
                // This should fail, but will hopefully give us some indication as to why it failed
                assertEquals(expected, actual);
            }
        }
        else {
            assertEquals(expected, actual);
        }
    }

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testEqualsForIndividualFields(String field) throws Exception {
        Method[] methods = this.fields.get(field);

        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertTrue(dtoA.equals(dtoB));

        Object input = this.getInputValueForMutator(field);
        methods[1].invoke(dtoA, input);

        assertFalse(dtoA.equals(dtoB));

        methods[1].invoke(dtoB, input);

        assertTrue(dtoA.equals(dtoB));
    }

    @Test
    public void testEqualityForAllFields() throws Exception {
        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertTrue(dtoA.equals(dtoB));

        for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
            String field = entry.getKey();
            Method[] methods = entry.getValue();

            Object input = this.getInputValueForMutator(field);
            methods[1].invoke(dtoA, input);

            assertFalse(dtoA.equals(dtoB));

            methods[1].invoke(dtoB, input);
        }

        assertTrue(dtoA.equals(dtoB));
    }

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testHashCodeForIndividualFields(String field) throws Exception {
        Method[] methods = this.fields.get(field);

        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertEquals(dtoA.hashCode(), dtoB.hashCode());

        Object input = this.getInputValueForMutator(field);
        methods[1].invoke(dtoA, input);

        assertFalse(dtoA.hashCode() == dtoB.hashCode());

        methods[1].invoke(dtoB, input);

        assertEquals(dtoA.hashCode(), dtoB.hashCode());
    }

    @Test
    public void testHashCodeForAllFields() throws Exception {
        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertEquals(dtoA.hashCode(), dtoB.hashCode());

        for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
            String field = entry.getKey();
            Method[] methods = entry.getValue();

            Object input = this.getInputValueForMutator(field);
            methods[1].invoke(dtoA, input);

            assertFalse(dtoA.hashCode() == dtoB.hashCode());

            methods[1].invoke(dtoB, input);

            assertTrue(dtoA.hashCode() == dtoB.hashCode());
        }

        assertEquals(dtoA.hashCode(), dtoB.hashCode());
    }

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testPopulateForIndividualFields(String field) throws Exception {
        Method[] methods = this.fields.get(field);

        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertEquals(dtoA, dtoB);

        Object input = this.getInputValueForMutator(field);
        methods[1].invoke(dtoA, input);

        assertFalse(dtoA.equals(dtoB));

        dtoB.populate(dtoA);
        assertEquals(dtoA, dtoB);
    }

    @Test
    public void testPopulateForAllFields() throws Exception {
        T dtoA = this.getDTOInstance();
        T dtoB = this.getDTOInstance();

        assertEquals(dtoA, dtoB);

        for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
            String field = entry.getKey();
            Method[] methods = entry.getValue();

            Object input = this.getInputValueForMutator(field);
            methods[1].invoke(dtoA, input);
        }

        assertNotEquals(dtoA, dtoB);

        dtoB.populate(dtoA);
        assertEquals(dtoA, dtoB);
    }

    @Test
    public void testPopulateWithNullSource() {
        T dto = this.getDTOInstance();
        assertThrows(IllegalArgumentException.class, () -> dto.populate(null));
    }

    @Test
    public void testCloneFromBaseState() {
        T dto = this.getDTOInstance();
        T copy = (T) dto.clone();

        assertEquals(dto, copy);
    }

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testCloneForIndividualFields(String field) throws Exception {
        Method[] methods = this.fields.get(field);
        T dto = this.getDTOInstance();

        Object input = this.getInputValueForMutator(field);
        methods[1].invoke(dto, input);

        T copy = (T) dto.clone();
        assertEquals(dto, copy);

        Object expected = methods[0].invoke(dto);
        Object actual = methods[0].invoke(copy);
        assertEquals(expected, actual);
    }

    @Test
    public void testCloneForAllFields() throws Exception {
        T dto = this.getDTOInstance();

        for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
            String field = entry.getKey();
            Method[] methods = entry.getValue();

            Object input = this.getInputValueForMutator(field);
            methods[1].invoke(dto, input);
        }

        T copy = (T) dto.clone();
        assertEquals(dto, copy);

        for (Method[] methods : this.fields.values()) {
            Object expected = methods[0].invoke(dto);
            Object actual = methods[0].invoke(copy);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testCopyConstructorFromBaseState() {
        // Skip this test if we don't have a copy constructor to test
        assumeTrue(this.copyConstructor != null);

        T dto = this.getDTOInstance();
        T copy = this.getDTOInstance(dto);

        assertEquals(dto, copy);
    }

    @ParameterizedTest
    @MethodSource("getFieldNames")
    public void testCopyConstructorForIndividualFields(String field) throws Exception {
        // Skip this test if we don't have a copy constructor to test
        assumeTrue(this.copyConstructor != null);

        Method[] methods = this.fields.get(field);
        T dto = this.getDTOInstance();

        Object input = this.getInputValueForMutator(field);
        methods[1].invoke(dto, input);

        T copy = this.getDTOInstance(dto);
        assertEquals(dto, copy);

        Object expected = methods[0].invoke(dto);
        Object actual = methods[0].invoke(copy);

        if (expected instanceof Collection) {
            // The collectionsAreEquals method does a more accurate comparison than assertEquals,
            // but it's still only a binary check. We'll fall back to assertEquals if it fails to
            // try to get some info as to what doesn't match.
            if (!Util.collectionsAreEqual((Collection) expected, (Collection) actual)) {
                // This should fail, but will hopefully give us some indication as to why it failed
                assertEquals(expected, actual);
            }
        }
        else {
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testCopyConstructorForAllFields() throws Exception {
        // Skip this test if we don't have a copy constructor to test
        assumeTrue(this.copyConstructor != null);

        T dto = this.getDTOInstance();

        for (Map.Entry<String, Method[]> entry : this.fields.entrySet()) {
            String field = entry.getKey();
            Method[] methods = entry.getValue();

            Object input = this.getInputValueForMutator(field);
            methods[1].invoke(dto, input);
        }

        T copy = this.getDTOInstance(dto);
        assertEquals(dto, copy);

        for (Method[] methods : this.fields.values()) {
            Object expected = methods[0].invoke(dto);
            Object actual = methods[0].invoke(copy);

            if (expected instanceof Collection) {
                // The collectionsAreEquals method does a more accurate comparison than assertEquals,
                // but it's still only a binary check. We'll fall back to assertEquals if it fails to
                // try to get some info as to what doesn't match.
                if (!Util.collectionsAreEqual((Collection) expected, (Collection) actual)) {
                    // This should fail, but will hopefully give us some indication as to why it failed
                    assertEquals(expected, actual);
                }
            }
            else {
                assertEquals(expected, actual);
            }
        }
    }
}
