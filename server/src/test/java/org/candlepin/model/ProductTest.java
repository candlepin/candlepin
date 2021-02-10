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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * ProductTest
 */
public class ProductTest {

    protected static Stream<Object[]> getValuesForEqualityAndReplication() {
        Map<String, String> attributes1 = new HashMap<>();
        attributes1.put("a1", "v1");
        attributes1.put("a2", "v2");
        attributes1.put("a3", "v3");

        Map<String, String> attributes2 = new HashMap<>();
        attributes2.put("a4", "v4");
        attributes2.put("a5", "v5");
        attributes2.put("a6", "v6");

        Content[] content = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new Content("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
            new Content("c4", "content-4", "test_type", "test_label-4", "test_vendor-4"),
            new Content("c5", "content-5", "test_type", "test_label-5", "test_vendor-5"),
            new Content("c6", "content-6", "test_type", "test_label-6", "test_vendor-6")
        };

        for (Content cobj : content) {
            cobj.setUuid(cobj.getId() + "_uuid");
        }

        Collection<ProductContent> productContent1 = Arrays.asList(
            new ProductContent(null, content[0], true),
            new ProductContent(null, content[1], false),
            new ProductContent(null, content[2], true)
        );

        Collection<ProductContent> productContent2 = Arrays.asList(
            new ProductContent(null, content[3], true),
            new ProductContent(null, content[4], false),
            new ProductContent(null, content[5], true)
        );

        Set<Branding> brandings1 = Stream.of(
            new Branding(null, "eng_prod_id_1", "eng_prod_name_1", "OS"),
            new Branding(null, "eng_prod_id_2", "eng_prod_name_2", "OS"),
            new Branding(null, "eng_prod_id_3", "eng_prod_name_3", "OS")
        ).collect(Collectors.toSet());

        Set<Branding> brandings2 = Stream.of(
            new Branding(null, "eng_prod_id_4", "eng_prod_name_4", "OS"),
            new Branding(null, "eng_prod_id_5", "eng_prod_name_5", "OS"),
            new Branding(null, "eng_prod_id_6", "eng_prod_name_6", "OS")
        ).collect(Collectors.toSet());

        Set<Product> providedP1 = Util.asSet(
            new Product("ak1", "providedProduct1", "varient1", "version1",
            "arch1" , "type1"),
            new Product("ak2", "providedProduct2", "varient2", "version2",
            "arch2", "type2")
        );

        Set<Product> providedP2 = Util.asSet(
            new Product("ak3", "providedProduct3", "varient3", "version3",
            "arch3" , "type3"),
            new Product("ak4", "providedProduct4", "varient4", "version4",
            "arch4" , "type4")
        );

        return Stream.of(
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Multiplier", 1234L, 4567L },
            new Object[] { "Attributes", attributes1, attributes2 },
            new Object[] { "ProductContent", productContent1, productContent2 },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5") },
            new Object[] { "Branding", brandings1, brandings2 },
            new Object[] { "ProvidedProducts", providedP1, providedP2 }
        );
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = Product.class.getDeclaredMethod("get" + methodSuffix);
        }
        catch (NoSuchMethodException e) {
            accessor = Product.class.getDeclaredMethod("is" + methodSuffix);
        }

        try {
            mutator = Product.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(mutatorInputClass)) {
                mutator = Product.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
            }
            else if (Map.class.isAssignableFrom(mutatorInputClass)) {
                mutator = Product.class.getDeclaredMethod("set" + methodSuffix, Map.class);
            }
            else if (Boolean.class.isAssignableFrom(mutatorInputClass)) {
                mutator = Product.class.getDeclaredMethod("set" + methodSuffix, boolean.class);
            }
            else {
                throw e;
            }
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        Product lhs = new Product();
        Product rhs = new Product();

        assertFalse(lhs.equals(null));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testEquality(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        Product lhs = new Product();
        Product rhs = new Product();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertEquals(lhs.hashCode(), rhs.hashCode());

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertFalse(lhs.equals(rhs));
        assertFalse(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
    }

    @Test
    public void testBaseEntityVersion() {
        Product lhs = new Product();
        Product rhs = new Product();

        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testEntityVersion(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        Product lhs = new Product();
        Product rhs = new Product();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertEquals(lhs.getEntityVersion(), rhs.getEntityVersion());

        mutator.invoke(rhs, value2);

        assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        assertNotEquals(lhs.getEntityVersion(), rhs.getEntityVersion());
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testClone(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        Product base = new Product();

        mutator.invoke(base, value1);

        Product clone = (Product) base.clone();

        if (value1 instanceof Collection) {
            assertTrue(Util.collectionsAreEqual(
                (Collection) accessor.invoke(base), (Collection) accessor.invoke(clone)
            ));
        }
        else {
            assertEquals(accessor.invoke(base), accessor.invoke(clone));
        }

        assertEquals(base, clone);
        assertEquals(base.hashCode(), clone.hashCode());
    }

}
