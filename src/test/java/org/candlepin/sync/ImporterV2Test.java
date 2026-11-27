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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.controller.Refresher;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v1.ConsumerTypeDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.dto.manifest.v2.CertificateDTO;
import org.candlepin.dto.manifest.v2.CertificateSerialDTO;
import org.candlepin.dto.manifest.v2.ContentDTO;
import org.candlepin.dto.manifest.v2.MetaDTO;
import org.candlepin.dto.manifest.v2.ProductDTO;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.xnap.commons.i18n.I18nFactory;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class ImporterV2Test extends DatabaseTestFixture {
    @TempDir
    private Path tempDir;

    private ManifestV2SignatureVerifier verifier;
    private ManifestV2IdentityCertificateReader identityReader;
    private ManifestV2ScaCertificateReader scaReader;
    private ManifestV2CertificateInstaller installer;
    private ManifestV2DataReader reader;
    private OwnerCurator mockOwnerCurator;
    private ConsumerImporter consumerImporter;
    private ConsumerTypeCurator typeCurator;
    private ManifestMetadataValidator metadataValidator;
    private ExporterMetadataCurator metadataCurator;
    private SubscriptionReconcilerV2 reconciler;
    private RefresherFactory refresherFactory;
    private ModelTranslator translator;
    private ImporterV2 importer;
    private Owner owner;
    private ManifestV2Data data;
    private ConflictOverrides overrides;
    private CertificateInfo identity;
    private CertificateInfo sca;
    private final Path archive = Path.of("archive.zip");
    private final Path directory = Path.of("payload");
    private Module overrideModule = new AbstractModule() {};

    @Override
    protected Module getGuiceOverrideModule() {
        return this.overrideModule;
    }

    @BeforeEach
    void setup() {
        this.verifier = mock(ManifestV2SignatureVerifier.class);
        this.identityReader = mock(ManifestV2IdentityCertificateReader.class);
        this.scaReader = mock(ManifestV2ScaCertificateReader.class);
        this.installer = mock(ManifestV2CertificateInstaller.class);
        this.reader = mock(ManifestV2DataReader.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);
        this.consumerImporter = mock(ConsumerImporter.class);
        this.typeCurator = mock(ConsumerTypeCurator.class);
        this.metadataValidator = mock(ManifestMetadataValidator.class);
        this.metadataCurator = mock(ExporterMetadataCurator.class);
        this.reconciler = mock(SubscriptionReconcilerV2.class);
        this.refresherFactory = mock(RefresherFactory.class);
        this.translator = mock(ModelTranslator.class);
        this.importer = new ImporterV2(this.verifier, this.identityReader, this.scaReader, this.installer,
            this.reader, this.mockOwnerCurator, this.consumerImporter, this.typeCurator, this.metadataValidator,
            this.metadataCurator, this.reconciler, this.refresherFactory, mock(CdnCurator.class), this.translator,
            I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK));
        this.owner = new Owner().setId("owner");
        ConsumerDTO consumer = new ConsumerDTO().setUuid("upstream").setType(new ConsumerTypeDTO()
            .setLabel("candlepin"));
        this.data = new ManifestV2Data(new Meta(), consumer,
            List.of(new SubscriptionDTO().setQuantity(-1L).setUpstreamPoolId("pool")));
        this.overrides = new ConflictOverrides();
        this.identity = mock(CertificateInfo.class);
        this.sca = mock(CertificateInfo.class);
    }

    @Test
    void verifiesBeforeReadingAndValidatesBeforePersistence() throws Exception {
        this.stubPayload();
        ConsumerType type = new ConsumerType("candlepin");
        type.setManifest(true);
        when(this.typeCurator.getByLabel("candlepin")).thenReturn(type);
        when(this.translator.translate(type, ConsumerTypeDTO.class)).thenReturn(this.data.getConsumer().getType());
        ExporterMetadata metadata = new ExporterMetadata();
        when(this.metadataValidator.validate(ExporterMetadata.TYPE_PER_USER, this.owner, this.data.getMeta(),
            this.overrides)).thenReturn(metadata);
        Refresher refresher = mock(Refresher.class);
        when(this.refresherFactory.getRefresher(any())).thenReturn(refresher);
        when(refresher.add(this.owner)).thenReturn(refresher);

        assertSame(this.data, this.importer.importObjects(this.owner, this.archive, this.directory, this.overrides));
        InOrder order = inOrder(this.verifier, this.reader, this.identityReader, this.scaReader, this.installer,
            this.mockOwnerCurator, this.consumerImporter, this.metadataCurator, this.reconciler, refresher);
        order.verify(this.verifier).verify(this.archive, this.directory, this.overrides);
        order.verify(this.reader).read(this.directory, this.owner);
        order.verify(this.identityReader).read(this.directory);
        order.verify(this.scaReader).read(this.directory);
        order.verify(this.installer).validate(this.data.getConsumer(), this.identity, this.sca);
        order.verify(this.mockOwnerCurator).lock(this.owner);
        order.verify(this.consumerImporter).store(this.owner, this.data.getConsumer(), this.overrides, null);
        order.verify(this.installer).install(this.owner, this.identity, this.sca);
        order.verify(this.metadataCurator).saveOrUpdateAll(List.of(metadata), true, false);
        order.verify(this.reconciler).reconcile(this.owner, this.data.getSubscriptions());
        order.verify(refresher).add(this.owner);
        order.verify(refresher).run();
        assertEquals("org_environment", this.owner.getContentAccessMode());
    }

    @Test
    void signatureFailurePreventsAllPayloadReadingAndWrites() throws Exception {
        ImporterException failure = new ImporterException("invalid signature");
        doThrow(failure).when(this.verifier).verify(this.archive, this.directory, this.overrides);
        assertSame(failure, assertThrows(ImporterException.class,
            () -> this.importer.importObjects(this.owner, this.archive, this.directory, this.overrides)));
        verifyNoInteractions(this.reader, this.identityReader, this.scaReader, this.installer,
            this.mockOwnerCurator, this.consumerImporter, this.metadataCurator, this.reconciler,
            this.refresherFactory);
    }

    @Test
    void certificateValidationFailurePreventsWrites() throws Exception {
        this.stubPayload();
        doThrow(new ImporterException("invalid certificate")).when(this.installer)
            .validate(this.data.getConsumer(), this.identity, this.sca);
        assertThrows(ImporterException.class,
            () -> this.importer.importObjects(this.owner, this.archive, this.directory, this.overrides));
        verifyNoInteractions(this.mockOwnerCurator, this.consumerImporter, this.metadataCurator,
            this.reconciler, this.refresherFactory);
    }

    @Test
    void missingScaCannotFallBackToIdentityOrEntitlementCertificates() throws Exception {
        when(this.reader.read(this.directory, this.owner)).thenReturn(this.data);
        when(this.identityReader.read(this.directory)).thenReturn(this.identity);
        assertThrows(SyncDataFormatException.class,
            () -> this.importer.importObjects(this.owner, this.archive, this.directory, this.overrides));
        verifyNoInteractions(this.installer, this.mockOwnerCurator, this.consumerImporter, this.refresherFactory);
    }

    @Test
    void reportsTimestampAndConsumerConflictsTogether() throws Exception {
        this.stubPayload();
        ConsumerType type = new ConsumerType("candlepin");
        type.setManifest(true);
        when(this.typeCurator.getByLabel("candlepin")).thenReturn(type);
        when(this.translator.translate(type, ConsumerTypeDTO.class)).thenReturn(this.data.getConsumer().getType());
        when(this.metadataValidator.validate(any(), eq(this.owner), any(), eq(this.overrides)))
            .thenThrow(new ImportConflictException("old", Importer.Conflict.MANIFEST_OLD));
        doThrow(new ImportConflictException("different distributor", Importer.Conflict.DISTRIBUTOR_CONFLICT))
            .when(this.consumerImporter).store(eq(this.owner), any(), eq(this.overrides), isNull());
        ImportConflictException failure = assertThrows(ImportConflictException.class,
            () -> this.importer.importObjects(this.owner, this.archive, this.directory, this.overrides));
        assertEquals(2, failure.message().getConflicts().size());
        verifyNoInteractions(this.metadataCurator, this.reconciler, this.refresherFactory);
        verify(this.installer).validate(this.data.getConsumer(), this.identity, this.sca);
    }

    @Test
    void rejectsNullArgumentsBeforeVerification() {
        assertThrows(NullPointerException.class,
            () -> this.importer.importObjects(null, this.archive, this.directory, this.overrides));
        assertThrows(NullPointerException.class,
            () -> this.importer.importObjects(this.owner, null, this.directory, this.overrides));
        assertThrows(NullPointerException.class,
            () -> this.importer.importObjects(this.owner, this.archive, null, this.overrides));
        assertThrows(NullPointerException.class,
            () -> this.importer.importObjects(this.owner, this.archive, this.directory, null));
        verifyNoInteractions(this.verifier);
    }

    @Test
    void importsSignedV2ArchiveReimportsAndReconcilesPools() throws Exception {
        this.ensureCandlepinConsumerType();
        Owner targetOwner = this.createOwner("v2-import-org", "V2 Import Org");
        Product customProduct = this.createProduct("custom-prod", "Custom Product");
        Pool customPool = this.createPool(targetOwner, customProduct, 5L, new Date(),
            Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));

        String upstreamUuid = "upstream-consumer-v2-1";
        Date firstCreated = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
        Path firstArchive = this.buildSignedV2Archive("first.zip", upstreamUuid, firstCreated, 700101L, 700102L,
            Map.of("prod-1", this.productDto("prod-1", "Product One", "content-1"),
                "prod-2", this.productDto("prod-2", "Product Two", "content-2")),
            Map.of("upstream-pool-1", this.subscriptionDto("sub-1", "prod-1", "contract-1", "account-1"),
                "upstream-pool-2", this.subscriptionDto("sub-2", "prod-2", "contract-2", "account-2")));

        Importer realImporter = this.injector.getInstance(Importer.class);
        ImportRecord record = realImporter.loadExport(targetOwner, firstArchive.toFile(),
            new ConflictOverrides(), "first.zip");

        assertEquals(ImportRecord.Status.SUCCESS, record.getStatus());
        Owner refreshedOwner = this.ownerCurator.get(targetOwner.getId());
        assertEquals("org_environment", refreshedOwner.getContentAccessMode());
        assertNotNull(refreshedOwner.getUpstreamConsumer());
        assertEquals(upstreamUuid, refreshedOwner.getUpstreamConsumer().getUuid());
        IdentityCertificate installedIdCert = refreshedOwner.getUpstreamConsumer().getIdCert();
        assertNotNull(installedIdCert);
        assertEquals(700101L, installedIdCert.getSerial().getId());
        assertEquals(BigInteger.valueOf(700101L), installedIdCert.getSerial().getSerial());
        SCACertificate installedScaCert = refreshedOwner.getUpstreamConsumer().getContentAccessCert();
        assertNotNull(installedScaCert);
        assertEquals(700102L, installedScaCert.getSerial().getId());
        assertEquals(BigInteger.valueOf(700102L), installedScaCert.getSerial().getSerial());

        List<Pool> poolsAfterFirst = this.poolCurator.listByOwner(refreshedOwner);
        assertEquals(3, poolsAfterFirst.size());
        Pool importedPool1 = poolsAfterFirst.stream()
            .filter(p -> "upstream-pool-1".equals(p.getUpstreamPoolId()))
            .findFirst()
            .orElseThrow();
        String originalSubId1 = importedPool1.getSubscriptionId();
        assertNotNull(originalSubId1);
        assertEquals(-1L, importedPool1.getQuantity());

        Content importedContent1 = this.contentCurator.getContentById(null, "content-1");
        assertNotNull(importedContent1);
        assertEquals(1L, importedContent1.getMetadataExpiration());

        // Reimport with updated pool-1, removed pool-2, added pool-3, and updated identity serial
        Date secondCreated = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Path secondArchive = this.buildSignedV2Archive("second.zip", upstreamUuid, secondCreated, 700201L, 700202L,
            Map.of("prod-1", this.productDto("prod-1", "Product One Updated", "content-1"),
                "prod-3", this.productDto("prod-3", "Product Three", "content-3")),
            Map.of("upstream-pool-1", this.subscriptionDto("sub-1-new", "prod-1", "contract-1-upd", "account-1"),
                "upstream-pool-3", this.subscriptionDto("sub-3", "prod-3", "contract-3", "account-3")));

        realImporter.loadExport(refreshedOwner, secondArchive.toFile(), new ConflictOverrides(), "second.zip");

        List<Pool> poolsAfterSecond = this.poolCurator.listByOwner(refreshedOwner);
        assertEquals(3, poolsAfterSecond.size());
        assertTrue(poolsAfterSecond.stream().anyMatch(p -> customPool.getId().equals(p.getId())));
        assertFalse(poolsAfterSecond.stream().anyMatch(p -> "upstream-pool-2".equals(p.getUpstreamPoolId())));
        Pool reconciledPool1 = poolsAfterSecond.stream()
            .filter(p -> "upstream-pool-1".equals(p.getUpstreamPoolId()))
            .findFirst()
            .orElseThrow();
        assertEquals(originalSubId1, reconciledPool1.getSubscriptionId());
        assertEquals("contract-1-upd", reconciledPool1.getContractNumber());
        assertTrue(poolsAfterSecond.stream().anyMatch(p -> "upstream-pool-3".equals(p.getUpstreamPoolId())));
        assertEquals(700201L, refreshedOwner.getUpstreamConsumer().getIdCert().getSerial().getId());
        assertEquals(700202L, refreshedOwner.getUpstreamConsumer().getContentAccessCert().getSerial().getId());

        // Empty manifest import removes upstream pools while keeping custom pool
        Date emptyCreated = Date.from(Instant.now());
        Path emptyArchive = this.buildSignedV2Archive("empty.zip", upstreamUuid, emptyCreated, 700201L, 700203L,
            Map.of(), Map.of());
        realImporter.loadExport(refreshedOwner, emptyArchive.toFile(), new ConflictOverrides(), "empty.zip");

        List<Pool> poolsAfterEmpty = this.poolCurator.listByOwner(refreshedOwner);
        assertEquals(1, poolsAfterEmpty.size());
        assertEquals(customPool.getId(), poolsAfterEmpty.get(0).getId());
    }

    @Test
    void enforcesSignatureAndTrustOverridesOnRealV2Archives() throws Exception {
        this.ensureCandlepinConsumerType();
        Owner targetOwner = this.createOwner("v2-sig-org", "V2 Sig Org");
        String upstreamUuid = "upstream-sig-consumer";
        Date created = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Map<String, byte[]> payloadEntries = this.buildPayloadEntries(upstreamUuid, upstreamUuid, upstreamUuid,
            created, 700301L, 700302L, Map.of(), Map.of());

        CryptoManager cryptoManager = this.injector.getInstance(CryptoManager.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        ObjectMapper syncMapper = ObjectMapperFactory.getSyncObjectMapper(this.config);
        payloadEntries.put(ManifestConstants.SCHEME_JSON, syncMapper.writeValueAsBytes(SchemeFile.from(scheme)));
        byte[] validPayload = this.zipBytes(payloadEntries);
        byte[] validSignature = cryptoManager.getSigner(scheme).sign(validPayload);

        // Tamper with the inner archive after signing
        payloadEntries.put(ManifestConstants.META_JSON, syncMapper.writeValueAsBytes(
            new MetaDTO().setVersion("6.0.0-1").setCreated(new Date())));
        byte[] tamperedPayload = this.zipBytes(payloadEntries);
        Path tamperedArchive = Files.write(this.tempDir.resolve("tampered.zip"),
            this.zipBytes(Map.of(ManifestConstants.CONSUMER_EXPORT_ZIP, tamperedPayload,
                ManifestConstants.SIGNATURE, validSignature)));

        Importer realImporter = this.injector.getInstance(Importer.class);
        ImportConflictException sigConflict = assertThrows(ImportConflictException.class,
            () -> realImporter.loadExport(targetOwner, tamperedArchive.toFile(),
                new ConflictOverrides(), "tampered.zip"));
        assertTrue(sigConflict.message().getConflicts().contains(Importer.Conflict.SIGNATURE_CONFLICT));
        assertNull(this.ownerCurator.get(targetOwner.getId()).getUpstreamConsumer());

        // Untrusted signing certificate
        KeyPair untrustedKeys = CryptoUtil.generateKeyPair(scheme);
        X509Certificate untrustedCert = this.selfSignedCertificate(untrustedKeys, scheme);
        Scheme untrustedScheme = new Scheme(scheme.name(), untrustedCert, Optional.of(untrustedKeys.getPrivate()),
            scheme.signatureAlgorithm(), scheme.keyAlgorithm(), scheme.keySize());
        payloadEntries.put(ManifestConstants.SCHEME_JSON,
            syncMapper.writeValueAsBytes(SchemeFile.from(untrustedScheme)));
        byte[] untrustedPayload = this.zipBytes(payloadEntries);
        Signature signer = Signature.getInstance(untrustedScheme.signatureAlgorithm(),
            cryptoManager.getSecurityProvider());
        signer.initSign(untrustedKeys.getPrivate());
        signer.update(untrustedPayload);
        byte[] untrustedSignature = signer.sign();

        Path untrustedArchive = Files.write(this.tempDir.resolve("untrusted.zip"),
            this.zipBytes(Map.of(ManifestConstants.CONSUMER_EXPORT_ZIP, untrustedPayload,
                ManifestConstants.SIGNATURE, untrustedSignature)));

        ImportConflictException trustConflict = assertThrows(ImportConflictException.class,
            () -> realImporter.loadExport(targetOwner, untrustedArchive.toFile(),
                new ConflictOverrides(), "untrusted.zip"));
        assertTrue(trustConflict.message().getConflicts().contains(Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE));

        // Succeeds when UNTRUSTED_SIGNING_CERTIFICATE is explicitly overridden
        ImportRecord record = realImporter.loadExport(targetOwner, untrustedArchive.toFile(),
            new ConflictOverrides(Importer.Conflict.UNTRUSTED_SIGNING_CERTIFICATE), "untrusted.zip");
        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(upstreamUuid, this.ownerCurator.get(targetOwner.getId()).getUpstreamUuid());
    }

    @Test
    void rejectsWrongCertificateOwnershipAndMalformedMaterialOnRealV2Archives() throws Exception {
        this.ensureCandlepinConsumerType();
        Owner targetOwner = this.createOwner("v2-invalid-cert-org", "V2 Invalid Cert Org");
        String upstreamUuid = "upstream-expected-uuid";
        Date created = Date.from(Instant.now());
        CryptoManager cryptoManager = this.injector.getInstance(CryptoManager.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        ObjectMapper syncMapper = ObjectMapperFactory.getSyncObjectMapper(this.config);

        // Wrong SCA certificate ownership (CN mismatch)
        Map<String, byte[]> wrongOwnerEntries = this.buildPayloadEntries(upstreamUuid, upstreamUuid,
            "different-consumer-uuid", created, 700401L, 700402L, Map.of(), Map.of());
        wrongOwnerEntries.put(ManifestConstants.SCHEME_JSON, syncMapper.writeValueAsBytes(SchemeFile.from(scheme)));
        byte[] wrongOwnerPayload = this.zipBytes(wrongOwnerEntries);
        Path wrongOwnerArchive = Files.write(this.tempDir.resolve("wrong-owner.zip"),
            this.zipBytes(Map.of(ManifestConstants.CONSUMER_EXPORT_ZIP, wrongOwnerPayload,
                ManifestConstants.SIGNATURE, cryptoManager.getSigner(scheme).sign(wrongOwnerPayload))));

        Importer realImporter = this.injector.getInstance(Importer.class);
        assertThrows(SyncDataFormatException.class,
            () -> realImporter.loadExport(targetOwner, wrongOwnerArchive.toFile(),
                new ConflictOverrides(), "wrong-owner.zip"));
        assertNull(this.ownerCurator.get(targetOwner.getId()).getUpstreamConsumer());
        assertNull(this.certSerialCurator.get(700401L));

        // Missing SCA certificate file
        Map<String, byte[]> missingScaEntries = this.buildPayloadEntries(upstreamUuid, upstreamUuid,
            upstreamUuid, created, 700403L, 700404L, Map.of(), Map.of());
        missingScaEntries.remove(ManifestConstants.SCA_CERTIFICATE_JSON);
        missingScaEntries.put(ManifestConstants.SCHEME_JSON, syncMapper.writeValueAsBytes(SchemeFile.from(scheme)));
        byte[] missingScaPayload = this.zipBytes(missingScaEntries);
        Path missingScaArchive = Files.write(this.tempDir.resolve("missing-sca.zip"),
            this.zipBytes(Map.of(ManifestConstants.CONSUMER_EXPORT_ZIP, missingScaPayload,
                ManifestConstants.SIGNATURE, cryptoManager.getSigner(scheme).sign(missingScaPayload))));

        assertThrows(SyncDataFormatException.class,
            () -> realImporter.loadExport(targetOwner, missingScaArchive.toFile(),
                new ConflictOverrides(), "missing-sca.zip"));
        assertNull(this.ownerCurator.get(targetOwner.getId()).getUpstreamConsumer());
    }

    @Test
    void rollsBackConsumerAndCertificateWritesWhenInstallOrRefreshFails() throws Exception {
        this.ensureCandlepinConsumerType();
        Owner targetOwner = this.createOwner("v2-rollback-org", "V2 Rollback Org");
        this.commitTransaction();
        this.getEntityManager().clear();

        String upstreamUuid = "upstream-rollback-consumer";
        Path archiveFile = this.buildSignedV2Archive("rollback.zip", upstreamUuid, new Date(), 700501L, 700502L,
            Map.of("prod-rb", this.productDto("prod-rb", "Rollback Product", "content-rb")),
            Map.of("pool-rb", this.subscriptionDto("sub-rb", "prod-rb", "contract-rb", "account-rb")));

        // 1. Certificate installer failure after ConsumerImporter.store() rolls back UpstreamConsumer
        ManifestV2CertificateInstaller failingInstaller = mock(ManifestV2CertificateInstaller.class);
        doThrow(new ImporterException("simulated certificate install failure"))
            .when(failingInstaller).install(any(), any(), any());

        this.overrideModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(ManifestV2CertificateInstaller.class).toInstance(failingInstaller);
            }
        };
        this.init(false);

        Importer installFailImporter = this.injector.getInstance(Importer.class);
        this.beginTransaction();
        Owner ownerForInstallFail = this.ownerCurator.get(targetOwner.getId());
        this.commitTransaction();

        assertThrows(ImporterException.class,
            () -> installFailImporter.loadExport(ownerForInstallFail, archiveFile.toFile(),
                new ConflictOverrides(), "rollback.zip"));

        this.getEntityManager().clear();
        this.beginTransaction();
        assertNull(this.ownerCurator.get(targetOwner.getId()).getUpstreamConsumer());
        this.commitTransaction();

        // 2. Refresher failure after certificate installation rolls back UpstreamConsumer and CertificateSerial
        RefresherFactory failingRefresherFactory = mock(RefresherFactory.class);
        Refresher failingRefresher = mock(Refresher.class);
        when(failingRefresherFactory.getRefresher(any())).thenReturn(failingRefresher);
        when(failingRefresher.add(any(Owner.class))).thenReturn(failingRefresher);
        doThrow(new RuntimeException("simulated refresh failure")).when(failingRefresher).run();

        this.overrideModule = new AbstractModule() {
            @Override
            protected void configure() {
                bind(RefresherFactory.class).toInstance(failingRefresherFactory);
            }
        };
        this.init(false);

        Importer rollbackImporter = this.injector.getInstance(Importer.class);

        this.beginTransaction();
        Owner managedOwner = this.ownerCurator.get(targetOwner.getId());
        this.commitTransaction();

        assertThrows(RuntimeException.class,
            () -> rollbackImporter.loadExport(managedOwner, archiveFile.toFile(),
                new ConflictOverrides(), "rollback.zip"));

        this.getEntityManager().clear();
        this.beginTransaction();
        Owner afterRollback = this.ownerCurator.get(targetOwner.getId());
        assertNull(afterRollback.getUpstreamConsumer());
        assertNull(this.certSerialCurator.get(700501L));
        assertNull(this.certSerialCurator.get(700502L));
        assertTrue(this.poolCurator.listByOwner(afterRollback).isEmpty());
        this.commitTransaction();
    }

    private void ensureCandlepinConsumerType() {
        ConsumerType existing = this.consumerTypeCurator.getByLabel(ConsumerTypeEnum.CANDLEPIN.getLabel());
        if (existing == null) {
            ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
            type.setManifest(true);
            this.consumerTypeCurator.create(type);
        }
    }

    private ProductDTO productDto(String id, String name, String contentId) {
        ContentDTO content = new ContentDTO()
            .setId(contentId)
            .setName("Content " + contentId)
            .setLabel("label-" + contentId)
            .setType("yum")
            .setVendor("Red Hat")
            .setContentUrl("/content/" + contentId)
            .setEnabled(true);
        return new ProductDTO()
            .setId(id)
            .setName(name)
            .setCreated(new Date(1000L))
            .setUpdated(new Date(2000L))
            .setAttributes(Map.of("arch", "x86_64"))
            .setProvidedProducts(Set.of())
            .setProductContent(Map.of(contentId, content));
    }

    private org.candlepin.dto.manifest.v2.SubscriptionDTO subscriptionDto(String subId, String productId,
        String contract, String account) {
        Date start = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Date end = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));
        return new org.candlepin.dto.manifest.v2.SubscriptionDTO()
            .setSubscriptionId(subId)
            .setProductId(productId)
            .setContractNumber(contract)
            .setAccountNumber(account)
            .setStartDate(start)
            .setEndDate(end)
            .setCreated(start)
            .setUpdated(start);
    }

    private Path buildSignedV2Archive(String filename, String consumerUuid, Date created,
        long idSerial, long scaSerial, Map<String, ProductDTO> products,
        Map<String, org.candlepin.dto.manifest.v2.SubscriptionDTO> subscriptions) throws Exception {
        CryptoManager cryptoManager = this.injector.getInstance(CryptoManager.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        ObjectMapper syncMapper = ObjectMapperFactory.getSyncObjectMapper(this.config);

        Map<String, byte[]> entries = this.buildPayloadEntries(consumerUuid, consumerUuid, consumerUuid,
            created, idSerial, scaSerial, products, subscriptions);
        entries.put(ManifestConstants.SCHEME_JSON, syncMapper.writeValueAsBytes(SchemeFile.from(scheme)));
        byte[] payload = this.zipBytes(entries);
        byte[] signature = cryptoManager.getSigner(scheme).sign(payload);
        return Files.write(this.tempDir.resolve(filename),
            this.zipBytes(Map.of(ManifestConstants.CONSUMER_EXPORT_ZIP, payload,
                ManifestConstants.SIGNATURE, signature)));
    }

    private Map<String, byte[]> buildPayloadEntries(String consumerUuid, String idCertCn, String scaCertCn,
        Date created, long idSerial, long scaSerial, Map<String, ProductDTO> products,
        Map<String, org.candlepin.dto.manifest.v2.SubscriptionDTO> subscriptions) throws Exception {
        CryptoManager cryptoManager = this.injector.getInstance(CryptoManager.class);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();
        ObjectMapper syncMapper = ObjectMapperFactory.getSyncObjectMapper(this.config);
        PemEncoder pemEncoder = this.injector.getInstance(PemEncoder.class);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        MetaDTO meta = new MetaDTO()
            .setVersion("6.0.0-1")
            .setCreated(created)
            .setPrincipalName("admin")
            .setWebAppPrefix("https://upstream.example.com/subscription");
        entries.put(ManifestConstants.META_JSON, syncMapper.writeValueAsBytes(meta));

        org.candlepin.dto.manifest.v2.ConsumerDTO consumer = new org.candlepin.dto.manifest.v2.ConsumerDTO()
            .setUuid(consumerUuid)
            .setName("upstream-" + consumerUuid)
            .setOwnerId("upstream-owner")
            .setUrlApi("https://upstream.example.com/candlepin")
            .setUrlWeb("https://upstream.example.com/subscription")
            .setType(new org.candlepin.dto.manifest.v2.ConsumerTypeDTO()
                .setId("candlepin-type")
                .setLabel(ConsumerTypeEnum.CANDLEPIN.getLabel()));
        entries.put(ManifestConstants.UPSTREAM_CONSUMER + "/" + ManifestConstants.CONSUMER_JSON,
            syncMapper.writeValueAsBytes(consumer));

        CertificateDTO idCertDto = this.generateCertDto(cryptoManager, scheme, pemEncoder, idCertCn, idSerial);
        entries.put(ManifestConstants.UPSTREAM_CONSUMER + "/" + idSerial + ManifestConstants.JSON_SUFFIX,
            syncMapper.writeValueAsBytes(idCertDto));

        CertificateDTO scaCertDto = this.generateCertDto(cryptoManager, scheme, pemEncoder, scaCertCn, scaSerial);
        scaCertDto.setCertificate(scaCertDto.getCertificate() +
            "-----BEGIN ENTITLEMENT DATA-----\npayload\n-----END ENTITLEMENT DATA-----\n" +
            "-----BEGIN SIGNATURE-----\nsignature\n-----END SIGNATURE-----\n");
        entries.put(ManifestConstants.SCA_CERTIFICATE_JSON, syncMapper.writeValueAsBytes(scaCertDto));

        for (Map.Entry<String, ProductDTO> entry : products.entrySet()) {
            entries.put(ManifestConstants.PRODUCTS + "/" + entry.getKey() + ManifestConstants.JSON_SUFFIX,
                syncMapper.writeValueAsBytes(entry.getValue()));
        }
        for (Map.Entry<String, org.candlepin.dto.manifest.v2.SubscriptionDTO> entry : subscriptions.entrySet()) {
            entries.put(ManifestConstants.SUBSCRIPTIONS + "/" + entry.getKey() + ManifestConstants.JSON_SUFFIX,
                syncMapper.writeValueAsBytes(entry.getValue()));
        }
        return entries;
    }

    private CertificateDTO generateCertDto(CryptoManager cryptoManager, Scheme scheme, PemEncoder pemEncoder,
        String commonName, long serialId) throws Exception {
        KeyPair keyPair = CryptoUtil.generateKeyPair(scheme);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(365, ChronoUnit.DAYS);
        Owner certOwner = TestUtil.createOwner("upstream-org");
        X509Certificate cert = cryptoManager.getCertificateBuilder(scheme)
            .withDN(new DistinguishedName(commonName, certOwner))
            .withValidity(start, end)
            .withSerial(BigInteger.valueOf(serialId))
            .withKeyPair(keyPair)
            .build();

        CertificateSerialDTO serialDto = new CertificateSerialDTO()
            .setId(serialId)
            .setSerial(BigInteger.valueOf(serialId))
            .setExpiration(Date.from(end))
            .setRevoked(false)
            .setCreated(Date.from(start))
            .setUpdated(Date.from(start));

        return new CertificateDTO()
            .setId("cert-" + serialId)
            .setCertificate(pemEncoder.encodeAsString(cert))
            .setKey(pemEncoder.encodeAsString(keyPair.getPrivate()))
            .setSerial(serialDto)
            .setCreated(Date.from(start))
            .setUpdated(Date.from(start));
    }

    private X509Certificate selfSignedCertificate(KeyPair keyPair, Scheme scheme) throws Exception {
        Instant now = Instant.now();
        X500Name issuer = new X500Name("CN=untrusted-ca");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(999L),
            Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(3600)), issuer, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(scheme.signatureAlgorithm())
            .setProvider(CryptoUtil.getSecurityProvider())
            .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
            .setProvider(CryptoUtil.getSecurityProvider())
            .getCertificate(builder.build(signer));
    }

    private byte[] zipBytes(Map<String, byte[]> entries) throws Exception {
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

    private void stubPayload() throws Exception {
        when(this.reader.read(this.directory, this.owner)).thenReturn(this.data);
        when(this.identityReader.read(this.directory)).thenReturn(this.identity);
        when(this.scaReader.read(this.directory)).thenReturn(this.sca);
    }
}
