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
package org.candlepin.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Test suite for the CandlepinDTO class
 */
public class CandlepinDTOTest {

    public static class CandlepinDTOImpl extends CandlepinDTO {
        // Intentionally left empty
    }

    public static class HibernateObjectImpl extends AbstractHibernateObject {
        public String getId() {
            return null;
        }
    }

    @Test
    public void testGetSetCreated() {
        CandlepinDTO dto = new CandlepinDTOImpl();
        Date input = TestUtil.createDate(1, 1, 2015);

        Date output = dto.getCreated();
        assertNull(output);

        CandlepinDTO output2 = dto.setCreated(input);
        assertSame(output2, dto);

        output = dto.getCreated();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetUpdated() {
        CandlepinDTO dto = new CandlepinDTOImpl();
        Date input = TestUtil.createDate(1, 1, 2015);

        Date output = dto.getUpdated();
        assertNull(output);

        CandlepinDTO output2 = dto.setUpdated(input);
        assertSame(output2, dto);

        output = dto.getUpdated();
        assertEquals(input, output);
    }

    protected static Stream<Object[]> getValuesForEqualityAndReplication() {
        return Stream.of(
            new Object[] { "Created", TestUtil.createDate(1, 1, 2015), TestUtil.createDate(2, 2, 2015) },
            new Object[] { "Updated", TestUtil.createDate(3, 3, 2016), TestUtil.createDate(4, 4, 2016) }
        );
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = CandlepinDTO.class.getDeclaredMethod("get" + methodSuffix);
        }
        catch (NoSuchMethodException e) {
            accessor = CandlepinDTO.class.getDeclaredMethod("is" + methodSuffix);
        }

        try {
            mutator = CandlepinDTO.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (!Collection.class.isAssignableFrom(mutatorInputClass)) {
                throw e;
            }

            mutator = CandlepinDTO.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        CandlepinDTO lhs = new CandlepinDTOImpl();
        CandlepinDTO rhs = new CandlepinDTOImpl();

        assertNotEquals(null, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
        assertEquals(lhs, rhs);
        assertEquals(rhs, lhs);
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testEquality(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        CandlepinDTO lhs = new CandlepinDTOImpl();
        CandlepinDTO rhs = new CandlepinDTOImpl();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertEquals(lhs, rhs);
        assertEquals(rhs, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
        assertEquals(lhs.hashCode(), rhs.hashCode());

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertNotEquals(lhs, rhs);
        assertNotEquals(rhs, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testClone(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        CandlepinDTO base = new CandlepinDTOImpl();

        mutator.invoke(base, value1);

        CandlepinDTO clone = (CandlepinDTO) base.clone();

        assertEquals(accessor.invoke(base), accessor.invoke(clone));
        assertEquals(base, clone);
        assertEquals(base.hashCode(), clone.hashCode());
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testPopulateWithDTO(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        CandlepinDTO base = new CandlepinDTOImpl();
        CandlepinDTO source = new CandlepinDTOImpl();

        mutator.invoke(source, value1);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : CandlepinDTOImpl.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+")) {
                Object output = method.invoke(base);

                if (method.getName().equals(accessor.getName())) {
                    if (value1 instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) value1, (Collection) output));
                    }
                    else {
                        assertEquals(value1, output);
                    }
                }
                else {
                    assertNull(output);
                }
            }
        }
    }

    protected static Stream<Object[]> getValuesPopulationByEntity() {
        return Stream.of(
            new Object[] { "Created", TestUtil.createDate(1, 1, 2015), null },
            new Object[] { "Updated", TestUtil.createDate(2, 2, 2016), null }
        );
    }

    @ParameterizedTest
    @MethodSource("getValuesPopulationByEntity")
    public void testPopulateWithEntity(String valueName, Object input, Object defaultValue) throws Exception {
        Method accessor = null;
        Method mutator = null;

        try {
            accessor = CandlepinDTO.class.getDeclaredMethod("get" + valueName);
        }
        catch (NoSuchMethodException e) {
            accessor = CandlepinDTO.class.getDeclaredMethod("is" + valueName);
        }

        try {
            mutator = AbstractHibernateObject.class.getDeclaredMethod("set" + valueName, input.getClass());
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(input.getClass())) {
                mutator = AbstractHibernateObject.class.getDeclaredMethod("set" + valueName,
                    Collection.class);
            }
            else if (Boolean.class.isAssignableFrom(input.getClass())) {
                mutator = AbstractHibernateObject.class.getDeclaredMethod("set" + valueName, boolean.class);
            }
            else {
                throw e;
            }
        }

        CandlepinDTO base = new CandlepinDTOImpl();
        AbstractHibernateObject source = new HibernateObjectImpl();

        mutator.invoke(source, input);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : CandlepinDTO.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+")) {
                Object output = method.invoke(base);

                if (method.getName().equals(accessor.getName())) {
                    if (input instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) input, (Collection) output));
                    }
                    else {
                        assertEquals(input, output);
                    }
                }
                else {
                    Iterator<Object[]> i = getValuesPopulationByEntity().iterator();
                    while (i.hasNext()) {
                        Object[] values = i.next();
                        if (method.getName().endsWith((String) values[0])) {
                            if (values[2] instanceof Collection) {
                                assertTrue(output instanceof Collection);
                                assertTrue(Util.collectionsAreEqual((Collection) values[2],
                                    (Collection) output));
                            }
                            else {
                                assertEquals(values[2], output);
                            }
                        }
                    }
                }
            }
        }
    }
}
