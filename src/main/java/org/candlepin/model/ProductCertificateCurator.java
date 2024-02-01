/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.util.X509ExtensionUtil;

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
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * ProductCertificateCurator
 */
@Singleton
public class ProductCertificateCurator extends AbstractHibernateCurator<ProductCertificate> {
    private static final Logger log = LoggerFactory.getLogger(ProductCertificateCurator.class);

    private final PKIUtility pki;
    private final X509ExtensionUtil extensionUtil;
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;

    @Inject
    public ProductCertificateCurator(PKIUtility pki, X509ExtensionUtil extensionUtil,
        KeyPairGenerator keyPairGenerator, PemEncoder pemEncoder) {
        super(ProductCertificate.class);

        this.pki = Objects.requireNonNull(pki);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
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
                    product, e);
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

        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();
        Set<X509Extension> extensions = this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();

        Calendar future = Calendar.getInstance();
        future.add(Calendar.YEAR, 10);

        DistinguishedName dn = new DistinguishedName(product.getId());
        X509Certificate x509Cert = this.pki.createX509Certificate(
            dn, extensions, new Date(), future.getTime(), keyPair,
            serial, null
        );

        ProductCertificate cert = new ProductCertificate();
        cert.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pemEncoder.encodeAsBytes(x509Cert));
        cert.setProduct(product);

        return cert;
    }

}
