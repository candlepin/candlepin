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

import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.certs.X509CertificateBuilder;
import org.candlepin.test.CertificateReaderForTesting;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.util.io.pem.PemGenerationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

class BouncyCastlePemEncoderTest {

    @Test
    void shouldEncodeKey() throws NoSuchAlgorithmException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();

        String result = encoder.encodeAsString(keyPair.getPrivate());

        assertThat(result)
            .startsWith("-----BEGIN RSA PRIVATE KEY-----")
            .endsWith("-----END RSA PRIVATE KEY-----\n");
    }

    @Test
    void shouldEncodeCert() throws NoSuchAlgorithmException, CertificateException, IOException {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();
        KeyPair keyPair = createKeyPair();
        BouncyCastleSecurityProvider provider = new BouncyCastleSecurityProvider();
        CertificateReaderForTesting certificateReader = new CertificateReaderForTesting();
        X509CertificateBuilder builder = new X509CertificateBuilder(certificateReader, provider);
        X509Certificate certificate = builder
            .withValidity(Instant.now(), Instant.now())
            .withRandomSerial()
            .withDN(new DistinguishedName("asd123"))
            .withKeyPair(keyPair)
            .build();

        String result = encoder.encodeAsString(certificate);

        assertThat(result)
            .startsWith("-----BEGIN CERTIFICATE-----")
            .endsWith("-----END CERTIFICATE-----\n");
    }

    @Test
    void shouldFailForInvalidObjects() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        assertThatThrownBy(() -> encoder.encodeAsBytes("Invalid"))
            .isInstanceOf(PemEncodingException.class);
        assertThatThrownBy(() -> encoder.encodeAsString("Invalid"))
            .isInstanceOf(PemEncodingException.class);
    }

    @Test
    void shouldFailForInvalidNulls() {
        BouncyCastlePemEncoder encoder = new BouncyCastlePemEncoder();

        assertThatThrownBy(() -> encoder.encodeAsBytes(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encoder.encodeAsString(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        return generator.generateKeyPair();
    }

}
