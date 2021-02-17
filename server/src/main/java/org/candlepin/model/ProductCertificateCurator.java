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
package org.candlepin.model;

import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.X509ExtensionWrapper;
import org.candlepin.util.X509ExtensionUtil;

import com.google.inject.Inject;

import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;

import javax.inject.Singleton;



/**
 * ProductCertificateCurator
 */
@Singleton
public class ProductCertificateCurator extends AbstractHibernateCurator<ProductCertificate> {
    private static Logger log = LoggerFactory.getLogger(ProductCertificateCurator.class);

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;


    @Inject
    public ProductCertificateCurator(PKIUtility pki, X509ExtensionUtil extensionUtil) {
        super(ProductCertificate.class);

        this.pki = pki;
        this.extensionUtil = extensionUtil;
    }

    public ProductCertificate findForProduct(Product product) {
        log.debug("Finding cert for product: {}", product);

        return (ProductCertificate) currentSession()
            .createCriteria(ProductCertificate.class)
            .add(Restrictions.eq("product", product))
            .uniqueResult();
    }

    /**
     * Gets the certificate that defines the given product, creating one if necessary. If the
     * product is not one that has certificates, this method returns null. If a certificate does
     * not yet exist and cannot be created, this method throws an exception.
     *
     * @param product
     *  the product for which to fetch the certificate
     *
     * @return
     *  the stored or created {@link ProductCertificate}, or null if the product cannot have
     *  certificates
     */
    public ProductCertificate getCertForProduct(Product product) {
        log.debug("Retrieving cert for product: {}", product);
        ProductCertificate cert = this.findForProduct(product);

        if (cert == null) {
            try {
                cert = this.createCertForProduct(product);
                this.create(cert);
            }
            catch (IllegalArgumentException e) {
                // This occurs if the product is not an engineering product, fails the cert
                // encoding (as marketing products have non-numeric IDs), and fails out with
                // an IAE.
                log.warn("Attempted to create a product certificate for a non-engineering product: {}",
                    product);
            }
            catch (Exception e) {
                log.error("Error creating product certificate!", e);
                throw new RuntimeException("Unable to generate product certificate", e);
            }
        }

        return cert;
    }

    private ProductCertificate createCertForProduct(Product product)
        throws GeneralSecurityException, IOException {
        log.debug("Generating cert for product: {}", product);

        KeyPair keyPair = this.pki.generateNewKeyPair();
        Set<X509ExtensionWrapper> extensions = this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();

        Calendar future = Calendar.getInstance();
        future.add(Calendar.YEAR, 10);

        X509Certificate x509Cert = this.pki.createX509Certificate(
            "CN=" + product.getId(), extensions, null, new Date(), future.getTime(), keyPair,
            serial, null
        );

        ProductCertificate cert = new ProductCertificate();
        cert.setKeyAsBytes(this.pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pki.getPemEncoded(x509Cert));
        cert.setProduct(product);

        return cert;
    }

}
