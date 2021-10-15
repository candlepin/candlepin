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

import static org.candlepin.util.X509Util.ARCH_FACT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPrefix;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.dto.TinySubscription;
import org.candlepin.test.TestUtil;
import org.candlepin.util.X509V3ExtensionUtil.NodePair;
import org.candlepin.util.X509V3ExtensionUtil.PathNode;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;



public class X509V3ExtensionUtilTest {
    private X509V3ExtensionUtil util;
    private ObjectMapper mapper;

    @BeforeEach
    public void init() {
        Configuration config = mock(Configuration.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        mapper = new ObjectMapper();
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

    @Test
    public void nullCompareTo() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);

        assertThrows(NullPointerException.class, () -> np.compareTo(null));
    }

    @Test
    public void nullEquals() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertNotEquals(null, np);
    }

    @Test
    public void otherObjectEquals() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        assertNotEquals(np, pn);
    }

    @Test
    public void notEqualNodes() {
        PathNode pn = util.new PathNode();
        NodePair np = new NodePair("name", pn);
        NodePair np1 = new NodePair("diff", pn);
        assertTrue(np.compareTo(np1) > 0);
        assertNotEquals(np, np1);
    }

    @Test
    public void productWithBrandName() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute(Product.Attributes.BRANDING_TYPE, "OS");
        Set<Product> prods = new HashSet<>(Arrays.asList(p));
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.addBranding(new Branding(null, engProdId, brandedName, "OS"));
        Pool pool = TestUtil.createPool(mktProd);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd, prods,
            new PromotedContent(emptyPrefix()), consumer, pool, new HashSet<>());

        assertEquals(1, certProds.size());
        assertEquals(brandedName, certProds.get(0).getBrandName());
        assertEquals("OS", certProds.get(0).getBrandType());
    }

    @Test
    public void shouldFilterContentWithCorrectArches() {
        String expectedArch = "ppc64";
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
        Product product = new Product("mkt", "MKT SKU");
        addContent(product, "x86_64");
        addContent(product, "x86_64");
        addContent(product, expectedArch);
        addContent(product, expectedArch);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(ARCH_FACT, expectedArch);

        Set<ProductContent> filteredContent = util.filterContentByContentArch(
            new HashSet<>(product.getProductContent()), consumer, product);

        assertEquals(2, filteredContent.size());
        boolean archesMatch = filteredContent.stream()
            .map(ProductContent::getContent)
            .map(Content::getArches)
            .allMatch(expectedArch::equals);
        assertTrue(archesMatch);
    }

    @Test
    public void shouldFilterByProductArchWhenContentHasNoArch() {
        String expectedArch = "ppc64";
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
        Product product = new Product("mkt", "MKT SKU");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        addContent(product);
        addContent(product);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(ARCH_FACT, expectedArch);

        Set<ProductContent> filteredContent = util.filterContentByContentArch(
            new HashSet<>(product.getProductContent()), consumer, product);

        assertEquals(0, filteredContent.size());
    }

    @Test
    public void shouldNOTFilterContentWithNoSpecifiedArches() {
        String expectedArch = "ppc64";
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
        Product product = new Product("mkt", "MKT SKU");
        addContent(product); // add a content without arches specified
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(ARCH_FACT, expectedArch);

        Set<ProductContent> filteredContent = util.filterContentByContentArch(
            new HashSet<>(product.getProductContent()), consumer, product);

        assertEquals(1, filteredContent.size());
        boolean archesMatch = filteredContent.stream()
            .map(ProductContent::getContent)
            .map(Content::getArches)
            .allMatch(Objects::isNull);
        assertTrue(archesMatch);
    }

    private void addContent(Product product) {
        addContent(product, null);
    }

    private void addContent(Product product, String arches) {
        int size = product.getProductContent().size() + 1;
        Content c = new Content();
        c.setUuid("content_" + size);
        c.setId("content_" + size);
        c.setArches(arches);
        product.addContent(c, true);
    }

    @Test
    public void productWithMultipleBrandNames() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
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
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd, prods,
            new PromotedContent(emptyPrefix()), consumer, pool, new HashSet<>());

        assertEquals(1, certProds.size());
        // Should get the first name we encountered
        // but they're in a set so we can't test order
        String resultBrandName = certProds.get(0).getBrandName();
        String resultBrandType = certProds.get(0).getBrandType();
        assertTrue(possibleBrandNames.contains(resultBrandName));
        assertEquals("OS", resultBrandType);
    }

    @Test
    public void shouldOnlyIncludeContentWithCompatibleArchitecture() {
        Owner owner = new Owner("Test Corporation");
        owner.setId("test-id");
        Content content1 = new Content("cont_id1");
        content1.setArches("x86_64");
        Content content2 = new Content("cont_id2");
        content2.setArches("ppc64");
        Product engProd = new Product("content_access", "Content Access");
        engProd.addContent(content1, true);
        engProd.addContent(content2, true);
        Product sku = new Product("content_access", "Content Access");
        Pool pool = TestUtil.createPool(sku);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact("uname.machine", "x86_64");

        org.candlepin.model.dto.Product certProds = util.mapProduct(engProd, sku,
            new PromotedContent(emptyPrefix()), consumer, pool,
            new HashSet<>(Arrays.asList("content_access")));

        assertEquals(1, certProds.getContent().size());
        assertEquals(1, certProds.getContent().get(0).getArches().size());
        assertEquals("x86_64", certProds.getContent().get(0).getArches().get(0));
    }

    private ContentPrefix emptyPrefix() {
        return envId -> "";
    }

    @Test
    public void subscriptionWithSysPurposeAttributes() throws JsonProcessingException {
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        Owner owner = new Owner("Test Corporation");
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.setAttribute(Product.Attributes.USAGE, "my_usage");
        mktProd.setAttribute(Product.Attributes.SUPPORT_LEVEL, "my_support_level");
        mktProd.setAttribute(Product.Attributes.SUPPORT_TYPE, "my_support_type");
        mktProd.setAttribute(Product.Attributes.ROLES, " my_role1, my_role2 , my_role3 ");
        mktProd.setAttribute(Product.Attributes.ADDONS, " my_addon1, my_addon2 , my_addon3 ");
        Pool pool = TestUtil.createPool(owner, mktProd);

        TinySubscription subscription = util.createSubscription(pool);
        String output = this.mapper.writeValueAsString(subscription);
        assertTrue(output.contains("my_usage"), "The serialized data should contain usage!");
        assertTrue(output.contains("my_support_level"), "The serialized data should contain support level!");
        assertTrue(output.contains("my_support_type"), "The serialized data should contain support type!");
        assertTrue(output.contains("my_role1"), "The serialized data should contain role!");
        assertTrue(output.contains("my_role2"), "The serialized data should contain role!");
        assertTrue(output.contains("my_role3"), "The serialized data should contain role!");
        assertTrue(output.contains("my_addon1"), "The serialized data should contain addon!");
        assertTrue(output.contains("my_addon2"), "The serialized data should contain addon!");
        assertTrue(output.contains("my_addon3"), "The serialized data should contain addon!");
    }

}
