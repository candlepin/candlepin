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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.TestConfig;
import org.candlepin.dto.manifest.v2.CertificateDTO;
import org.candlepin.dto.manifest.v2.CertificateSerialDTO;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Locale;

class DefaultManifestV2ScaCertificateReaderTest {
    @TempDir
    private Path directory;
    private ObjectMapper mapper;
    private DefaultManifestV2ScaCertificateReader reader;
    private CertificateDTO certificate;
    private I18n i18n;

    @BeforeEach
    void setup() throws Exception {
        this.mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        this.i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.reader = new DefaultManifestV2ScaCertificateReader(this.mapper, this.i18n);
        Scheme scheme = CryptoUtil.getCryptoManager().getDefaultCryptoScheme();
        this.certificate = new CertificateDTO()
            .setId("sca-certificate")
            .setCertificate(CryptoUtil.getPemEncoder().encodeAsString(scheme.certificate()))
            .setKey(CryptoUtil.getPemEncoder().encodeAsString(scheme.privateKey().orElseThrow()))
            .setSerial(new CertificateSerialDTO().setId(42L).setExpiration(new Date(10000L)));
    }

    @Test
    void decodesExportedScaWithoutChangingSerialOrKey() throws Exception {
        this.writeCertificate();
        CertificateDTO imported = this.reader.read(this.directory);
        assertEquals(this.certificate, imported);
        assertEquals(42L, imported.getSerial().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificate", "key", "serial", "serialId", "expiration", "zeroSerialId"})
    void rejectsIncompleteMaterial(String field) throws Exception {
        switch (field) {
            case "certificate" -> this.certificate.setCertificate(" ");
            case "key" -> this.certificate.setKey("");
            case "serial" -> this.certificate.setSerial(null);
            case "serialId" -> this.certificate.getSerial().setId(null);
            case "expiration" -> this.certificate.getSerial().setExpiration(null);
            case "zeroSerialId" -> this.certificate.getSerial().setId(0L);
            default -> throw new IllegalArgumentException(field);
        }
        this.writeCertificate();
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory));
    }

    @Test
    void rejectsMissingScaCertificate() {
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory));
    }

    @Test
    void rejectsSymlinks() throws Exception {
        this.writeCertificate();
        Path target = this.directory.resolve("target_cert.json");
        Path scaFile = this.directory.resolve(ManifestConstants.SCA_CERTIFICATE_JSON);
        Files.move(scaFile, target);
        Files.createSymbolicLink(scaFile, target);
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory));
    }

    @Test
    void rejectsDirectoryInsteadOfFile() throws Exception {
        Files.createDirectory(this.directory.resolve(ManifestConstants.SCA_CERTIFICATE_JSON));
        assertThrows(SyncDataFormatException.class, () -> this.reader.read(this.directory));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "", "{invalid-json-private-key-secret", "{}"})
    void rejectsMalformedJsonWithoutLeakingPrivateMaterial(String json) throws Exception {
        Files.writeString(this.directory.resolve(ManifestConstants.SCA_CERTIFICATE_JSON), json);
        ImporterException failure = assertThrows(ImporterException.class, () -> this.reader.read(this.directory));
        assertFalse(failure.getMessage().contains("private-key-secret"));
        assertNull(failure.getCause());
    }

    @Test
    void rejectsMissingDirectoryAndNullInput() {
        assertThrows(ImporterException.class, () -> this.reader.read(this.directory.resolve("absent")));
        assertThrows(NullPointerException.class, () -> this.reader.read(null));
    }

    @Test
    void rejectsNullDependencies() {
        assertThrows(NullPointerException.class, () ->
            new DefaultManifestV2ScaCertificateReader(null, this.i18n));
        assertThrows(NullPointerException.class, () ->
            new DefaultManifestV2ScaCertificateReader(this.mapper, null));
    }

    private void writeCertificate() throws Exception {
        this.mapper.writeValue(this.directory.resolve(ManifestConstants.SCA_CERTIFICATE_JSON).toFile(),
            this.certificate);
    }
}
