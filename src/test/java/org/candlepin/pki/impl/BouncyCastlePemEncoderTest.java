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

package org.candlepin.pki.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.candlepin.config.TestConfig;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.test.CertificateReaderForTesting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

class BouncyCastlePemEncoderTest {

    @Test
    void shouldEncodeKeyAsBytes() throws NoSuchAlgorithmException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();

        String encoded = encoder.encodeAsString(keyPair.getPrivate());
        byte[] encodedBytes = encoder.encodeAsBytes(keyPair.getPrivate());

        assertThat(new String(encodedBytes))
            .isEqualTo(encoded);
    }

    @Test
    void shouldEncodeKeyAsString() throws NoSuchAlgorithmException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();

        String encodedKey = encoder.encodeAsString(keyPair.getPrivate());

        assertThat(encodedKey)
            .startsWith("-----BEGIN PRIVATE KEY-----")
            .endsWith("-----END PRIVATE KEY-----\n");
    }

    @Test
    void shouldEncodeCertAsBytes() throws IOException, GeneralSecurityException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        X509Certificate certificate = createCertificate();

        String encoded = encoder.encodeAsString(certificate);
        byte[] encodedBytes = encoder.encodeAsBytes(certificate);

        assertThat(new String(encodedBytes))
            .isEqualTo(encoded);
    }

    @Test
    void shouldEncodeCertAsString() throws GeneralSecurityException, IOException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        X509Certificate certificate = createCertificate();

        String result = encoder.encodeAsString(certificate);

        assertThat(result)
            .startsWith("-----BEGIN CERTIFICATE-----")
            .endsWith("-----END CERTIFICATE-----\n");
    }

    @Test
    void shouldFailForInvalidNulls() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        assertThatThrownBy(() -> encoder.encodeAsBytes((X509Certificate) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsBytes((PrivateKey) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString((X509Certificate) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString((PrivateKey) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private X509Certificate createCertificate() throws GeneralSecurityException, IOException {
        KeyPair keyPair = createKeyPair();
        BouncyCastleSecurityProvider provider = new BouncyCastleSecurityProvider();
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        SubjectKeyIdentifierWriter subjectKeyIdentifierWriter = new BouncyCastleSubjectKeyIdentifierWriter();
        KeyPairDataCurator keypairDataCurator = mock(KeyPairDataCurator.class);
        PKIUtility pki = new BouncyCastlePKIUtility(provider, certificateReader,
            subjectKeyIdentifierWriter, TestConfig.defaults(), keypairDataCurator);

        return pki.createX509Certificate(new DistinguishedName("test_name"), null, null, new Date(),
            new Date(), keyPair, BigInteger.valueOf(1999L), "altName");
    }

    private KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        return generator.generateKeyPair();
    }

}
