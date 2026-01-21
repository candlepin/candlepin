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
package org.candlepin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.config.Configuration;
import org.candlepin.controller.util.ContentPathBuilder;
import org.candlepin.controller.util.PromotedContent;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.pki.huffman.Huffman;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public class X509V3ExtensionUtilTest {
    private X509V3ExtensionUtil util;
    private ObjectMapper mapper;

    @BeforeEach
    public void init() {
        this.mapper = ObjectMapperFactory.getX509V3ExtensionUtilObjectMapper();

        Configuration config = mock(Configuration.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        util = new X509V3ExtensionUtil(config, ec, new Huffman());
    }

    @Test
    public void productWithBrandName() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute(Product.Attributes.BRANDING_TYPE, "OS");
        Set<Product> prods = Set.of(p);
        Product mktProd = new Product("mkt", "MKT SKU");
        mktProd.addBranding(new Branding(engProdId, brandedName, "OS"));
        Pool pool = TestUtil.createPool(mktProd);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd, prods,
            new PromotedContent(contentPathBuilder()), consumer, pool, new HashSet<>());

        assertEquals(1, certProds.size());
        assertEquals(brandedName, certProds.get(0).getBrandName());
        assertEquals("OS", certProds.get(0).getBrandType());
    }

    @Test
    public void shouldFilterContentWithCorrectArches() {
        String expectedArch = "ppc64";
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product product = new Product("mkt", "MKT SKU");
        addContent(product, "x86_64");
        addContent(product, "x86_64");
        addContent(product, expectedArch);
        addContent(product, expectedArch);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(Consumer.Facts.ARCHITECTURE, expectedArch);

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
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product product = new Product("mkt", "MKT SKU");
        product.setAttribute(Product.Attributes.ARCHITECTURE, "x86_64");
        addContent(product);
        addContent(product);
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(Consumer.Facts.ARCHITECTURE, expectedArch);

        Set<ProductContent> filteredContent = util.filterContentByContentArch(
            new HashSet<>(product.getProductContent()), consumer, product);

        assertEquals(0, filteredContent.size());
    }

    @Test
    public void shouldNOTFilterContentWithNoSpecifiedArches() {
        String expectedArch = "ppc64";
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product product = new Product("mkt", "MKT SKU");
        addContent(product); // add a content without arches specified
        Consumer consumer = new Consumer();
        consumer.setOwner(owner);
        consumer.setFact(Consumer.Facts.ARCHITECTURE, expectedArch);

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

        Content content = new Content("content_" + size)
            .setUuid("content_" + size)
            .setArches(arches);

        product.addContent(content, true);
    }

    @Test
    public void productWithMultipleBrandNames() {
        String engProdId = "1000";
        String brandedName = "Branded Eng Product";
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Product p = new Product(engProdId, "Eng Product 1000");
        p.setAttribute(Product.Attributes.BRANDING_TYPE, "OS");
        Set<Product> prods = Set.of(p);
        Product mktProd = new Product("mkt", "MKT SKU")
            .addBranding(new Branding(engProdId, brandedName, "OS"))
            .addBranding(new Branding(engProdId, "another brand name", "OS"))
            .addBranding(new Branding(engProdId, "number 3", "OS"));

        List<String> possibleBrandNames = mktProd.getBranding()
            .stream()
            .map(Branding::getName)
            .toList();

        Pool pool = TestUtil.createPool(mktProd);



        Consumer consumer = new Consumer();
        consumer.setOwner(owner);

        List<org.candlepin.model.dto.Product> certProds = util.createProducts(mktProd, prods,
            new PromotedContent(contentPathBuilder()), consumer, pool, new HashSet<>());

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
        Owner owner = new Owner()
            .setId("test-id")
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");
        Content content1 = new Content("cont_id1")
            .setArches("x86_64")
            .setContentUrl("/cont_id1");
        Content content2 = new Content("cont_id2")
            .setArches("ppc64")
            .setContentUrl("/cont_id2");
        Product engProd = new Product()
            .setId("content_access")
            .setName("Content Access");
        engProd.addContent(content1, true);
        engProd.addContent(content2, true);
        Product sku = new Product()
            .setId("content_access")
            .setName("Content Access");
        Pool pool = TestUtil.createPool(sku);
        Consumer consumer = new Consumer()
            .setOwner(owner)
            .setFact("uname.machine", "x86_64");

        org.candlepin.model.dto.Product certProds = util.mapProduct(engProd, sku,
            new PromotedContent(contentPathBuilder()), consumer, pool,
            Set.of("content_access"));

        assertEquals(1, certProds.getContent().size());
        assertEquals(1, certProds.getContent().get(0).getArches().size());
        assertEquals("x86_64", certProds.getContent().get(0).getArches().get(0));
    }

    private static ContentPathBuilder contentPathBuilder() {
        return ContentPathBuilder.from(new Owner(), List.of());
    }

}
