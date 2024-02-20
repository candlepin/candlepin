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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.SignatureFailedException;
import org.candlepin.test.CertificateReaderForTesting;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;

class SignerTest {
    private byte[] expectedSignature;
    private Signer signer;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        this.expectedSignature = IOUtils.toByteArray(
            this.getClass().getClassLoader().getResourceAsStream("certs/signature"));
        this.signer = new Signer(new CertificateReaderForTesting());
    }

    @Test
    void shouldCalculateSignature() {
        ByteArrayInputStream input = new ByteArrayInputStream(
            "Hello, World!".getBytes(StandardCharsets.UTF_8));

        byte[] signature = this.signer.sign(input);

        assertArrayEquals(this.expectedSignature, signature);
    }

    @Test
    void shouldFailCertOperationsFail() {
        CertificateReader certificateReader = Mockito.mock(CertificateReader.class);
        Mockito.when(certificateReader.getCaKey()).thenThrow(RuntimeException.class);
        Signer signer = new Signer(certificateReader);
        ByteArrayInputStream input = new ByteArrayInputStream(
            "Hello, World!".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> signer.sign(input))
            .isInstanceOf(SignatureFailedException.class);
    }

    @Test
    void shouldVerifySignature() throws IOException {
        Path tempFile = Files.createTempFile("input", "txt");
        Files.writeString(tempFile, "Hello, World!");

        assertTrue(this.signer.verifySignature(tempFile.toFile(), this.expectedSignature));
    }
}
