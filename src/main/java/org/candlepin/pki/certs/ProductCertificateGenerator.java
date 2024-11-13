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
package org.candlepin.pki.certs;

import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.X509Extension;
import org.candlepin.util.X509ExtensionUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * This generator is responsible for generation and caching of product certificates.
 */
@Singleton
public class ProductCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(ProductCertificateGenerator.class);

    private final ProductCertificateCurator productCertificateCurator;
    private final X509ExtensionUtil extensionUtil;
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;
    private final Provider<X509CertificateBuilder> certificateBuilder;

    @Inject
    public ProductCertificateGenerator(ProductCertificateCurator productCertificateCurator,
        X509ExtensionUtil extensionUtil, KeyPairGenerator keyPairGenerator, PemEncoder pemEncoder,
        Provider<X509CertificateBuilder> certificateBuilder) {
        this.extensionUtil = Objects.requireNonNull(extensionUtil);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.productCertificateCurator = Objects.requireNonNull(productCertificateCurator);
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
    public ProductCertificate generate(Product product) {
        if (product == null) {
            log.warn("Cannot generate certificate for null product!");
            return null;
        }

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
                throw new CertificateCreationException("Unable to generate product certificate", e);
            }
        }

        return cert;
    }

    private ProductCertificate createCertForProduct(Product product) {
        log.debug("Generating cert for product: {}", product);

        KeyPair keyPair = this.keyPairGenerator.generateKeyPair();
        Set<X509Extension> extensions = this.extensionUtil.productExtensions(product);

        BigInteger serial = BigInteger.valueOf(product.getId().hashCode()).abs();

        Instant from = Instant.now();
        Instant to = OffsetDateTime.now(ZoneOffset.UTC).plusYears(10).toInstant();

        DistinguishedName dn = new DistinguishedName(product.getId());
        X509Certificate x509Cert = this.certificateBuilder.get()
            .withDN(dn)
            .withSerial(serial)
            .withValidity(from, to)
            .withKeyPair(keyPair)
            .withExtensions(extensions)
            .build();

        ProductCertificate cert = new ProductCertificate();
        cert.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keyPair.getPrivate()));
        cert.setCertAsBytes(this.pemEncoder.encodeAsBytes(x509Cert));
        cert.setProduct(product);

        return cert;
    }

}
