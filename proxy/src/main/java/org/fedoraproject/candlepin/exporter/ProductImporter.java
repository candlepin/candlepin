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

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.ProductCurator;

/**
 * ProductImporter
 */
public class ProductImporter implements EntityImporter<Product> {

    private ProductCurator curator;
    private ContentCurator contentCurator;

    public ProductImporter(ProductCurator curator, ContentCurator contentCurator) {
        this.curator = curator;
        this.contentCurator = contentCurator;
    }

    public Product createObject(ObjectMapper mapper, Reader reader) throws IOException {
        Product p = mapper.readValue(reader, Product.class);
        
        // Make sure the ID's are null, otherwise Hibernate thinks these are detached
        // entities.
        for (ProductAttribute a : p.getAttributes()) {
            a.setId(null);
        }
        
        // TODO: test product content doesn't dangle
        
        return p;
    }

    public void store(Set<Product> products) {
        for (Product p : products) {
            // Handling the storing/updating of Content here. This is technically a 
            // disjoint entity, but really only makes sense in the concept of products.
            // Downside, if multiple products reference the same content, it will be
            // updated multiple times during the import.
            for (ProductContent content : p.getProductContent()) {
                contentCurator.createOrUpdate(content.getContent());
            }
            
            curator.createOrUpdate(p);
        }
    }
}
