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


import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;


/**
 * ProductCertificateCurator
 */
@Component
public class ProductCertificateCurator extends AbstractHibernateCurator<ProductCertificate> {
    private static Logger log = LoggerFactory.getLogger(ProductCertificateCurator.class);

    private PKIUtility pki;
    private X509ExtensionUtil extensionUtil;


    @Autowired
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
     * Gets the certificate that defines the given product, creating one if necessary.
     *
     * @param product
     * @return the stored or created {@link ProductCertificate}
     */
    public ProductCertificate getCertForProduct(Product product) {
        log.debug("Retrieving cert for product: {}", product);
        ProductCertificate cert = this.findForProduct(product);

        if (cert == null) {
            // TODO: Do something better with these exceptions!
            try {
                cert = this.createCertForProduct(product);
                this.create(cert);
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

    private ProductCertificate createCertForProduct(Product product)
        throws GeneralSecurityException, IOException {
        log.debug("Generating cert for product: {}", product);

        KeyPair keyPair = this.pki.generateNewKeyPair();
        Set<X509ExtensionWrapper> extensions = this.extensionUtil.productExtensions(product);

        // TODO: Should this use the RH product ID, or the object's UUID?
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
