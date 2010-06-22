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
package org.fedoraproject.candlepin.service.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCertificate;
import org.fedoraproject.candlepin.model.ProductCertificateCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;

/**
 * Default implementation of the ProductserviceAdapter.
 */
public class DefaultProductServiceAdapter implements ProductServiceAdapter {

    private static Logger log = Logger
        .getLogger(DefaultProductServiceAdapter.class);

    private ProductCurator prodCurator;

    // for product cert storage/generation - not sure if this should go in
    // a separate service?
    private ProductCertificateCurator prodCertCurator;
    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;

    @Inject
    public DefaultProductServiceAdapter(ProductCurator prodCurator,
        ProductCertificateCurator prodCertCurator, PKIUtility pki,
        X509ExtensionUtil extensionUtil) {

        this.prodCurator = prodCurator;
        this.prodCertCurator = prodCertCurator;
        this.pki = pki;
        this.extensionUtil = extensionUtil;
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

    // TODO: Looks like this needs to change, there should probably be an error
    // thrown if you try to create a product that already exists, not a silent return.
    // This may have been done for the tests, so those may need to be modified to only
    // create the products if they do not exist.
    @Override
    public Product createProduct(Product product) {
        if ((prodCurator.find(product.getId()) == null)) {
            Product newProduct = prodCurator.create(product);
            return newProduct;
        }
        return prodCurator.find(product.getId());
    }

    @Override
    public HashMap<String, String> getProductNamesByProductId(String[] ids) {
        HashMap<String, String> names = new HashMap<String, String>();
        for (String id : ids) {
            Product p = getProductById(id);
            if (p != null) {
                names.put(id, p.getName());
            }
            else {
                names.put(id, null);
            }
        }
        return names;
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

        Set<X509ExtensionWrapper> extensions = 
            this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode())
            .abs();

        Calendar future = Calendar.getInstance();
        future.add(Calendar.YEAR, 10);

        X509Certificate x509Cert = this.pki.createX509Certificate("CN=" +
            product.getId(), extensions, new Date(), future.getTime(), keyPair,
            serial);

        ProductCertificate cert = new ProductCertificate();
        cert.setKeyAsBytes(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pki.getPemEncoded(x509Cert));
        cert.setProduct(product);

        return cert;
    }

}
