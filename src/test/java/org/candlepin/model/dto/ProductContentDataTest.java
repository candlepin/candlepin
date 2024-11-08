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

import org.candlepin.model.Content;
import org.candlepin.model.ProductContent;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Test suite for the ProductContentData class
 */
public class ProductContentDataTest {

    @Test
    public void testGetSetContent() {
        ProductContentData dto = new ProductContentData();
        ContentData input = new ContentData("id", "name", "type", "label", "vendor");

        ContentData output = dto.getContent();
        assertNull(output);

        ProductContentData output2 = dto.setContent(input);
        assertSame(output2, dto);

        output = dto.getContent();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetEnabled() {
        ProductContentData dto = new ProductContentData();
        Boolean input = Boolean.TRUE;

        Boolean output = dto.isEnabled();
        assertNull(output);

        ProductContentData output2 = dto.setEnabled(input);
        assertSame(output2, dto);

        output = dto.isEnabled();
        assertEquals(input, output);
    }

    protected static Stream<Object[]> getValuesForEqualityAndReplication() {
        ContentData input1 = new ContentData("id1", "name1", "type1", "label1", "vendor1");
        ContentData input2 = new ContentData("id2", "name2", "type2", "label2", "vendor2");

        return Stream.of(
            new Object[] { "Content", input1, input2 },
            new Object[] { "Enabled", Boolean.TRUE, Boolean.FALSE }
        );
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ProductContentData.class.getDeclaredMethod("get" + methodSuffix);
        }
        catch (NoSuchMethodException e) {
            accessor = ProductContentData.class.getDeclaredMethod("is" + methodSuffix);
        }

        try {
            mutator = ProductContentData.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (!Collection.class.isAssignableFrom(mutatorInputClass)) {
                throw e;
            }

            mutator = ProductContentData.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        ProductContentData lhs = new ProductContentData();
        ProductContentData rhs = new ProductContentData();

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

        ProductContentData lhs = new ProductContentData();
        ProductContentData rhs = new ProductContentData();

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

        ProductContentData base = new ProductContentData();

        mutator.invoke(base, value1);

        ProductContentData clone = (ProductContentData) base.clone();

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

        ProductContentData base = new ProductContentData();
        ProductContentData source = new ProductContentData();

        mutator.invoke(source, value1);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : ProductContentData.class.getDeclaredMethods()) {
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

    @Test
    public void testPopulateWithEntityContent() {
        Content contentEntity = new Content("id1")
            .setName("name1")
            .setType("type1")
            .setLabel("label1")
            .setVendor("vendor1");

        ContentData contentData = contentEntity.toDTO();

        ProductContentData base = new ProductContentData();
        ProductContent source = new ProductContent(contentEntity, true);

        base.populate(source);

        assertEquals(contentData, base.getContent());
        assertTrue(base.isEnabled());
    }
}
