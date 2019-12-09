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
package org.candlepin.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import org.candlepin.TestingModules;
import org.candlepin.common.config.Configuration;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Branding;
import org.candlepin.model.ProductContent;
import org.candlepin.model.Owner;
import org.candlepin.model.dto.TinySubscription;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * X509V3ExtensionUtilTest
 */
public class X509V3ExtensionUtilTest {
    private Configuration config;
    private EntitlementCurator ec;
    private X509V3ExtensionUtil util;
    @Inject @Named("X509V3ExtensionUtilObjectMapper") private ObjectMapper mapper;

    @Before
    public void init() {
        config = mock(Configuration.class);
        ec = mock(EntitlementCurator.class);
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest()
        );
        injector.injectMembers(this);
        util = new X509V3ExtensionUtil(config, ec, this.mapper);
    }

    @Test
    public void compareToEquals() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("name", pn);
        assertEquals(0, np.compareTo(np1));
        assertEquals(np, np1);
    }

    @Test(expected = NullPointerException.class)
    public void nullCompareTo() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertEquals(1, np.compareTo(null));
    }

    @Test
    public void nullEquals() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertFalse(np.equals(null));
    }

    @Test
    public void otherObjectEquals() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertFalse(np.equals(pn));
    }

    @Test
    public void notEqualNodes() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("diff", pn);
        assertTrue(np.compareTo(np1) > 0);
        assertFalse(np.equals(np1));
    }

    @Test
    public void testPrefixLogic() {
        Owner owner = new Owner("Test Corporation");
        Product p = new Product("JarJar", "Binks");
        Content c = new Content();
        c.setContentUrl("/some/path");
        ProductContent pc = new ProductContent(p, c, true);

        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is/", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is///", pc));
        c.setContentUrl("some/path");
        assertEquals("/some/path", util.createFullContentPath(null, pc));
        assertEquals("/some/path", util.createFullContentPath("", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is/", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is///", pc));
        c.setContentUrl("///////some/path");
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is/", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is", pc));
        assertEquals("/this/is/some/path", util.createFullContentPath("/this/is///", pc));
        assertEquals("/some/path", util.createFullContentPath(null, pc));
        assertEquals("/some/path", util.createFullContentPath("", pc));
        c.setContentUrl("http://some/path");
        assertEquals("http://some/path", util.createFullContentPath("/this/is", pc));
        c.setContentUrl("https://some/path");
        assertEquals("https://some/path", util.createFullContentPath("/this/is", pc));
        c.setContentUrl("ftp://some/path");
        assertEquals("ftp://some/path", util.createFullContentPath("/this/is", pc));
        c.setContentUrl("file://some/path");
        assertEquals("file://some/path", util.createFullContentPath("/this/is", pc));
    }

    @Test
    public void productWithBrandName() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Owner owner = new Owner("Test Corporation");
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute(Product.Attributes.BRANDING_TYPE, "OS");
        Set<Product> prods = new HashSet<>(Arrays.asList(p));
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.addBranding(new Branding(null, engProdId, brandedName, "OS"));
        Pool pool = TestUtil.createPool(mktProd);
        Consumer consumer = new Consumer();
        Entitlement e = new Entitlement(pool, consumer, owner, 10);

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd,
            prods, "", new HashMap<>(),  new Consumer(), pool);

        assertEquals(1, certProds.size());
        assertEquals(brandedName, certProds.get(0).getBrandName());
        assertEquals("OS", certProds.get(0).getBrandType());
    }

    @Test
    public void productWithMultipleBrandNames() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute(Product.Attributes.BRANDING_TYPE, "OS");
        Set<Product> prods = new HashSet<>(Arrays.asList(p));
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.addBranding(new Branding(null, engProdId, brandedName, "OS"));
        mktProd.addBranding(new Branding(null, engProdId, "another brand name", "OS"));
        mktProd.addBranding(new Branding(null, engProdId, "number 3", "OS"));
        Pool pool = TestUtil.createPool(mktProd);
        Set<String> possibleBrandNames = new HashSet<>();
        for (Branding b : mktProd.getBranding()) {
            possibleBrandNames.add(b.getName());
        }

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd,
            prods, "", new HashMap<>(),  new Consumer(), pool);

        assertEquals(1, certProds.size());
        // Should get the first name we encountered
        // but they're in a set so we can't test order
        String resultBrandName = certProds.get(0).getBrandName();
        String resultBrandType = certProds.get(0).getBrandType();
        assertTrue(possibleBrandNames.contains(resultBrandName));
        assertEquals("OS", resultBrandType);
    }

    @Test
    public void susbcriptionWithSyspurposeAttributes() throws JsonProcessingException {
        Owner owner = new Owner("Test Corporation");
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.setAttribute(Product.Attributes.USAGE, "my_usage");
        mktProd.setAttribute(Product.Attributes.SUPPORT_LEVEL, "my_support_level");
        mktProd.setAttribute(Product.Attributes.SUPPORT_TYPE, "my_support_type");
        mktProd.setAttribute(Product.Attributes.ROLES, " my_role1, my_role2 ");
        mktProd.setAttribute(Product.Attributes.ADDONS, " my_addon1, my_addon2 ");
        Pool pool = TestUtil.createPool(owner, mktProd);

        TinySubscription subscription = util.createSubscription(pool);
        String output = this.mapper.writeValueAsString(subscription);
        assertTrue("The serialized data should contain usage!", output.contains("my_usage"));
        assertTrue("The serialized data should contain support level!", output.contains("my_support_level"));
        assertTrue("The serialized data should contain support type!", output.contains("my_support_type"));
        assertTrue("The serialized data should contain role!", output.contains("my_role1"));
        assertTrue("The serialized data should contain role!", output.contains("my_role2"));
        assertTrue("The serialized data should contain addon!", output.contains("my_addon1"));
        assertTrue("The serialized data should contain addon!", output.contains("my_addon2"));
    }

}
