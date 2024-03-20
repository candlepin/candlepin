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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCertificate;
import org.candlepin.model.ProductCertificateCurator;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.impl.BouncyCastleKeyPairGenerator;
import org.candlepin.pki.impl.BouncyCastlePemEncoder;
import org.candlepin.pki.impl.BouncyCastleSecurityProvider;
import org.candlepin.pki.impl.BouncyCastleSubjectKeyIdentifierWriter;
import org.candlepin.test.CertificateReaderForTesting;
import org.candlepin.util.X509ExtensionUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.cert.CertificateException;

class ProductCertificateGeneratorTest {
    private ProductCertificateCurator productCertificateCurator;
    private X509CertificateBuilder certificateBuilder;
    private ProductCertificateGenerator productCertificateGenerator;

    @BeforeEach
    public void init() throws CertificateException, IOException {
        BouncyCastleSecurityProvider securityProvider = new BouncyCastleSecurityProvider();
        X509ExtensionUtil extensionUtil = mock(X509ExtensionUtil.class);
        KeyPairGenerator keyPairGenerator = new BouncyCastleKeyPairGenerator(
            securityProvider, mock(KeyPairDataCurator.class));
        PemEncoder pemEncoder = new BouncyCastlePemEncoder();
        this.productCertificateCurator = mock(ProductCertificateCurator.class);
        this.certificateBuilder = new X509CertificateBuilder(new CertificateReaderForTesting(),
            securityProvider, new BouncyCastleSubjectKeyIdentifierWriter());
        this.productCertificateGenerator = new ProductCertificateGenerator(
            this.productCertificateCurator,
            extensionUtil,
            keyPairGenerator,
            pemEncoder,
            () -> this.certificateBuilder
        );
    }

    @Test
    void shouldHandleNullGracefully() {
        assertNull(this.productCertificateGenerator.generate(null));
    }

    @Test
    void shouldCacheProductCertificates() {
        when(this.productCertificateCurator.findForProduct(any(Product.class)))
            .thenReturn(mock(ProductCertificate.class));
        Product product = new Product();

        ProductCertificate certificate = this.productCertificateGenerator.generate(product);

        assertNotNull(certificate);
        verify(this.productCertificateCurator).findForProduct(any(Product.class));
        verifyNoMoreInteractions(this.productCertificateCurator);
    }

    @Test
    void shouldCreateMissingProductCertificates() {
        Product product = new Product().setId("123");

        ProductCertificate certificate = this.productCertificateGenerator.generate(product);

        assertNotNull(certificate);
        verify(this.productCertificateCurator).create(any(ProductCertificate.class));
    }
}
