/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.exporter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductImporterTest
 */
public class ProductImporterTest {

    private ObjectMapper mapper;
    private ProductImporter importer;
    private ProductCurator productCuratorMock;
    private ContentCurator contentCuratorMock;
    private SubscriptionCurator subCuratorMock;
    private PoolCurator poolCuratorMock;

    @Before
    public void setUp() throws IOException {
        mapper = ExportUtils.getObjectMapper();
        productCuratorMock = mock(ProductCurator.class);
        contentCuratorMock = mock(ContentCurator.class);
        subCuratorMock = mock(SubscriptionCurator.class);
        poolCuratorMock = mock(PoolCurator.class);
        importer = new ProductImporter(productCuratorMock, contentCuratorMock,
            poolCuratorMock, subCuratorMock);
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
        when(subCuratorMock.listByProduct(product)).thenReturn(
            new LinkedList<Subscription>());

        importer.store(storeThese);

        verify(productCuratorMock).createOrUpdate(created);
    }
    
    @Test
    public void testUnusedProductsCleanedUp() throws Exception {

        Product inUse = TestUtil.createProduct();
        inUse.getSubscriptions().add(TestUtil.createSubscription(inUse));

        Product notInUse = TestUtil.createProduct();

        // Create the list of all products our mocked curator will return:
        List<Product> allProducts = new LinkedList<Product>();
        allProducts.add(inUse);
        allProducts.add(notInUse);

        when(productCuratorMock.listAll()).thenReturn(allProducts);
        importer.cleanupUnusedProductsAndContent();
        verify(productCuratorMock).delete(notInUse);
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
    }

    // Returns the Content object added
    private void addContentTo(Product p) {
        Content c = new Content("name", new Long(100130), "label", "type",
            "vendor", "url", "gpgurl");
        p.getProductContent().add(new ProductContent(p, c, true));
    }

    private String getJsonForProduct(Product p) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, p);
        return writer.toString();
    }

}
