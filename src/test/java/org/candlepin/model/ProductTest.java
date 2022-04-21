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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

        Set<Product> provProducts1 = Set.of(
            new Product("prov_prod-1", "prov_prod-1", "var1", "ver1", "arch1", "type1").setUuid("pp_uuid-1"),
            new Product("prov_prod-2", "prov_prod-2", "var2", "ver2", "arch2", "type2").setUuid("pp_uuid-2"),
            new Product("prov_prod-3", "prov_prod-3", "var3", "ver3", "arch3", "type3"));

        Set<Product> provProducts2 = Set.of(
            new Product("prov_prod-4", "prov_prod-4", "var4", "ver4", "arch4", "type4").setUuid("pp_uuid-4"),
            new Product("prov_prod-5", "prov_prod-5", "var5", "ver5", "arch5", "type5").setUuid("pp_uuid-5"),
            new Product("prov_prod-6", "prov_prod-6", "var6", "ver6", "arch6", "type6"));

        Product derivedProd1 = new Product()
            .setUuid("dp_uuid-1")
            .setId("derived_product-1")
            .setName("derived product 1");

        Product derivedProd2 = new Product()
            .setUuid("dp_uuid-2")
            .setId("derived_product-2")
            .setName("derived product 2");

        Product derivedProd3 = new Product()
            .setId("derived_product-3")
            .setName("derived product 3");

        return Stream.of(
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Multiplier", 1234L, 4567L },
            new Object[] { "Attributes", attributes1, attributes2 },
            new Object[] { "ProductContent", productContent1, productContent2 },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5") },
            new Object[] { "Branding", brandings1, brandings2 },
            new Object[] { "DerivedProduct", derivedProd1, derivedProd2, derivedProd3 },
            new Object[] { "ProvidedProducts", provProducts1, provProducts2 }
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

    @ParameterizedTest(name = "{displayName} {index}: {0} => {1}, {2}")
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

    @ParameterizedTest(name = "{displayName} {index}: {0} => {1}, {2}")
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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
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

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testSetDerivedProductChecksForCyclesOnDerivedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.setDerivedProduct(next);
            tail = next;
        }

        tail.setDerivedProduct(parent);

        assertThrows(IllegalStateException.class, () -> parent.setDerivedProduct(chain));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testSetDerivedProductChecksForCyclesOnProvidedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.addProvidedProduct(next);

            // Add some noise on each level
            for (int i = 0; i < 2; ++i) {
                tail.addProvidedProduct(new Product());
            }

            tail = next;
        }

        tail.addProvidedProduct(parent);

        assertThrows(IllegalStateException.class, () -> parent.setDerivedProduct(chain));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testAddProvidedProductChecksForCyclesOnDerivedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.setDerivedProduct(next);
            tail = next;
        }

        tail.setDerivedProduct(parent);

        assertThrows(IllegalStateException.class, () -> parent.addProvidedProduct(chain));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testAddProvidedProductChecksForCyclesOnProvidedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.addProvidedProduct(next);

            // Add some noise on each level
            for (int i = 0; i < 2; ++i) {
                tail.addProvidedProduct(new Product());
            }

            tail = next;
        }

        tail.addProvidedProduct(parent);

        assertThrows(IllegalStateException.class, () -> parent.addProvidedProduct(chain));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testSetProvidedProductsChecksForCyclesOnDerivedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.setDerivedProduct(next);
            tail = next;
        }

        tail.setDerivedProduct(parent);

        List<Product> children = List.of(new Product(), new Product(), chain);
        assertThrows(IllegalStateException.class, () -> parent.setProvidedProducts(children));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "1", "3", "5" })
    public void testSetProvidedProductsChecksForCyclesOnProvidedProducts(int depth) {
        Product parent = new Product();

        Product chain = new Product();
        Product tail = chain;

        for (int cd = 1; cd < depth; ++cd) {
            Product next = new Product();
            tail.addProvidedProduct(next);

            // Add some noise on each level
            for (int i = 0; i < 2; ++i) {
                tail.addProvidedProduct(new Product());
            }

            tail = next;
        }

        tail.addProvidedProduct(parent);

        List<Product> children = List.of(new Product(), new Product(), chain);
        assertThrows(IllegalStateException.class, () -> parent.setProvidedProducts(children));
    }

    @Test
    public void testSetProvidedProductsFiltersNullElements() {
        Product product = new Product();
        Product child1 = new Product().setId("child1");
        Product child2 = new Product().setId("child2");

        List<Product> children = new ArrayList<>();
        children.add(child1);
        children.add(null);
        children.add(child2);

        Product output = product.setProvidedProducts(children);
        assertSame(output, product);

        Collection<Product> providedProducts = product.getProvidedProducts();
        assertNotNull(providedProducts);
        assertEquals(2, providedProducts.size());

        assertTrue(providedProducts.contains(child1));
        assertTrue(providedProducts.contains(child2));
        assertFalse(providedProducts.contains(null));
    }

    private List<Product> buildTestProducts() {
        Product p1 = spy(new Product())
            .setId("p1");

        Product p2 = spy(new Product())
            .setId("p2");

        Product p3 = spy(new Product())
            .setId("p3");

        // Explictly set the entity version such that we can get into a case where the sum
        // of a subset equals the sum of a different subset
        doReturn(1L).when(p1).getEntityVersion();
        doReturn(2L).when(p2).getEntityVersion();
        doReturn(3L).when(p3).getEntityVersion();

        return List.of(p1, p2, p3);
    }

    @Test
    public void testEntityVersionChangesWithProvidedProducts() {
        // The products here must be generated such that the sum of the entity versions of the first
        // two children is equal to the entity version of the third.
        List<Product> children = this.buildTestProducts();

        Product p1 = new Product();

        Product p2 = new Product();
        p2.addProvidedProduct(children.get(0));

        Product p3 = new Product();
        p3.addProvidedProduct(children.get(0));
        p3.addProvidedProduct(children.get(1));

        Product p4 = new Product();
        p4.addProvidedProduct(children.get(2));

        assertNotEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p2.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p3.getEntityVersion(), p4.getEntityVersion());
    }

    @Test
    public void testEntityVersionIgnoresProvidedProductOrder() {
        List<Product> children = this.buildTestProducts();

        Product p1 = new Product();
        p1.addProvidedProduct(children.get(0));
        p1.addProvidedProduct(children.get(1));
        p1.addProvidedProduct(children.get(2));

        Product p2 = new Product();
        p2.addProvidedProduct(children.get(0));
        p2.addProvidedProduct(children.get(2));
        p2.addProvidedProduct(children.get(1));

        Product p3 = new Product();
        p3.addProvidedProduct(children.get(1));
        p3.addProvidedProduct(children.get(0));
        p3.addProvidedProduct(children.get(2));

        Product p4 = new Product();
        p4.addProvidedProduct(children.get(1));
        p4.addProvidedProduct(children.get(2));
        p4.addProvidedProduct(children.get(0));

        Product p5 = new Product();
        p5.addProvidedProduct(children.get(2));
        p5.addProvidedProduct(children.get(0));
        p5.addProvidedProduct(children.get(1));

        Product p6 = new Product();
        p6.addProvidedProduct(children.get(2));
        p6.addProvidedProduct(children.get(1));
        p6.addProvidedProduct(children.get(0));

        assertEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertEquals(p3.getEntityVersion(), p4.getEntityVersion());
        assertEquals(p4.getEntityVersion(), p5.getEntityVersion());
        assertEquals(p5.getEntityVersion(), p6.getEntityVersion());
    }

    private List<ProductContent> buildTestContent() {
        ProductContent pc1 = spy(new ProductContent())
            .setContent(new Content().setId("c1"));

        ProductContent pc2 = spy(new ProductContent())
            .setContent(new Content().setId("c2"));

        ProductContent pc3 = spy(new ProductContent())
            .setContent(new Content().setId("c3"));

        // Explictly set the entity version such that we can get into a case where the sum
        // of a subset equals the sum of a different subset
        doReturn(1L).when(pc1).getEntityVersion();
        doReturn(2L).when(pc2).getEntityVersion();
        doReturn(3L).when(pc3).getEntityVersion();

        return List.of(pc1, pc2, pc3);
    }

    @Test
    public void testEntityVersionChangesWithContent() {
        // The ProductContent instances here must be generated such that the sum of the entity
        // versions of the first two children is equal to the entity version of the third.
        List<ProductContent> children = this.buildTestContent();

        Product p1 = new Product();

        Product p2 = new Product();
        p2.addProductContent(children.get(0));

        Product p3 = new Product();
        p3.addProductContent(children.get(0));
        p3.addProductContent(children.get(1));

        Product p4 = new Product();
        p4.addProductContent(children.get(2));

        assertNotEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p2.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p3.getEntityVersion(), p4.getEntityVersion());
    }

    @Test
    public void testEntityVersionIgnoresContentOrder() {
        List<ProductContent> children = this.buildTestContent();

        Product p1 = new Product();
        p1.addProductContent(children.get(0));
        p1.addProductContent(children.get(1));
        p1.addProductContent(children.get(2));

        Product p2 = new Product();
        p2.addProductContent(children.get(0));
        p2.addProductContent(children.get(2));
        p2.addProductContent(children.get(1));

        Product p3 = new Product();
        p3.addProductContent(children.get(1));
        p3.addProductContent(children.get(0));
        p3.addProductContent(children.get(2));

        Product p4 = new Product();
        p4.addProductContent(children.get(1));
        p4.addProductContent(children.get(2));
        p4.addProductContent(children.get(0));

        Product p5 = new Product();
        p5.addProductContent(children.get(2));
        p5.addProductContent(children.get(0));
        p5.addProductContent(children.get(1));

        Product p6 = new Product();
        p6.addProductContent(children.get(2));
        p6.addProductContent(children.get(1));
        p6.addProductContent(children.get(0));

        assertEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertEquals(p3.getEntityVersion(), p4.getEntityVersion());
        assertEquals(p4.getEntityVersion(), p5.getEntityVersion());
        assertEquals(p5.getEntityVersion(), p6.getEntityVersion());
    }

    private List<Branding> buildTestBranding() {
        // Explictly set the hashcode such that we can get into a case where the sum
        // of a subset equals the sum of a different subset
        Branding b1 = new Branding() {
            @Override
            @SuppressWarnings("EqualsHashCode")
            public int hashCode() {
                return 1;
            }
        };
        b1.setId("b1");

        Branding b2 = new Branding() {
            @Override
            @SuppressWarnings("EqualsHashCode")
            public int hashCode() {
                return 2;
            }
        };
        b2.setId("b2");

        Branding b3 = new Branding() {
            @Override
            @SuppressWarnings("EqualsHashCode")
            public int hashCode() {
                return 3;
            }
        };
        b3.setId("b3");

        return List.of(b1, b2, b3);
    }

    @Test
    public void testEntityVersionChangesWithBranding() {
        // The branding here must be generated such that the sum of the hashcodes of the first two
        // children is equal to the hash code of the third.
        List<Branding> children = this.buildTestBranding();

        Product p1 = new Product();

        Product p2 = new Product();
        p2.addBranding(children.get(0));

        Product p3 = new Product();
        p3.addBranding(children.get(0));
        p3.addBranding(children.get(1));

        Product p4 = new Product();
        p4.addBranding(children.get(2));

        assertNotEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p2.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p3.getEntityVersion(), p4.getEntityVersion());
    }

    @Test
    public void testEntityVersionIgnoresBrandingOrder() {
        List<Branding> children = this.buildTestBranding();

        Product p1 = new Product();
        p1.addBranding(children.get(0));
        p1.addBranding(children.get(1));
        p1.addBranding(children.get(2));

        Product p2 = new Product();
        p2.addBranding(children.get(0));
        p2.addBranding(children.get(2));
        p2.addBranding(children.get(1));

        Product p3 = new Product();
        p3.addBranding(children.get(1));
        p3.addBranding(children.get(0));
        p3.addBranding(children.get(2));

        Product p4 = new Product();
        p4.addBranding(children.get(1));
        p4.addBranding(children.get(2));
        p4.addBranding(children.get(0));

        Product p5 = new Product();
        p5.addBranding(children.get(2));
        p5.addBranding(children.get(0));
        p5.addBranding(children.get(1));

        Product p6 = new Product();
        p6.addBranding(children.get(2));
        p6.addBranding(children.get(1));
        p6.addBranding(children.get(0));

        assertEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertEquals(p3.getEntityVersion(), p4.getEntityVersion());
        assertEquals(p4.getEntityVersion(), p5.getEntityVersion());
        assertEquals(p5.getEntityVersion(), p6.getEntityVersion());
    }

    @Test
    public void testEntityVersioningAvoidsCollidingOnDifferentChildren() {
        // Create a set of children which all have the same version or hashcode
        Product derived = spy(new Product())
            .setId("derived");
        Product provided = spy(new Product())
            .setId("provided");
        ProductContent pcontent = spy(new ProductContent())
            .setContent(new Content().setId("content"));

        doReturn(1L).when(derived).getEntityVersion();
        doReturn(1L).when(provided).getEntityVersion();
        doReturn(1L).when(pcontent).getEntityVersion();

        Branding branding = new Branding() {
            @Override
            @SuppressWarnings("EqualsHashCode")
            public int hashCode() {
                return 1;
            }
        };
        branding.setId("b1");


        Product p1 = new Product();
        p1.setDerivedProduct(derived);

        Product p2 = new Product();
        p2.addProvidedProduct(provided);

        Product p3 = new Product();
        p3.addProductContent(pcontent);

        Product p4 = new Product();
        p4.addBranding(branding);

        assertNotEquals(p1.getEntityVersion(), p2.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p1.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p2.getEntityVersion(), p3.getEntityVersion());
        assertNotEquals(p2.getEntityVersion(), p4.getEntityVersion());

        assertNotEquals(p3.getEntityVersion(), p4.getEntityVersion());
    }
}
