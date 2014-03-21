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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.config.Config;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Content;
import org.candlepin.model.ProductContent;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;
import org.junit.Before;
import org.junit.Test;


/**
 * X509V3ExtensionUtilTest
 */
public class X509V3ExtensionUtilTest {
    private Config config;
    private EntitlementCurator ec;
    private X509V3ExtensionUtil util;


    @Before
    public void init() {
        config = mock(Config.class);
        ec = mock(EntitlementCurator.class);
        util = new X509V3ExtensionUtil(config, ec);
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
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute("brand_type", "OS");
        Set<Product> prods = new HashSet<Product>(Arrays.asList(p));
        Pool pool = TestUtil.createPool(new Product("mkt", "MKT SKU"));
        pool.getBranding().add(new Branding(engProdId, "OS", brandedName));
        Consumer consumer = new Consumer();
        Entitlement e = new Entitlement(pool, consumer, 10);

        List<org.candlepin.json.model.Product> certProds = util.createProducts(prods, "",
            new HashMap<String, EnvironmentContent>(),  new Consumer(), e);

        assertEquals(1, certProds.size());
        assertEquals(brandedName, certProds.get(0).getBrandName());
        assertEquals("OS", certProds.get(0).getBrandType());
    }

    @Test
    public void productWithMultipleBrandNames() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute("brand_type", "OS");
        Set<Product> prods = new HashSet<Product>(Arrays.asList(p));
        Pool pool = TestUtil.createPool(new Product("mkt", "MKT SKU"));
        pool.getBranding().add(new Branding(engProdId, "OS", brandedName));
        pool.getBranding().add(new Branding(engProdId, "OS", "another brand name"));
        pool.getBranding().add(new Branding(engProdId, "OS", "number 3"));
        Set<String> possibleBrandNames = new HashSet<String>();
        for (Branding b : pool.getBranding()) {
            possibleBrandNames.add(b.getName());
        }
        Consumer consumer = new Consumer();
        Entitlement e = new Entitlement(pool, consumer, 10);

        List<org.candlepin.json.model.Product> certProds = util.createProducts(prods, "",
            new HashMap<String, EnvironmentContent>(),  new Consumer(), e);

        assertEquals(1, certProds.size());
        // Should get the first name we encountered
        // but they're in a set so we can't test order
        String resultBrandName = certProds.get(0).getBrandName();
        String resultBrandType = certProds.get(0).getBrandType();
        assertTrue(possibleBrandNames.contains(resultBrandName));
        assertEquals("OS", resultBrandType);
    }

}
