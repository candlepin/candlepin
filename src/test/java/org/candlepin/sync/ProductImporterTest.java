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
package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.ProductDTO.ProductContentDTO;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ProductImporterTest {

    private I18n i18n;
    private DevConfig config;
    private File tmpdir;
    private ObjectMapper mapper;

    @BeforeEach
    public void setup() throws Exception {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

        this.tmpdir = Files.createTempDirectory("product_importer_test").toFile();
        this.tmpdir.deleteOnExit();

        this.mapper = ObjectMapperFactory.getSyncObjectMapper(config);
    }

    @Test
    public void testImportProduct() throws Exception {
        ProductDTO pdto1 = this.generateProductDTO(3, 3);
        ProductDTO pdto2 = this.generateProductDTO(3, 3);
        ProductDTO pdto3 = this.generateProductDTO(3, 3);

        this.writeMockProductData(pdto1, pdto2, pdto3);

        ProductImporter importer = this.buildProductImporter();

        ProductDTO output = importer.importProduct(pdto2.getId());

        assertNotNull(output);
        assertEquals(this.normalizeProductDTO(pdto2), output);
    }

    @Test
    public void testImportProductResolvesProvidedProductsFavoringManifest() throws Exception {
        ProductDTO child1a = this.generateProductDTO(3, 0)
            .setId("child_prod");
        ProductDTO child1b = this.generateProductDTO(3, 0)
            .setId("child_prod");
        ProductDTO child2 = this.generateProductDTO(3, 0);
        ProductDTO child3 = this.generateProductDTO(3, 0);

        ProductDTO product = this.generateProductDTO(0, 3)
            .setProvidedProducts(Set.of(child1a, child2, child3));

        // We've attached child1a to the product, but we'll write child1b to the manifest data.
        // Our expectation is that child1b is what we pull from the importer
        this.writeMockProductData(child1b, child2, child3, product);
        Map<String, ProductDTO> expectedChildren = Stream.of(child1b, child2, child3)
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        ProductImporter importer = this.buildProductImporter();
        ProductDTO output = importer.importProduct(product.getId());
        assertNotNull(output);
        assertNotNull(output.getProvidedProducts());

        for (ProductDTO child : output.getProvidedProducts()) {
            ProductDTO expected = expectedChildren.get(child.getId());

            assertNotNull(expected);
            assertEquals(this.normalizeProductDTO(expected), child);
        }
    }

    @Test
    public void testImportProductResolvesDerivedProductFavoringManifest() throws Exception {
        ProductDTO child1a = this.generateProductDTO(3, 0)
            .setId("child_prod");
        ProductDTO child1b = this.generateProductDTO(3, 0)
            .setId("child_prod");

        ProductDTO product = this.generateProductDTO(0, 3)
            .setDerivedProduct(child1a);

        // We've attached child1a to the product, but we'll write child1b to the manifest data.
        // Our expectation is that child1b is what we pull from the importer
        this.writeMockProductData(child1b, product);

        ProductImporter importer = this.buildProductImporter();
        ProductDTO output = importer.importProduct(product.getId());
        assertNotNull(output);

        ProductDTO outputDerived = output.getDerivedProduct();
        assertNotNull(outputDerived);
        assertNotEquals(this.normalizeProductDTO(child1a), outputDerived);
        assertEquals(this.normalizeProductDTO(child1b), outputDerived);
    }

    @Test
    public void testImportProductResolvesDerivedProvidedProductsFavoringManifest() throws Exception {
        ProductDTO child1a = this.generateProductDTO(3, 0)
            .setId("child_prod");
        ProductDTO child1b = this.generateProductDTO(3, 0)
            .setId("child_prod");
        ProductDTO child2 = this.generateProductDTO(3, 0);
        ProductDTO child3 = this.generateProductDTO(3, 0);

        ProductDTO derived = this.generateProductDTO(0, 0)
            .setProvidedProducts(Set.of(child1a, child2, child3));

        ProductDTO product = this.generateProductDTO(0, 0)
            .setDerivedProduct(derived);

        // We've attached child1a to the product, but we'll write child1b to the manifest data.
        // Our expectation is that child1b is what we pull from the importer
        this.writeMockProductData(child1b, child2, child3, derived, product);
        Map<String, ProductDTO> expectedChildren = Stream.of(child1b, child2, child3)
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        ProductImporter importer = this.buildProductImporter();
        ProductDTO output = importer.importProduct(product.getId());
        assertNotNull(output);

        ProductDTO outputDerived = output.getDerivedProduct();
        assertNotNull(outputDerived);
        assertNotNull(outputDerived.getProvidedProducts());

        for (ProductDTO child : outputDerived.getProvidedProducts()) {
            ProductDTO expected = expectedChildren.get(child.getId());

            assertNotNull(expected);
            assertEquals(this.normalizeProductDTO(expected), child);
        }
    }

    @Test
    public void testImportProductThrowsExceptionOnInvalidProductId() throws Exception {
        ProductDTO pdto1 = this.generateProductDTO(3, 3);
        ProductDTO pdto2 = this.generateProductDTO(3, 3);
        ProductDTO pdto3 = this.generateProductDTO(3, 3);

        this.writeMockProductData(pdto1, pdto2, pdto3);
        ProductImporter importer = this.buildProductImporter();

        assertThrows(SyncDataFormatException.class, () -> importer.importProduct("invalid pid"));
    }

    /**
     * This test only exists to deal with the use case where a product in the manifest can reference
     * other products that themselves may not be defined in the manifest. If this case is no longer
     * valid, this test and all the code that supports it can be removed.
     */
    @Test
    public void testImportProductLeavesDanglingChildReferences() throws Exception {
        ProductDTO provided1 = this.generateProductDTO(3, 0);
        ProductDTO provided2 = this.generateProductDTO(3, 0);
        ProductDTO provided3 = this.generateProductDTO(3, 0);
        ProductDTO derivedProvided1 = this.generateProductDTO(3, 0);
        ProductDTO derivedProvided2 = this.generateProductDTO(3, 0);
        ProductDTO derivedProvided3 = this.generateProductDTO(3, 0);

        ProductDTO derived = this.generateProductDTO(0, 0)
            .setProvidedProducts(List.of(derivedProvided1, derivedProvided2, derivedProvided3));

        ProductDTO product = this.generateProductDTO(0, 0)
            .setProvidedProducts(List.of(provided1, provided2, provided3))
            .setDerivedProduct(derived);

        // We've written only the base product to the manifest data, but none of the children.
        // In this case, we should still have the full tree available from the base product,
        // but none of the children should be directly importable.
        this.writeMockProductData(product);
        ProductImporter importer = this.buildProductImporter();

        ProductDTO output = importer.importProduct(product.getId());
        assertNotNull(output);
        assertEquals(this.normalizeProductDTO(product), output);

        // The equals assertion above should cover these, but for clarity, we'll include basic
        // checks on the children
        assertNotNull(output.getProvidedProducts());
        assertEquals(3, output.getProvidedProducts().size());

        assertNotNull(output.getDerivedProduct());
        assertEquals(this.normalizeProductDTO(derived), output.getDerivedProduct());

        assertNotNull(output.getDerivedProduct().getProvidedProducts());
        assertEquals(3, output.getDerivedProduct().getProvidedProducts().size());

        // These should all fail as they are dangling references off the single exported product
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(provided1.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(provided2.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(provided3.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(derived.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(derivedProvided1.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(derivedProvided2.getId()));
        assertThrows(SyncDataFormatException.class, () -> importer.importProduct(derivedProvided3.getId()));
    }

    @Test
    public void testImportProductMap() throws Exception {
        ProductDTO pdto1 = this.generateProductDTO(3, 3);
        ProductDTO pdto2 = this.generateProductDTO(3, 3);
        ProductDTO pdto3 = this.generateProductDTO(3, 3);

        Map<String, ProductDTO> expected = Stream.of(pdto1, pdto2, pdto3)
            .map(this::normalizeProductDTO)
            .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

        this.writeMockProductData(pdto1, pdto2, pdto3);
        ProductImporter importer = this.buildProductImporter();

        Map<String, ProductDTO> output = importer.importProductMap();

        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testImportProductMapReturnsEmptyMapWithNoData() throws IOException {
        ProductImporter importer = this.buildProductImporter();

        Map<String, ProductDTO> output = importer.importProductMap();

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void importShouldSetMetadataExpireTo1() throws Exception {
        ProductDTO productToImport = this.generateProductDTO(3, 3);
        ProductDTO providedProduct = this.generateProductDTO(3, 3);
        ProductDTO derivedProduct = this.generateProductDTO(3, 3);
        productToImport.addProvidedProduct(providedProduct);
        productToImport.setDerivedProduct(derivedProduct);

        this.writeMockProductData(productToImport, providedProduct);

        ProductImporter importer = this.buildProductImporter();

        ProductDTO importedProduct = importer.importProduct(productToImport.getId());

        assertNotNull(importedProduct);
        for (ProductContentDTO content : importedProduct.getProductContent()) {
            assertEquals(1L, content.getContent().getMetadataExpiration());
        }
        for (ProductDTO importedProvided : importedProduct.getProvidedProducts()) {
            for (ProductContentDTO content : importedProvided.getProductContent()) {
                assertEquals(1L, content.getContent().getMetadataExpiration());
            }
        }
        for (ProductContentDTO content : importedProduct.getDerivedProduct().getProductContent()) {
            assertEquals(1L, content.getContent().getMetadataExpiration());
        }
    }

    private ProductImporter buildProductImporter() {
        return new ProductImporter(this.tmpdir, this.mapper, this.i18n);
    }

    private void writeMockProductData(ProductDTO... products) throws IOException {
        if (products == null) {
            return;
        }

        // Impl note: we're intentionally *not* collecting children products here so we can
        // easily test the case where a child product does not exist in the manifest

        for (ProductDTO pdto : products) {
            File pfile = new File(this.tmpdir, pdto.getId() + ProductImporter.PRODUCT_FILE_SUFFIX);
            pfile.deleteOnExit();

            try (FileWriter writer = new FileWriter(pfile)) {
                this.mapper.writeValue(writer, pdto);
            }
        }
    }

    private ProductDTO generateProductDTO(int content, int branding) {
        String suffix = TestUtil.randomString(8, TestUtil.CHARSET_NUMERIC);

        ProductDTO pdto = new ProductDTO()
            .setUuid(TestUtil.randomString(32, TestUtil.CHARSET_NUMERIC_HEX))
            .setId("test_prod-" + suffix)
            .setName("Test Prod " + suffix)
            .setMultiplier(5L)
            .setAttribute("attr1", "attr1val_" + suffix)
            .setAttribute("attr2", "attr2val_" + suffix)
            .setAttribute("attr3", "attr3val_" + suffix);

        for (int i = 0; i < content; ++i) {
            Set<String> requiredProductIds = Set.of("rpi1-" + suffix, "rpi2-" + suffix, "rpi3-" + suffix);
            String csuffix = TestUtil.randomString(8, TestUtil.CHARSET_NUMERIC);

            ContentDTO cdto = new ContentDTO()
                .setUuid(TestUtil.randomString(32, TestUtil.CHARSET_NUMERIC_HEX))
                .setId("test_cont-" + csuffix)
                .setType("test_type-" + csuffix)
                .setLabel("test_label-" + csuffix)
                .setName("Test Content " + csuffix)
                .setVendor("test_vendor-" + csuffix)
                .setContentUrl("test_content_url-" + csuffix)
                .setRequiredTags("test_tags-" + csuffix)
                .setReleaseVersion("release_ver-" + csuffix)
                .setArches("test_arches-" + csuffix)
                .setMetadataExpiration(50L)
                .setRequiredProductIds(requiredProductIds);

            pdto.addContent(cdto, true);
        }

        for (int i = 0; i < branding; ++i) {
            String bsuffix = TestUtil.randomString(8, TestUtil.CHARSET_NUMERIC);
            BrandingDTO bdto = new BrandingDTO()
                .setId("test_branding-" + bsuffix)
                .setProductId(pdto.getId())
                .setName("Test Branding " + bsuffix)
                .setType("test_brand_type-" + bsuffix);

            pdto.addBranding(bdto);
        }

        return pdto;
    }

    /**
     * Normalizes content data according to the logic expected from the importer's output. This
     * allows us to write simpler test cases using assert[Not]Equals rather than having to manually
     * test field-by-field.
     *
     * @param input
     *  the input content DTO to normalize
     *
     * @return
     *  a normalized copy of the input DTO
     */
    private ContentDTO normalizeContentDTO(ContentDTO input) {
        if (input == null) {
            return null;
        }

        ContentDTO normalized = new ContentDTO(input)
            .setUuid(null)
            .setMetadataExpiration(1L);

        if (normalized.getVendor() == null || normalized.getVendor().isEmpty()) {
            normalized.setVendor("unknown");
        }

        return normalized;
    }

    /**
     * Normalizes product and content data according to the logic expected from the importer's
     * output. This allows us to write simpler test cases using assert[Not]Equals rather than
     * having to manually test field-by-field.
     *
     * @param input
     *  the input product DTO to normalize
     *
     * @return
     *  a normalized copy of the input DTO
     */
    private ProductDTO normalizeProductDTO(ProductDTO input) {
        if (input == null) {
            return null;
        }

        // Impl note: We're doing our own deep copy here, so we don't need a deep copy to start with
        ProductDTO normalized = new ProductDTO(input)
            .setUuid(null)
            .setMultiplier(1L)
            .setProductContent(null)
            .setProvidedProducts(null)
            .setBranding(null); // apparently we @JsonIgnore branding on product...???

        normalized.setDerivedProduct(this.normalizeProductDTO(normalized.getDerivedProduct()));

        Collection<ProductDTO> providedProducts = input.getProvidedProducts();
        if (providedProducts != null) {
            providedProducts.forEach(ppdto -> normalized.addProvidedProduct(this.normalizeProductDTO(ppdto)));
        }

        Collection<ProductContentDTO> productContent = input.getProductContent();
        if (productContent != null) {
            for (ProductContentDTO pcdto : productContent) {
                ContentDTO cdto = this.normalizeContentDTO(pcdto.getContent());
                normalized.addContent(cdto, pcdto.isEnabled());
            }
        }

        return normalized;
    }

}
