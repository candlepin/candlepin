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
package org.candlepin.pki.impl.bc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;

import java.security.KeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

// TODO: This test should be made generic and not written targeting a specific provider. Instead, the tests
// should be only targeting the base interface (PemEncoder), with the provider-specific subclasses of the
// test suite providing the provider-specific PemEncoder implementation opaquely.

class BouncyCastlePemEncoderTest {

    @Test
    void shouldEncodeKeyAsBytes() throws Exception {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();

        String encoded = encoder.encodeAsString(keyPair.getPrivate());
        byte[] encodedBytes = encoder.encodeAsBytes(keyPair.getPrivate());

        assertThat(new String(encodedBytes))
            .isEqualTo(encoded);
    }

    @Test
    void shouldEncodeKeyAsString() throws Exception {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();

        String encodedKey = encoder.encodeAsString(keyPair.getPrivate());

        assertThat(encodedKey)
            .startsWith("-----BEGIN PRIVATE KEY-----")
            .endsWith("-----END PRIVATE KEY-----\n");
    }

    @Test
    void shouldEncodeCertAsBytes() throws Exception {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        X509Certificate certificate = createCertificate();

        // FIXME: This test is not validating correct certificate encoding. It should be updated to not rely
        // on the UUT for its authoritative/expected result.
        String encoded = encoder.encodeAsString(certificate);
        byte[] encodedBytes = encoder.encodeAsBytes(certificate);

        assertThat(new String(encodedBytes))
            .isEqualTo(encoded);
    }

    @Test
    void shouldEncodeCertAsString() throws Exception {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        X509Certificate certificate = createCertificate();

        // FIXME: This test does not validate the encoding at all, only that it has a header and footer that
        // a PEM-encoded object would have. The test should also verify that we can support encoding the
        // cert regardless of the crypto scheme used to generate it.

        String result = encoder.encodeAsString(certificate);

        assertThat(result)
            .startsWith("-----BEGIN CERTIFICATE-----")
            .endsWith("-----END CERTIFICATE-----\n");
    }

    @Test
    void shouldFailForInvalidNulls() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        // FIXME: Break these tests up into individual tests so their spec is properly captured

        assertThatThrownBy(() -> encoder.encodeAsBytes((X509Certificate) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsBytes((PrivateKey) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString((X509Certificate) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString((PrivateKey) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private X509Certificate createCertificate() throws KeyException, CertificateException {
        Scheme scheme = CryptoUtil.generateRsaScheme();
        return CryptoUtil.generateX509Certificate(scheme);
    }

    private KeyPair createKeyPair() throws KeyException {
        return CryptoUtil.generateKeyPair("RSA", 4096);
    }

}
