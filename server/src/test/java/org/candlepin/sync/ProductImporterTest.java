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
import static org.mockito.Mockito.*;

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
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
        mapper = SyncUtils.getObjectMapper(new MapConfiguration(
            new HashMap<String, String>() {
                {
                    put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES, "false");
                }
            }
        ));

        importer = new ProductImporter();
    }

    @Test
    public void testCreateObject() throws Exception {
        Product product = TestUtil.createProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader, owner);
        assertEquals(product.getUuid(), created.getUuid());
        assertEquals(product.getName(), created.getName());
        assertEquals(product.getAttributes(), created.getAttributes());
    }

    @Test
    public void testNewProductCreated() throws Exception {
        Product product = TestUtil.createProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);

        Product created = importer.createObject(mapper, reader, owner);

        assertEquals(product, created);
    }

    @Test
    public void testContentCreated() throws Exception {
        Product product = TestUtil.createProduct();
        addContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader, owner);
        Content c = created.getProductContent().iterator().next().getContent();

        // Metadata expiry should be overridden to 0 on import:
        assertEquals(new Long(1), c.getMetadataExpire());
    }

    @Test
    public void testVendorSetToUnknown() throws Exception {
        Product product = TestUtil.createProduct();
        addNoVendorContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader, owner);
        Content c = created.getProductContent().iterator().next().getContent();
        assertEquals("unknown", c.getVendor());
    }

    // Returns the Content object added
    private Content addContentTo(Product product) {
        Content content = TestUtil.createContent("100130", "content_name");
        content.setMetadataExpire(1000L);

        product.getProductContent().add(new ProductContent(product, content, true));

        return content;
    }

    // Returns the Content object added without vendor
    private Content addNoVendorContentTo(Product product) {
        Content content = TestUtil.createContent("100130", "name");
        content.setVendor("");
        content.setMetadataExpire(1000L);

        product.getProductContent().add(new ProductContent(product, content, true));

        return content;
    }

    private String getJsonForProduct(Product product) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, product);
        return writer.toString();
    }

}
