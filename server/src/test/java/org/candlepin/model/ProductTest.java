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

import static org.junit.Assert.*;

import org.candlepin.test.DatabaseTestFixture;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;


/**
 * ProductTest
 */
@RunWith(JUnitParamsRunner.class)
public class ProductTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;

    @Test
    public void testLockStateAffectsEquality() {
        Owner owner = new Owner("Example-Corporation");
        Product p1 = new Product("test-prod", "test-prod-name", "variant", "1.0.0", "x86", "type");
        Product p2 = new Product("test-prod", "test-prod-name", "variant", "1.0.0", "x86", "type");

        assertEquals(p1, p2);

        p2.setLocked(true);
        assertNotEquals(p1, p2);

        p1.setLocked(true);
        assertEquals(p1, p2);
    }

    protected Object[][] getValuesForEqualityAndReplication() {
        Map<String, String> attributes1 = new HashMap<String, String>();
        attributes1.put("a1", "v1");
        attributes1.put("a2", "v2");
        attributes1.put("a3", "v3");

        Map<String, String> attributes2 = new HashMap<String, String>();
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

        return new Object[][] {
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Multiplier", 1234L, 4567L },
            new Object[] { "Attributes", attributes1, attributes2 },
            new Object[] { "ProductContent", productContent1, productContent2 },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5") },
            // new Object[] { "Href", "test_value", null },
            new Object[] { "Locked", Boolean.TRUE, false }
        };
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = Product.class.getDeclaredMethod("get" + methodSuffix, null);
        }
        catch (NoSuchMethodException e) {
            accessor = Product.class.getDeclaredMethod("is" + methodSuffix, null);
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

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
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

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
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
}
