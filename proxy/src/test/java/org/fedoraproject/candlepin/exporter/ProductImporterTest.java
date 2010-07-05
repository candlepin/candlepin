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
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.junit.Before;
import org.junit.Test;

/**
 * ProductImporterTest
 */
public class ProductImporterTest {

    private ObjectMapper mapper;
    private ProductImporter importer;
    private ProductCurator mockedCurator;

    @Before
    public void setUp() throws IOException {
        mapper = ExportUtils.getObjectMapper();
        mockedCurator = mock(ProductCurator.class);
        importer = new ProductImporter(mockedCurator);
    }
    
    @Test
    public void testCreateObject() throws Exception {
        Product product = createTestProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);
        Product created = importer.createObject(mapper, reader);
        assertEquals(product.getId(), created.getId());
        assertEquals(product.getName(), created.getName());
        assertEquals(product.getAttributes(), created.getAttributes());
    }

    @Test
    public void testNewProductCreated() throws Exception {
        // TODO
    }

    @Test
    public void testExistingProductUpdated() throws Exception {
        Product product = createTestProduct();
        String json = getJsonForProduct(product);
        Reader reader = new StringReader(json);

        Product created = importer.createObject(mapper, reader);


        // Dummy up some changes to this product:
        String newProductName = "New Name";
        created.setName(newProductName);

        Set<Product> storeThese = new HashSet<Product>();
        storeThese.add(created);

        // Simulate the pre-existing product:
        when(mockedCurator.lookupById(product.getId())).thenReturn(product);

        importer.store(storeThese);

        verify(mockedCurator).createOrUpdate(created);
    }

    // TODO: test old products cleaned up

    // TODO: test old products not cleaned up if in use

    // TODO: test old products cleaned up if not in use *after* this import

    private Product createTestProduct() {
        Product p = new Product("testProductId", "Test Product");

        Attribute a1 = new Attribute("a1", "a1");
        a1.setId(new Long(1001)); // faked
        p.addAttribute(a1);

        Attribute a2 = new Attribute("a2", "a2");
        a2.setId(new Long(1002)); // faked
        p.addAttribute(a2);

        Attribute a3 = new Attribute("a3", "a3");
        a3.setId(new Long(1003)); // faked
        p.addAttribute(a3);

        p.setMultiplier(new Long(1));
        return p;
    }

    private String getJsonForProduct(Product p) throws Exception {
        Writer writer = new StringWriter();
        mapper.writeValue(writer, p);
        return writer.toString();
    }

}
