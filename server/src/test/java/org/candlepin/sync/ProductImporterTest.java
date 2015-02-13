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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        Product product = TestUtil.createProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        assertEquals(product.getId(), created.getId());
        assertEquals(product.getName(), created.getName());
        assertEquals(product.getAttributes(), created.getAttributes());
    }

    @Test
    public void testNewProductCreated() throws Exception {
        Product product = TestUtil.createProduct();

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        when(productCuratorMock.lookupById(product.getId())).thenReturn(null);
        importer.store(storeThese);
        verify(productCuratorMock).createOrUpdate(created);
    }

    @Test
    public void testExistingProductUpdated() throws Exception {
        Product product = TestUtil.createProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);

        Product created = importer.createObject(mapper, reader);

        // Dummy up some changes to this product:
        String newProductName = "New Name";
        created.setName(newProductName);

        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);

        // Simulate the pre-existing product:
        when(productCuratorMock.lookupById(product.getId())).thenReturn(product);

        importer.store(storeThese);

        verify(productCuratorMock).createOrUpdate(created);
    }

    @Test
    public void testContentCreated() throws Exception {
        Product product = TestUtil.createProduct();
        addContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Content c = created.getProductContent().iterator().next().getContent();
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        importer.store(storeThese);

        verify(contentCuratorMock).createOrUpdate(c);

        assertEquals(new Long(1000), c.getMetadataExpire());
    }

    @Test
    public void testExistingProductContentAdded() throws Exception {
        Owner owner = new Owner("Test Corporation");
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);

        addContentTo(newProduct);
        Content c = newProduct.getProductContent().iterator().next().getContent();

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(newProduct);

        importer.store(storeThese);

        verify(productCuratorMock).createOrUpdate(newProduct);
        verify(contentCuratorMock).createOrUpdate(c);
    }

    @Test
    public void testGetChangedProductsNoNewProducts() {
        Owner owner = new Owner("Test Corporation");
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Set<Product> products = new HashSet<Product>();

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(0)).lookupById(oldProduct.getId());

        assertTrue(changed.isEmpty());
    }

    @Test
    public void testGetChangedProductsAllBrandNew() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(newProduct.getId())).thenReturn(null);

        Set<Product> changed = importer.getChangedProducts(products);

        assertTrue(changed.isEmpty());
    }

    @Test
    public void testGetChangedProductsAllIdentical() {
        Owner owner = new Owner("Test Corporation");
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Set<Product> products = new HashSet<Product>();
        products.add(oldProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertTrue(changed.isEmpty());
    }

    @Test
    public void testGetChangedProductsNameChanged() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name new", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsMultiplierChanged() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        oldProduct.setMultiplier(1L);
        newProduct.setMultiplier(2L);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeAdded() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        newProduct.setAttribute("fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeRemoved() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        oldProduct.setAttribute("fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeModified() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        oldProduct.setAttribute("fake attr", "value");
        newProduct.setAttribute("fake attr", "value new");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeSwapped() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        oldProduct.setAttribute("fake attr", "value");
        newProduct.setAttribute("other fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentAdded() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Content content = new Content();

        newProduct.addContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentRemoved() {
        Owner owner = new Owner("Test Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Content content = new Content();

        oldProduct.addContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentSwapped() {
        Owner owner = new Owner("Example-Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Content content = new Content(owner, "foobar", null, null, null, null, null, null, null);
        Content content2 = new Content(owner, "baz", null, null, null, null, null, null, null);

        oldProduct.addContent(content);
        newProduct.addContent(content2);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentEnabledToggled() {
        Owner owner = new Owner("Example-Corporation");
        Product newProduct = TestUtil.createProduct("fake id", "fake name", owner);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", owner);

        Content content = new Content(owner, "foobar", null, null, null, null, null, null, null);

        oldProduct.addContent(content);
        newProduct.addEnabledContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(oldProduct.getId())).thenReturn(oldProduct);

        Set<Product> changed = importer.getChangedProducts(products);

        verify(productCuratorMock, times(1)).lookupById(oldProduct.getId());

        assertEquals(1, changed.size());
    }

    @Test
    public void testVendorSetToUnknown() throws Exception {
        Product product = TestUtil.createProduct();
        addNoVendorContentTo(product);

        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        Content c = created.getProductContent().iterator().next().getContent();
        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);
        importer.store(storeThese);

        verify(contentCuratorMock).createOrUpdate(c);

        assertEquals("unknown", c.getVendor());
    }

    // Returns the Content object added
    private void addContentTo(Product p) {
        Owner owner = new Owner("Example-Corporation");
        Content c = new Content(owner, "name", "100130", "label", "type",
            "vendor", "url", "gpgurl", "arch");
        c.setMetadataExpire(1000L);
        p.getProductContent().add(new ProductContent(p, c, true));
    }

    // Returns the Content object added without vendor
    private void addNoVendorContentTo(Product p) {
        Owner owner = new Owner("Example-Corporation");
        Content c = new Content(owner, "name", "100130", "label", "type",
            "", "url", "gpgurl", "arch");
        c.setMetadataExpire(1000L);
        p.getProductContent().add(new ProductContent(p, c, true));
    }

    private String getJsonForProduct(Product p) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, p);
        return writer.toString();
    }

}
