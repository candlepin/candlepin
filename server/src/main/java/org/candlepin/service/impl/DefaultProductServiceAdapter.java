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
package org.candlepin.service.impl;

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of the ProductserviceAdapter.
 */
public class DefaultProductServiceAdapter implements ProductServiceAdapter {

    private static Logger log =
        LoggerFactory.getLogger(DefaultProductServiceAdapter.class);

    private ProductCurator prodCurator;
    private ContentCurator contentCurator;

    // for product cert storage/generation - not sure if this should go in
    // a separate service?
    private ProductCertificateCurator prodCertCurator;
    private UniqueIdGenerator idGenerator;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator prodCurator,
        ProductCertificateCurator prodCertCurator, ContentCurator contentCurator,
        UniqueIdGenerator idGenerator) {

        this.prodCurator = prodCurator;
        this.prodCertCurator = prodCertCurator;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
    }

    @Override
    public Product getProductById(Owner owner, String id) {
        return prodCurator.lookupById(owner, id);
    }

    @Override
    public List<Product> getProducts() {
        return prodCurator.listAll();
    }

    @Override
    public Product createProduct(Product product) {
        // TODO: This may not actually work properly with the change from getId to getProductId
        if (prodCurator.find(product.getId()) != null) {
            throw new BadRequestException("product with ID " + product.getId() + " already exists");
        }
        else {
            if (product.getId() == null || product.getId().trim().equals("")) {
                product.setId(idGenerator.generateId());
            }
            Product newProduct = prodCurator.create(product);
            return newProduct;
        }
    }

    @Override
    public void deleteProduct(Product product) {
        // clean up any product certificates
        ProductCertificate cert = prodCertCurator.findForProduct(product);
        if (cert != null) {
            prodCertCurator.delete(cert);
        }
        prodCurator.delete(product);
    }

    @Override
    public ProductCertificate getProductCertificate(Product product) {
        return this.prodCertCurator.getCertForProduct(product);
    }



    @Override
    public void purgeCache(Collection<String> cachedKeys) {
        // noop
    }

    @Override
    public void removeContent(Owner owner, String productId, String contentId) {
        Product product = prodCurator.lookupById(owner, productId);
        Content content = contentCurator.lookupById(owner, contentId);
        prodCurator.removeProductContent(product, content);
    }

    @Override
    public Product mergeProduct(Product prod) {
        return prodCurator.merge(prod);
    }

    public boolean productHasSubscriptions(Product prod) {
        return prodCurator.productHasSubscriptions(prod);
    }

    @Override
    public List<Product> getProductsByIds(Owner owner, Collection<String> ids) {
        return prodCurator.listAllByIds(owner, ids);
    }

    @Override
    public Set<String> getProductsWithContent(Owner owner, Collection<String> contentIds) {
        HashSet<String> productIds = new HashSet<String>();

        for (Product product : prodCurator.getProductsWithContent(owner, contentIds)) {
            productIds.add(product.getId());
        }

        return productIds;
    }

}
