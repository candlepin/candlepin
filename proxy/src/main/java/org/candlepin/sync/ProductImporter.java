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
package org.candlepin.sync;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.codehaus.jackson.map.ObjectMapper;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.util.Util;

import com.google.common.collect.Sets;
import org.candlepin.controller.PoolManager;

/**
 * ProductImporter
 */
public class ProductImporter {

    private ProductCurator curator;
    private ContentCurator contentCurator;
    private PoolManager poolManager;

    public ProductImporter(ProductCurator curator, ContentCurator contentCurator,
        PoolManager poolManager) {
        this.curator = curator;
        this.contentCurator = contentCurator;
        this.poolManager = poolManager;
    }

    public Product createObject(ObjectMapper mapper, Reader reader)
        throws IOException {
        final Product importedProduct = mapper.readValue(reader, Product.class);
        // Make sure the ID's are null, otherwise Hibernate thinks these are
        // detached
        // entities.
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
        //have to maintain a map because entitlements don't
        //override equals/hashcode and only way to maintain unique
        //entitlements is to have a key -> value right now.
        Map<String, Entitlement> toRegenEntitlements = Util.newMap();
        for (Product importedProduct : products) {
            final Product existingProduct = this.curator.find(importedProduct
                .getId());

            if (hasProductChanged(existingProduct, importedProduct)) {
                for (Pool pool : this.poolManager
                    .getListOfEntitlementPoolsForProduct(importedProduct.getId())) {
                    for (Entitlement e : pool.getEntitlements()) {
                        if (!toRegenEntitlements.containsKey(e.getId())) {
                            toRegenEntitlements.put(e.getId(), e);
                        }
                    }
                }
            }
            // Handling the storing/updating of Content here. This is
            // technically a
            // disjoint entity, but really only makes sense in the concept of
            // products.
            // Downside, if multiple products reference the same content, it
            // will be
            // updated multiple times during the import.
            for (ProductContent content : importedProduct.getProductContent()) {
                contentCurator.createOrUpdate(content.getContent());
            }


            curator.createOrUpdate(importedProduct);
        }

      //regenerate entitlement certificates.
        this.poolManager.regenerateCertificatesOf(toRegenEntitlements.values());
    }

    protected final boolean hasProductChanged(Product existingProd, Product importedProd) {

        if (existingProd == null) {
            return true;
        }
        return Sets.difference(existingProd.getProductContent(),
                    importedProd.getProductContent()).size() > 0 ||
                Sets.difference(existingProd.getAttributes(),
                        importedProd.getAttributes()).size() > 0 ||
                !new EqualsBuilder()
                        .append(existingProd.getName(), importedProd.getName())
                        .append(existingProd.getMultiplier(), importedProd.getMultiplier())
                        .isEquals();
    }

}
