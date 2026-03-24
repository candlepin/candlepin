/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.Signer;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SignatureFileExporterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private CryptoManager cryptoManager;

    @BeforeEach
    public void beforeEach() {
        DevConfig config = TestConfig.defaults();
        this.cryptoManager = CryptoUtil.getCryptoManager(config);
    }

    @Test
    public void testExportWithNullOutputStream() {
        SignatureFileExporter exporter = new SignatureFileExporter(MAPPER);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        byte[] signature = this.getSignature(cryptoManager, scheme);

        assertThrows(IllegalArgumentException.class, () -> {
            exporter.export(null, scheme, signature);
        });
    }

    @Test
    public void testExportWithNullScheme() {
        SignatureFileExporter exporter = new SignatureFileExporter(MAPPER);
        ZipOutputStream outputStream = mock(ZipOutputStream.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        byte[] signature = this.getSignature(cryptoManager, scheme);

        assertThrows(IllegalArgumentException.class, () -> {
            exporter.export(outputStream, null, signature);
        });
    }

    @Test
    public void testExportWithNullSignature() {
        SignatureFileExporter exporter = new SignatureFileExporter(MAPPER);
        ZipOutputStream outputStream = mock(ZipOutputStream.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();

        assertThrows(IllegalArgumentException.class, () -> {
            exporter.export(outputStream, scheme, null);
        });
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testExport(Scheme scheme) throws Exception {
        byte[] signature = this.getSignature(cryptoManager, scheme);
        File signatureFile = File.createTempFile("temp", ".zip");
        signatureFile.deleteOnExit();

        SignatureFileExporter exporter = new SignatureFileExporter(MAPPER);
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(signatureFile))) {
            exporter.export(outputStream, scheme, signature);
        }

        SignatureFile expectedSignatureFile = SignatureFile.from(scheme, signature);
        JsonNode expected = MAPPER.valueToTree(expectedSignatureFile);

        this.assertSignatureFile(expected, signatureFile);
    }

    @Test
    public void testExportWithSerializationError() throws Exception {
        Scheme scheme = this.cryptoManager.getDefaultCryptoScheme();
        byte[] signature = this.getSignature(cryptoManager, scheme);
        File signatureFile = File.createTempFile("temp", ".zip");
        signatureFile.deleteOnExit();

        ObjectMapper mockMapper = mock(ObjectMapper.class);
        doThrow(JacksonException.class).when(mockMapper).writeValueAsString(any(SignatureFile.class));

        SignatureFileExporter exporter = new SignatureFileExporter(mockMapper);
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(signatureFile))) {
            assertThrows(IOException.class, () -> exporter.export(outputStream, scheme, signature));
        }
    }

    private void assertSignatureFile(JsonNode expected, File zipFile) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(SignatureFile.FILENAME);
            assertNotNull(entry, SignatureFile.FILENAME + " does not exist in zip file");

            try (InputStream istream = zip.getInputStream(entry)) {
                String actualJson = new String(istream.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode actualNode = MAPPER.readTree(actualJson);

                assertThat(actualNode)
                    .isEqualTo(expected);
            }
        }
    }

    private byte[] getSignature(CryptoManager cryptoManager, Scheme scheme) {
        Signer signer = cryptoManager.getSigner(scheme);
        String data = TestUtil.randomString("data-");

        return signer.sign(data.getBytes());
    }

}
