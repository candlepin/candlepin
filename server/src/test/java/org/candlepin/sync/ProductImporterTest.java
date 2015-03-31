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

import static org.junit.Assert.*;
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
    private ProductCurator productCuratorMock;
    private ContentCurator contentCuratorMock;
    private Owner owner = new Owner("Test Corporation");

    @Before
    public void setUp() throws IOException {
        mapper = SyncUtils.getObjectMapper(new MapConfiguration(
                new HashMap<String, String>() {

                    {
                        put(ConfigProperties.FAIL_ON_UNKNOWN_IMPORT_PROPERTIES,
                                "false");
                    }
                }));
        productCuratorMock = mock(ProductCurator.class);
        contentCuratorMock = mock(ContentCurator.class);
        importer = new ProductImporter(productCuratorMock, contentCuratorMock);
    }

    @Test
    public void testCreateObject() throws Exception {
        Product product = TestUtil.createProduct(owner);
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        assertEquals(product.getUuid(), created.getUuid());
        assertEquals(product.getName(), created.getName());
        assertEquals(product.getAttributes(), created.getAttributes());
    }

    @Test
    public void testNewProductCreated() throws Exception {
        Product product = TestUtil.createProduct(owner);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        when(productCuratorMock.lookupById(product.getOwner(), product.getId())).thenReturn(null);
        importer.store(storeThese, owner);
    }

    @Test
    public void testExistingProductUpdated() throws Exception {
        Product product = TestUtil.createProduct(owner);
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);

        Product created = importer.createObject(mapper, reader);

        // Dummy up some changes to this product:
        String newProductName = "New Name";
        created.setName(newProductName);

        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);

        // Simulate the pre-existing product:
        when(productCuratorMock.lookupById(product.getOwner(), product.getId())).thenReturn(product);

        importer.store(storeThese, owner);

    }

    @Test
    public void testContentCreated() throws Exception {
        Product product = TestUtil.createProduct(owner);
        addContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Content c = created.getProductContent().iterator().next().getContent();
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        importer.store(storeThese, owner);

        verify(contentCuratorMock).createOrUpdate(c);

        // Metadata expiry should be overridden to 0 on import:
        assertEquals(new Long(1), c.getMetadataExpire());
    }

    @Test
    public void testExistingProductContentAdded() throws Exception {
        Owner owner = new Owner("Test Corporation");
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);

        addContentTo(newProduct);
        Content c = newProduct.getProductContent().iterator().next().getContent();

        when(productCuratorMock.find(oldProduct.getUuid()))
            .thenReturn(oldProduct);

        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(newProduct);

        importer.store(storeThese, owner);
    }

    @Test
    public void testVendorSetToUnknown() throws Exception {
        Product product = TestUtil.createProduct(owner);
        addNoVendorContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Content c = created.getProductContent().iterator().next().getContent();
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        importer.store(storeThese, owner);

        verify(contentCuratorMock).createOrUpdate(c);

        assertEquals("unknown", c.getVendor());
    }

    // Returns the Content object added
    private Content addContentTo(Product p) {
        Owner owner = new Owner("Example-Corporation");
        Content c = new Content(
            owner, "name", "100130", "label", "type", "vendor", "url", "gpgurl", "arch"
        );

        c.setMetadataExpire(1000L);
        p.getProductContent().add(new ProductContent(p, c, true));

        return c;
    }

    // Returns the Content object added without vendor
    private void addNoVendorContentTo(Product p) {
        Owner owner = new Owner("Example-Corporation");
        Content c = new Content(
            owner, "name", "100130", "label", "type", "", "url", "gpgurl", "arch"
        );

        c.setMetadataExpire(1000L);
        p.getProductContent().add(new ProductContent(p, c, true));
    }

    private String getJsonForProduct(Product p) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, p);
        return writer.toString();
    }

}
