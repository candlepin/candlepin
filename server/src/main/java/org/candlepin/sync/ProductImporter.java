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

import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

/**
 * ProductImporter
 */
public class ProductImporter {

    private ProductCurator curator;
    private ContentCurator contentCurator;

    public ProductImporter(ProductCurator curator, ContentCurator contentCurator) {
        this.curator = curator;
        this.contentCurator = contentCurator;
    }

    public Product createObject(ObjectMapper mapper, Reader reader)
        throws IOException {
        final Product importedProduct = mapper.readValue(reader, Product.class);
        // Make sure the ID's are null, otherwise Hibernate thinks these are
        // detached entities.
        for (ProductAttribute a : importedProduct.getAttributes()) {
            a.setId(null);
        }

        // Multiplication has already happened on the upstream candlepin. set this to 1
        // so we can use multipliers on local products if necessary.
        importedProduct.setMultiplier(1L);

        // TODO: test product content doesn't dangle
        return importedProduct;
    }

    public void store(Set<Product> products) {
        Set<String> savedContents = new HashSet<String>();
        /*
         * Handling the storing/updating of Content here. This is technically a
         * disjoint entity, but really only makes sense in the concept of
         * products.
         * We cannot persist products in this loop due to 1363701 described below
         */
        for (Product importedProduct : products) {
            for (ProductContent content : importedProduct.getProductContent()) {
                if (savedContents.contains(content.getContent().getId())) {
                    continue;
                } else {
                    savedContents.add(content.getContent().getId());
                }
                // BZ 990113 error occurs because incoming content data has
                // no value for Vendor. Will place one to avoid DB issues.
                Content c = content.getContent();
                if (StringUtils.isBlank(c.getVendor())) {
                    c.setVendor("unknown");
                }

                /*
                 * On standalone servers we will set metadata expire to 1 second so
                 * clients an immediately get changes to content when published on the
                 * server. We would use 0, but the client plugin interprets this as unset
                 * and ignores it completely resulting in the default yum values being
                 * used.
                 *
                 * We know this is a standalone server due to the fact that import is
                 * being used, so there is no need to guard this behavior.
                 */
                c.setMetadataExpire(new Long(1));

                contentCurator.createOrUpdate(c);
            }
        }

        /*
         * 1363701: flushing before we update the products is crucial. older
         * versions of postgressql driver (postgresql-jdbc-9.2.1002) and
         * postgresql server (postgresql-server-9.2.15-1) seem to end up in
         * violating unique constraint rule of product constraint relationship,
         * as product content is added and then the duplicate is subsequently
         * removed ( in the wrong order ).
         * related:
         * https://vladmihalcea.com/2013/10/16/hibernate-facts-knowing-flush-operations-order-matters/
         * newer version of driver and server seem to not have this issue.
         */
        curator.flush();
        for (Product importedProduct : products) {
            curator.createOrUpdate(importedProduct);
        }
    }

    /**
     * Examine the list of products that are about to be imported, and return a set of them
     * that have been modified from their state in the db.
     *
     * Will not return brand new products.
     *
     * @param products The list of yet to be imported products
     * @return a set of all products that exist in the db, but will be changed
     */
    Set<Product> getChangedProducts(Set<Product> products) {
        Set<Product> toReturn = Util.newSet();

        for (Product product : products) {
            Product existing = curator.lookupById(product.getId());

            if (existing != null && hasProductChanged(existing, product)) {
                toReturn.add(product);
            }
        }

        return toReturn;
    }

    protected final boolean hasProductChanged(Product existingProd, Product importedProd) {
        // trying to go in order from least to most work.
        if (!existingProd.getName().equals(importedProd.getName())) {
            return true;
        }

        if (!existingProd.getMultiplier().equals(importedProd.getMultiplier())) {
            return true;
        }

        if (existingProd.getAttributes().size() != importedProd.getAttributes().size()) {
            return true;
        }
        if (Sets.intersection(existingProd.getAttributes(),
            importedProd.getAttributes()).size() != existingProd.getAttributes().size()) {
            return true;
        }

        if (existingProd.getProductContent().size() != importedProd.getProductContent().size()) {
            return true;
        }
        if (Sets.intersection(new HashSet<ProductContent>(existingProd.getProductContent()),
                new HashSet<ProductContent>(importedProd.getProductContent())).size() !=
                existingProd.getProductContent().size()) {
            return true;
        }

        return false;
    }
}
