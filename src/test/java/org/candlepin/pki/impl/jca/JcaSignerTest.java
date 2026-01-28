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

package org.candlepin.pki.impl.jca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.candlepin.pki.Scheme;
import org.candlepin.test.CertificateReaderForTesting;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.cert.CertificateException;



class JcaSignerTest {
    private static final String SIGNATURE_SCHEME_NAME = "rsa";
    private static final String KEY_ALGORITHM = "rsa";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private byte[] expectedSignature;
    private JcaSigner signer;
    private CertificateReaderForTesting certificateAuthority;

    @BeforeEach
    public void setUp() throws CertificateException, IOException, KeyException {
        this.expectedSignature = IOUtils.toByteArray(
            this.getClass().getClassLoader().getResourceAsStream("certs/signature"));

        this.certificateAuthority = new CertificateReaderForTesting();
        this.signer = new JcaSigner(certificateAuthority);
    }

    @Test
    public void testGetSignatureScheme() {
        Scheme actual = this.signer.getSignatureScheme();

        assertThat(actual)
            .isNotNull()
            .returns(SIGNATURE_SCHEME_NAME, Scheme::name)
            .returns(KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(this.certificateAuthority.getCACert(), Scheme::certificate);
    }

    @Test
    public void shouldCalculateSignature() {
        ByteArrayInputStream input = new ByteArrayInputStream(
            "Hello, World!".getBytes(StandardCharsets.UTF_8));

        byte[] signature = this.signer.sign(input);

        assertArrayEquals(this.expectedSignature, signature);
    }

}
