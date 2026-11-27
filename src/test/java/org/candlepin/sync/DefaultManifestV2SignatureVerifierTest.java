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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.TestConfig;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.test.CryptoUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class DefaultManifestV2SignatureVerifierTest {
    @TempDir
    private Path directory;
    private ObjectMapper mapper;
    private CryptoManager cryptoManager;
    private DefaultManifestV2SignatureVerifier verifier;
    private I18n i18n;
    private Scheme scheme;

    @BeforeEach
    void setup() {
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        this.cryptoManager = CryptoUtil.getCryptoManager();
        this.scheme = this.cryptoManager.getDefaultCryptoScheme();
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.verifier = new DefaultManifestV2SignatureVerifier(this.mapper, this.cryptoManager,
            CryptoUtil.getCertificateReader(), this.i18n);
    }

    @ParameterizedTest
    @MethodSource("schemes")
    void verifiesOriginalSignedBytesWithoutReadingUnsignedScheme(Scheme scheme) throws Exception {
        this.scheme = scheme;
        Path archive = this.signedArchive(this.payload());
        Files.writeString(this.directory.resolve("scheme.json"), "invalid unsigned scheme");

        assertDoesNotThrow(() -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
    }

    private static Stream<Scheme> schemes() {
        return CryptoUtil.SUPPORTED_SCHEMES.values().stream();
    }

    @Test
    void rejectsPayloadMixedWithUnsignedEnvelopeEntries() throws Exception {
        Path archive = this.signedArchive(this.payload());
        Files.write(this.directory.resolve("consumer_export.zip"), this.payload());
        assertThrows(SyncDataFormatException.class,
            () -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
    }

    @Test
    void rejectsTamperingUnlessSignatureConflictIsExplicitlyOverridden() throws Exception {
        byte[] payload = this.payload();
        byte[] signature = this.cryptoManager.getSigner(this.scheme).sign(payload);
        byte[] changed = this.zip(Map.of("scheme.json", this.mapper.writeValueAsBytes(SchemeFile.from(this.scheme)),
            "meta.json", new byte[] { 1 }));
        Path archive = this.archive(Map.of("consumer_export.zip", changed, "signature", signature));

        assertThrows(ImportConflictException.class,
            () -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
        assertDoesNotThrow(() -> this.verifier.verify(archive, this.directory,
            new ConflictOverrides(Importer.Conflict.SIGNATURE_CONFLICT)));
    }

    @Test
    void trustOverrideDoesNotBypassSignatureValidation() throws Exception {
        CryptoManager manager = mock(CryptoManager.class);
        SignatureValidator validator = mock(SignatureValidator.class);
        when(manager.getSignatureValidator(any())).thenReturn(validator);
        when(validator.forSignature(any())).thenReturn(validator);
        DefaultManifestV2SignatureVerifier untrusted = new DefaultManifestV2SignatureVerifier(this.mapper,
            manager, CryptoUtil.getCertificateReader(), this.i18n);
        Path archive = this.signedArchive(this.payload());

        assertThrows(ImportConflictException.class,
            () -> untrusted.verify(archive, this.directory, new ConflictOverrides()));
        assertThrows(ImportConflictException.class, () -> untrusted.verify(archive, this.directory,
            new ConflictOverrides(Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE)));
        when(validator.validate(any(byte[].class))).thenReturn(true);
        assertDoesNotThrow(() -> untrusted.verify(archive, this.directory,
            new ConflictOverrides(Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"signature", "consumer_export.zip"})
    void rejectsMissingOrEmptySigningMaterialEvenWithOverrides(String missing) throws Exception {
        byte[] payload = this.payload();
        Map<String, byte[]> entries = new java.util.HashMap<>(Map.of("consumer_export.zip", payload,
            "signature", this.cryptoManager.getSigner(this.scheme).sign(payload)));
        entries.remove(missing);
        Path archive = this.archive(entries);
        ConflictOverrides overrides = new ConflictOverrides(Importer.Conflict.SIGNATURE_CONFLICT,
            Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE);
        assertThrows(ImporterException.class, () -> this.verifier.verify(archive, this.directory, overrides));
        entries.put(missing, new byte[0]);
        this.archive(entries);
        assertThrows(ImporterException.class, () -> this.verifier.verify(archive, this.directory, overrides));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "invalid json", "{\"certificate\":\"not base64\"}"})
    void rejectsMalformedScheme(String metadata) throws Exception {
        Path archive = this.signedArchive(this.zip(Map.of("scheme.json", metadata.getBytes(
            java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(ImporterException.class,
            () -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
    }

    @Test
    void rejectsUnsignedPayloadAndMissingScheme() throws Exception {
        Path archive = this.archive(Map.of("meta.json", new byte[] { 1 }));
        assertThrows(ImporterException.class,
            () -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
        this.signedArchive(this.zip(Map.of("meta.json", new byte[] { 1 })));
        assertThrows(ImporterException.class,
            () -> this.verifier.verify(archive, this.directory, new ConflictOverrides()));
    }

    @Test
    void rejectsNullArgumentsAndDependencies() {
        assertThrows(NullPointerException.class,
            () -> this.verifier.verify(null, this.directory, new ConflictOverrides()));
        assertThrows(NullPointerException.class,
            () -> this.verifier.verify(this.directory, null, new ConflictOverrides()));
        assertThrows(NullPointerException.class, () -> this.verifier.verify(this.directory, this.directory, null));
        assertThrows(NullPointerException.class,
            () -> new DefaultManifestV2SignatureVerifier(null, this.cryptoManager,
                CryptoUtil.getCertificateReader(), this.i18n));
    }

    private byte[] payload() throws Exception {
        return this.zip(Map.of("scheme.json", this.mapper.writeValueAsBytes(SchemeFile.from(this.scheme)),
            "meta.json", new byte[] { 0 }));
    }

    private Path signedArchive(byte[] payload) throws Exception {
        return this.archive(Map.of("consumer_export.zip", payload,
            "signature", this.cryptoManager.getSigner(this.scheme).sign(payload)));
    }

    private Path archive(Map<String, byte[]> entries) throws Exception {
        return Files.write(this.directory.resolve("archive.zip"), this.zip(entries));
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
