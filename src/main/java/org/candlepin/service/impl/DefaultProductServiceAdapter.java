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

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.model.ProductCurator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.UniqueIdGenerator;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
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
    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;
    private UniqueIdGenerator idGenerator;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator prodCurator,
        ProductCertificateCurator prodCertCurator, PKIUtility pki,
        X509ExtensionUtil extensionUtil, ContentCurator contentCurator,
        UniqueIdGenerator idGenerator) {

        this.prodCurator = prodCurator;
        this.prodCertCurator = prodCertCurator;
        this.pki = pki;
        this.extensionUtil = extensionUtil;
        this.contentCurator = contentCurator;
        this.idGenerator = idGenerator;
    }

    @Override
    public Product getProductById(String id) {
        log.debug("called getProductById");
        return prodCurator.lookupById(id);
    }

    @Override
    public List<Product> getProducts() {
        return prodCurator.listAll();
    }

    @Override
    public Product createProduct(Product product) {
        if (prodCurator.find(product.getId()) != null) {
            throw new BadRequestException("product with ID " + product.getId() +
                " already exists");
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
        ProductCertificate cert = this.prodCertCurator.findForProduct(product);

        if (cert == null) {
            // TODO: Do something better with these exceptions!
            try {
                cert = createForProduct(product);
                this.prodCertCurator.create(cert);
            }
            catch (GeneralSecurityException e) {
                log.error("Error creating product certificate!", e);
            }
            catch (IOException e) {
                log.error("Error creating product certificate!", e);
            }
        }

        return cert;
    }

    private ProductCertificate createForProduct(Product product)
        throws GeneralSecurityException, IOException {

        KeyPair keyPair = pki.generateNewKeyPair();

        Set<X509ExtensionWrapper> extensions = this.extensionUtil
            .productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode())
            .abs();

        Calendar future = Calendar.getInstance();
        future.add(Calendar.YEAR, 10);

        X509Certificate x509Cert = this.pki.createX509Certificate("CN=" +
            product.getId(), extensions, null, new Date(), future.getTime(), keyPair,
            serial, null);

        ProductCertificate cert = new ProductCertificate();
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pki.getPemEncoded(x509Cert));
        cert.setProduct(product);

        return cert;
    }

    @Override
    public void purgeCache() {

    }

    @Override
    public void removeContent(String productId, String contentId) {
        Product product = prodCurator.find(productId);
        Content content = contentCurator.find(contentId);
        prodCurator.removeProductContent(product, content);
    }

    @Override
    public Product mergeProduct(Product prod) {
        return prodCurator.merge(prod);
    }

    public boolean productHasSubscriptions(Product prod) {
        return prodCurator.productHasSubscriptions(prod);
    }

    public void addRely(String productId, String relyId) {
        Product product = prodCurator.find(productId);
        prodCurator.addRely(product, relyId);
    }

    public Set<String> getReliesOn(String productId) {
        Product product = prodCurator.find(productId);
        return product.getReliesOn();
    }

    public void removeRely(String productId, String relyId) {
        Product product = prodCurator.find(productId);
        prodCurator.removeRely(product, relyId);
    }

    @Override
    public List<Product> getProductsByIds(Collection<String> ids) {
        return prodCurator.listAllByIds(ids);
    }

    @Override
    public Set<String> getProductsWithContent(Collection<String> contentIds) {
        return new HashSet<String>(prodCurator.getProductIdsWithContent(contentIds));
    }
}
