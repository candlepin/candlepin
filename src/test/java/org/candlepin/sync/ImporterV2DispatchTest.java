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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventSink;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.sync.file.ManifestFile;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ImporterV2DispatchTest {
    @TempDir
    private Path directory;
    private SyncUtils syncUtils;
    private CryptoManager cryptoManager;
    private EventSink sink;
    private OwnerCurator ownerCurator;
    private ImportRecordCurator records;
    private Owner owner;
    private Path staging;

    @BeforeEach
    void setup() throws Exception {
        this.syncUtils = mock(SyncUtils.class);
        this.cryptoManager = mock(CryptoManager.class);
        this.sink = mock(EventSink.class);
        this.ownerCurator = mock(OwnerCurator.class);
        this.records = mock(ImportRecordCurator.class);
        this.owner = new Owner().setId("owner").setKey("owner-key");
        this.staging = this.directory.resolve("stage");
        when(this.syncUtils.makeTempDir("import")).thenAnswer(call -> Files.createDirectory(this.staging).toFile());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "3", "\"3.0.0\"", "\"\""})
    void rejectsUnsupportedExplicitVersionsBeforeV1Verification(String version) throws Exception {
        Path archive = this.writeArchive(Map.of("meta.json", this.bytes("{\"manifestVersion\":" + version + "}")));
        ImporterV2 pipeline = this.createPipeline();
        Importer importer = this.build(pipeline);
        assertThrows(SyncDataFormatException.class,
            () -> importer.loadExport(this.owner, archive.toFile(), new ConflictOverrides(), "manifest.zip"));
        assertFalse(this.staging.toFile().exists());
        verify(pipeline, never()).importObjects(any(), any(), any(), any());
        verifyNoInteractions(this.cryptoManager, this.sink, this.ownerCurator);
    }

    @Test
    void passesOriginalBytesAndPayloadToAvailablePipeline() throws Exception {
        byte[] metadata = this.bytes("{\"manifestVersion\":\"2.0.0\"}");
        Path archive = this.writeArchive(Map.of("meta.json", metadata, "upstream_consumer/", new byte[0]));
        byte[] original = Files.readAllBytes(archive);
        ImporterV2 pipeline = this.createPipeline();
        doAnswer(call -> {
            Path savedArchive = call.getArgument(1);
            Path payload = call.getArgument(2);
            assertArrayEquals(original, Files.readAllBytes(savedArchive));
            assertArrayEquals(metadata, Files.readAllBytes(payload.resolve("meta.json")));
            assertTrue(Files.isDirectory(payload.resolve("upstream_consumer")));
            return new ManifestV2Data(new Meta(), new ConsumerDTO(), List.of());
        }).when(pipeline).importObjects(any(), any(), any(), any());
        this.build(pipeline).loadExport(this.owner, archive.toFile(),
            new ConflictOverrides(), "manifest.zip");
        verify(this.sink).emitImportCreated(this.owner);
        verify(this.records).create(any());
        verifyNoInteractions(this.cryptoManager);
        assertFalse(this.staging.toFile().exists());
    }

    @Test
    void storedImportsUseTheSameV2DispatchAndCleanup() throws Exception {
        byte[] zip = this.zip(Map.of("meta.json", this.bytes("{\"manifestVersion\":\"2.0.0\"}")));
        ManifestFile stored = mock(ManifestFile.class);
        when(stored.getInputStream()).thenReturn(new ByteArrayInputStream(zip));
        when(stored.getId()).thenReturn("stored-id");
        ImporterV2 pipeline = this.createPipeline();
        doReturn(new ManifestV2Data(new Meta(), new ConsumerDTO(), List.of()))
            .when(pipeline).importObjects(any(), any(), any(), any());
        Importer importer = this.build(pipeline);
        importer.loadStoredExport(stored, this.owner, new ConflictOverrides(), "manifest.zip");
        verify(pipeline).importObjects(eq(this.owner), any(), any(), any());
        assertFalse(this.staging.toFile().exists());
    }

    @Test
    void recognizesNestedV2MetadataBeforeLegacySignatureHandling() throws Exception {
        byte[] inner = this.zip(Map.of("export/meta.json", this.bytes("{\"manifestVersion\":\"2.0.0\"}")));
        Path archive = this.writeArchive(Map.of("consumer_export.zip", inner));
        ImporterV2 pipeline = this.createPipeline();
        doReturn(new ManifestV2Data(new Meta(), new ConsumerDTO(), List.of()))
            .when(pipeline).importObjects(any(), any(), any(), any());
        this.build(pipeline).loadExport(this.owner, archive.toFile(),
            new ConflictOverrides(), "manifest.zip");
        verify(pipeline).importObjects(eq(this.owner), any(), any(), any());
        verifyNoInteractions(this.cryptoManager);
    }

    @Test
    void passesRootLayoutFromSignedV2ExportToPipeline() throws Exception {
        byte[] metadata = this.bytes("{\"manifestVersion\":\"2.0.0\"}");
        Path archive = this.writeArchive(Map.of("consumer_export.zip", this.zip(Map.of("meta.json", metadata)),
            "signature", this.bytes("signature"), "products/unsigned.json", this.bytes("unsigned")));
        ImporterV2 pipeline = this.createPipeline();
        doAnswer(call -> {
            Path payload = call.getArgument(2);
            assertArrayEquals(metadata, Files.readAllBytes(payload.resolve("meta.json")));
            assertFalse(Files.exists(payload.resolve("products/unsigned.json")));
            assertFalse(Files.exists(payload.resolve("signature")));
            return new ManifestV2Data(new Meta(), new ConsumerDTO(), List.of());
        }).when(pipeline).importObjects(any(), any(), any(), any());

        this.build(pipeline).loadExport(this.owner, archive.toFile(),
            new ConflictOverrides(), "manifest.zip");

        verify(pipeline).importObjects(eq(this.owner), any(), any(), any());
        verifyNoInteractions(this.cryptoManager);
        assertFalse(this.staging.toFile().exists());
    }

    @Test
    void rejectsSiblingPathTraversalAndDeletesFailedExtraction() throws Exception {
        Path archive = this.writeArchive(Map.of("../payload-escape/file", this.bytes("bad")));
        assertThrows(ImportExtractionException.class,
            () -> this.build(this.createPipeline()).loadExport(this.owner, archive.toFile(),
                new ConflictOverrides(), "manifest.zip"));
        assertFalse(this.staging.toFile().exists());
        assertFalse(this.directory.resolve("payload-escape").toFile().exists());
    }

    @Test
    void pipelineFailurePreventsSuccessRecordsAndEvents() throws Exception {
        Path archive = this.writeArchive(Map.of("meta.json", this.bytes("{\"manifestVersion\":\"2.0.0\"}")));
        ImporterV2 pipeline = this.createPipeline();
        doThrow(new ImporterException("invalid signature"))
            .when(pipeline).importObjects(any(), any(), any(), any());
        ImporterException failure = assertThrows(ImporterException.class,
            () -> this.build(pipeline).loadExport(this.owner, archive.toFile(),
                new ConflictOverrides(), "manifest.zip"));
        assertEquals("invalid signature", failure.getMessage());
        verifyNoInteractions(this.sink, this.records);
        assertFalse(this.staging.toFile().exists());
    }

    private ImporterV2 createPipeline() {
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());
        ManifestV2DataReader dataReader = new ManifestV2DataReader(mapper, i18n, new ManifestFormatDetector(i18n));
        return spy(new ImporterV2(mock(ManifestV2SignatureVerifier.class),
            mock(ManifestV2IdentityCertificateReader.class), mock(ManifestV2ScaCertificateReader.class),
            mock(ManifestV2CertificateInstaller.class), dataReader, this.ownerCurator,
            mock(ConsumerImporter.class), mock(ConsumerTypeCurator.class),
            mock(ManifestMetadataValidator.class), mock(ExporterMetadataCurator.class),
            mock(SubscriptionReconcilerV2.class), mock(RefresherFactory.class),
            mock(CdnCurator.class), mock(ModelTranslator.class), i18n));
    }

    private Importer build(ImporterV2 pipeline) {
        return new Importer(mock(ConsumerTypeCurator.class), mock(RulesImporter.class), this.ownerCurator,
            mock(IdentityCertificateCurator.class), mock(RefresherFactory.class), this.cryptoManager,
            mock(ExporterMetadataCurator.class), mock(CertificateSerialCurator.class), this.sink,
            I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK),
            mock(DistributorVersionCurator.class), mock(CdnCurator.class), this.syncUtils,
            ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults()), this.records,
            mock(SubscriptionReconciler.class), mock(ModelTranslator.class), mock(CertificateReader.class), pipeline);
    }

    private Path writeArchive(Map<String, byte[]> entries) throws Exception {
        return Files.write(this.directory.resolve("upload.zip"), this.zip(entries));
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

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
