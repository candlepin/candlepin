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
package org.fedoraproject.candlepin.resource.test;


import java.util.ArrayList;
import java.util.HashSet;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.ProductResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;


/**
 * ProductResourceTest
 */
public class ProductResourceTest extends DatabaseTestFixture {
    
    private ProductResource productResource;
    
    @Before
    public void setUp() {
        
        productResource = injector.getInstance(ProductResource.class);
    }
    
    private Product createProduct() {
  //      String id = "test_product";
        String label = "test_product";
        String name = "Test Product";
        String variant = "server";
        String version = "1.0";
        String arch = "ALL";
        Long  hash = Math.abs(Long.valueOf(label.hashCode()));
        String type = "SVC";
        HashSet<Product> childProducts = null;
        Product prod = new Product(label, name, variant,
                version, arch, hash, type, childProducts);
        return prod;
        
    }

   
    @Test
    public void testCreateProductResource() {
        
        Product toSubmit = createProduct();
        productResource.createProduct(toSubmit, new ArrayList<String>());
        
        
    }
    
}
