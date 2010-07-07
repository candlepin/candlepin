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
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.ContentCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;

/**
 * ProductImporter
 */
public class ProductImporter implements EntityImporter<Product> {

    private static Logger log = Logger.getLogger(ProductImporter.class);

    private ProductCurator curator;
    private ContentCurator contentCurator;
    private SubscriptionCurator subCurator;
    private PoolCurator poolCurator;

    public ProductImporter(ProductCurator curator, ContentCurator contentCurator,
        PoolCurator poolCurator, SubscriptionCurator subCurator) {
        this.curator = curator;
        this.contentCurator = contentCurator;
        this.poolCurator = poolCurator;
        this.subCurator = subCurator;
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

    public void cleanupUnusedProductsAndContent() {
        log.info("Cleaning up unused products:");
        List<Product> allProducts = curator.listAll();
        for (Product p : allProducts) {

            // Check for subscriptions for this product, or which provide this product:
            if (p.getSubscriptions().size() == 0 &&
                (subCurator.listByProduct(p).size() == 0)) {
                log.info("Removing unused product: " + p);
                curator.delete(p);
            }
        }
    }
}
