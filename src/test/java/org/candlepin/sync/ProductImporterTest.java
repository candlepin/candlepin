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
package org.candlepin.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.MapConfiguration;
import org.candlepin.dto.manifest.v1.BrandingDTO;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


/**
 * ProductImporterTest
 */
public class ProductImporterTest {

    private I18n i18n;
    private Configuration config;
    private File tmpdir;
    private ObjectMapper mapper;
    private ProductImporter importer;
    private Owner owner = new Owner("Test Corporation");

    @Before
    public void setUp() throws IOException {
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

        this.config = new CandlepinCommonTestConfig();
        this.config.setProperty(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

        this.mapper = new SyncUtils(new MapConfiguration(this.config)).getObjectMapper();

        this.tmpdir = Files.createTempDirectory("product_importer_test").toFile();
        this.tmpdir.deleteOnExit();

        importer = new ProductImporter();
    }

    @Test
    public void testCreateObject() throws Exception {
        Product product = TestUtil.createProduct();
        product.setAttribute("a1", "a1");
        product.setAttribute("a2", "a2");

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        ProductDTO created = importer.createObject(mapper, reader, owner);
        assertEquals(product.getUuid(), created.getUuid());
        assertEquals(product.getName(), created.getName());
        assertEquals(product.getAttributes(), created.getAttributes());
    }

    @Test
    public void testNewProductCreated() throws Exception {
        ProductDTO product = new ProductDTO();
        product.setId("test-id");
        product.setName("test-name");
        product.setAttribute("attr1", "val1");
        product.setAttribute("attr2", "val2");
        product.setMultiplier(1L);
        Set<String> dependentProdIDs = new HashSet<>();
        dependentProdIDs.add("g23gh23h2");
        dependentProdIDs.add("353g823h");
        product.setDependentProductIds(dependentProdIDs);
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);

        ProductDTO created = importer.createObject(mapper, reader, owner);

        assertEquals(product, created);
    }

    @Test
    public void testContentCreated() throws Exception {
        Product product = TestUtil.createProduct();
        addContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        ProductDTO created = importer.createObject(mapper, reader, owner);
        ContentDTO c = created.getProductContent().iterator().next().getContent();

        // Metadata expiry should be overridden to 0 on import:
        assertEquals(new Long(1), c.getMetadataExpiration());
    }

    @Test
    public void testVendorSetToUnknown() throws Exception {
        Product product = TestUtil.createProduct();
        addNoVendorContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        ProductDTO created = importer.createObject(mapper, reader, owner);
        ContentDTO c = created.getProductContent().iterator().next().getContent();
        assertEquals("unknown", c.getVendor());
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

        String json = getJsonForProduct(productToImport);
        Reader reader = new StringReader(json);
        ProductDTO importedProduct = importer.createObject(this.mapper, reader, this.owner);

        assertNotNull(importedProduct);
        for (ProductDTO.ProductContentDTO content : importedProduct.getProductContent()) {
            Assertions.assertEquals(1L, content.getContent().getMetadataExpiration());
        }
        for (ProductDTO importedProvided : importedProduct.getProvidedProducts()) {
            for (ProductDTO.ProductContentDTO content : importedProvided.getProductContent()) {
                Assertions.assertEquals(1L, content.getContent().getMetadataExpiration());
            }
        }
        for (ProductDTO.ProductContentDTO content : importedProduct.getDerivedProduct().getProductContent()) {
            Assertions.assertEquals(1L, content.getContent().getMetadataExpiration());
        }
    }

    private ProductImporter buildProductImporter() {
        return new ProductImporter();
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

        Collection<ProductDTO.ProductContentDTO> productContent = input.getProductContent();
        if (productContent != null) {
            for (ProductDTO.ProductContentDTO pcdto : productContent) {
                ContentDTO cdto = this.normalizeContentDTO(pcdto.getContent());
                normalized.addContent(cdto, pcdto.isEnabled());
            }
        }

        return normalized;
    }

    // Returns the Content object added
    private Content addContentTo(Product product) {
        Content content = TestUtil.createContent("100130", "content_name");
        content.setMetadataExpiration(1000L);

        product.addContent(content, true);

        return content;
    }

    // Returns the Content object added without vendor
    private Content addNoVendorContentTo(Product product) {
        Content content = TestUtil.createContent("100130", "name");
        content.setVendor("");
        content.setMetadataExpiration(1000L);

        product.addContent(content, true);

        return content;
    }

    private String getJsonForProduct(Product product) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, product);
        return writer.toString();
    }

    private String getJsonForProduct(ProductDTO product) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, product);
        return writer.toString();
    }
}
