/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

package org.candlepin.pki.certs;

import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.pki.impl.KeyPairGenerator;
import org.candlepin.util.X509ExtensionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

public class ProductCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(ProductCertificateGenerator.class);

    private final X509ExtensionUtil extensionUtil;
    private final ProductCertificateCurator productCertificateCurator;
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;
    private final Provider<X509CertificateBuilder> certificateBuilder;

    @Inject
    public ProductCertificateGenerator(
        ProductCertificateCurator productCertificateCurator,
        KeyPairGenerator keyPairGenerator,
        PemEncoder pemEncoder,
        X509ExtensionUtil extensionUtil,
        Provider<X509CertificateBuilder> certificateBuilder) {
        this.productCertificateCurator = Objects.requireNonNull(productCertificateCurator);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
        this.certificateBuilder = Objects.requireNonNull(certificateBuilder);
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
        ProductCertificate cert = this.productCertificateCurator.findForProduct(product);

        if (cert == null) {
            try {
                cert = this.createCertForProduct(product);
                this.productCertificateCurator.create(cert);
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

    private ProductCertificate createCertForProduct(Product product) {
        log.debug("Generating cert for product: {}", product);

        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();
        Set<X509Extension> extensions = this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();

        Calendar future = Calendar.getInstance();
        future.add(Calendar.YEAR, 10);
        Instant from = Instant.now();
        Instant to = OffsetDateTime.now(ZoneOffset.UTC).plusYears(10).toInstant();

        DistinguishedName dn = new DistinguishedName(product.getId());
        X509Certificate x509Certificate = this.certificateBuilder.get()
            .withDN(dn)
            .withValidity(from, to)
            .withKeyPair(keyPair)
            .withSerial(serial)
            .withExtensions(extensions)
            .build();

        ProductCertificate certificate = new ProductCertificate();
        certificate.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        certificate.setCertAsBytes(this.pemEncoder.encodeAsBytes(x509Certificate));
        certificate.setProduct(product);

        return certificate;
    }

}
