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

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * ProductImporterTest
 */
public class ProductImporterTest {

    private ObjectMapper mapper;
    private ProductImporter importer;
    private Owner owner = new Owner("Test Corporation");

    @Before
    public void setUp() throws IOException {
        Map<String, String> configProps = new HashMap<>();
        configProps.put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");

        this.mapper = new SyncUtils(new MapConfiguration(configProps)).getObjectMapper();

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
