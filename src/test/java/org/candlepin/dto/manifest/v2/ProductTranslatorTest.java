/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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


package org.candlepin.dto.manifest.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ProductTranslatorTest extends AbstractTranslatorTest<Product, ProductDTO, ProductTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Product.class, ProductDTO.class);
        modelTranslator.registerTranslator(new ContentTranslator(), Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(new BrandingTranslator(), Branding.class, BrandingDTO.class);
    }

    @Override
    protected ProductTranslator initObjectTranslator() {
        return new ProductTranslator();
    }

    @Override
    protected Product initSourceObject() {
        Product product = new Product("product-id", "product-name")
            .setAttributes(Map.of("arch", "x86_64"))
            .setDerivedProduct(new Product("derived-id", "derived-name"));
        product.setCreated(new Date(1000L));
        product.setUpdated(new Date(2000L));
        product.addContent(new Content("content-id").setName("content-name"), true);
        product.setBranding(List.of(new Branding("product-id", "branding-name", "branding-type")));
        Product provided = new Product("provided-id", "provided-name");
        provided.setCreated(new Date(3000L));
        provided.setUpdated(new Date(4000L));
        product.addProvidedProduct(provided);
        return product;
    }

    @Override
    protected ProductDTO initDestinationObject() {
        return new ProductDTO();
    }

    @Override
    protected void verifyOutput(Product source, ProductDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.getId(), destination.getId());
        assertEquals(source.getName(), destination.getName());
        assertEquals(source.getAttributes(), destination.getAttributes());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getUpdated(), destination.getUpdated());
        assertEquals("derived-id", destination.getDerivedProduct());
        assertEquals(Set.of("provided-id"), destination.getProvidedProducts());
        if (childrenGenerated) {
            assertEquals("content-name", destination.getProductContent().get("content-id").getName());
            assertEquals("branding-name", destination.getBranding().iterator().next().getName());
        }
        else {
            assertTrue(destination.getProductContent().isEmpty());
            assertTrue(destination.getBranding().isEmpty());
        }
    }

    @Test
    void testPopulateClearsNestedDataForEmptyProduct() {
        ProductDTO destination = this.translator.translate(this.modelTranslator, this.source);

        this.translator.populate(this.modelTranslator, new Product(), destination);

        assertNull(destination.getDerivedProduct());
        assertTrue(destination.getProvidedProducts().isEmpty());
        assertTrue(destination.getProductContent().isEmpty());
        assertTrue(destination.getBranding().isEmpty());
    }

    @Test
    void testTranslatesNamespacedProduct() {
        Product product = new Product("namespaced", "Namespaced").setNamespace("private");

        assertEquals("namespaced", this.translator.translate(this.modelTranslator, product).getId());
        assertEquals("namespaced", this.translator.translate(product).getId());
        assertEquals("namespaced", this.translator.populate(this.modelTranslator, product, this.dest).getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void testGlobalNamespacesAreAllowed(String namespace) {
        Product product = new Product("global", "Global").setNamespace(namespace);

        assertNotNull(this.translator.translate(this.modelTranslator, product));
    }

    @Test
    void testPreservesNamespacedRelationships() {
        Product provided = new Product("provided", "Provided");
        provided.addContent(new Content("global-content"), true);
        provided.addContent(new Content("private-content").setNamespace("private"), true);
        provided.addProvidedProduct(new Product("private-grandchild", "Private").setNamespace("private"));
        Product derived = new Product("derived", "Derived");
        derived.addProvidedProduct(provided);
        Product root = new Product("root", "Root").setDerivedProduct(derived);
        root.addProvidedProduct(provided);
        root.addProvidedProduct(new Product("private-child", "Private").setNamespace("private"));
        provided.setDerivedProduct(new Product("private-derived", "Private").setNamespace("private"));

        ProductDTO result = this.translator.translate(this.modelTranslator, root);
        assertEquals(Set.of("provided", "private-child"),
            result.getProvidedProducts());
        assertEquals("derived", result.getDerivedProduct());
        ProductDTO providedDto = this.translator.translate(this.modelTranslator, provided);
        assertEquals(Set.of("global-content", "private-content"),
            providedDto.getProductContent().keySet());
        assertEquals(Set.of("private-grandchild"), providedDto.getProvidedProducts());
        assertEquals("private-derived", providedDto.getDerivedProduct());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testContentEnabledComesFromProductAssociation(boolean enabled) {
        Content content = new Content("shared-content");
        Product first = new Product("first", "First");
        first.addContent(content, enabled);
        Product second = new Product("second", "Second");
        second.addContent(content, !enabled);

        ProductDTO firstDto = this.translator.translate(this.modelTranslator, first);
        ProductDTO secondDto = this.translator.translate(this.modelTranslator, second);

        assertEquals(enabled, firstDto.getProductContent().get(content.getId()).isEnabled());
        assertEquals(!enabled, secondDto.getProductContent().get(content.getId()).isEnabled());
    }

}
