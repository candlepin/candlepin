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
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyException;
import java.security.cert.CertificateException;

// TODO: FIXME: this test suite needs a lot of work:
// - there aren't many tests here; we need more testing around signing to ensure multi-scheme support and
//   all public-facing methods are working properly
// - the test should be made generic, moved to an abstract test suite, with this class simply providing the
//   BC implementation to test without providing any additional tests for the interface methods

public class JcaSignerTest {
    private Scheme scheme;
    private JcaSigner signer;

    @BeforeEach
    public void setUp() throws CertificateException, KeyException {
        // Temporary. We need to be testing all supported schemes, not just RSA.
        this.scheme = CryptoUtil.generateRsaScheme();
        this.signer = new JcaSigner(CryptoUtil.getSecurityProvider(), scheme);
    }

    @Test
    public void testGetCryptoScheme() {
        Scheme actual = this.signer.getCryptoScheme();
        Scheme expected = this.scheme;

        assertThat(actual)
            .returns(expected.name(), Scheme::name)
            .returns(expected.privateKey(), Scheme::privateKey)
            .returns(expected.certificate(), Scheme::certificate)
            .returns(expected.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(expected.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(expected.keySize(), Scheme::keySize);
    }

    @Test
    public void shouldCalculateSignature() throws Exception {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        byte[] expected = CryptoUtil.sign(data, this.scheme);

        byte[] signature = this.signer.sign(new ByteArrayInputStream(data));
        assertArrayEquals(expected, signature);
    }

}
