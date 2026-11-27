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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.dto.manifest.v1.ConsumerDTO;
import org.candlepin.dto.manifest.v2.CertificateDTO;
import org.candlepin.dto.manifest.v2.CertificateSerialDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.SCACertificate;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class DefaultManifestV2CertificateInstallerTest {
    private static final String CONSUMER_UUID = "consumer-uuid-12345";
    private static final String OTHER_UUID = "other-consumer-uuid-99999";

    private CryptoManager cryptoManager;
    private IdentityCertificateCurator identityCertificateCurator;
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    private CertificateSerialCurator serialCurator;
    private OwnerCurator ownerCurator;
    private DefaultManifestV2CertificateInstaller installer;
    private ConsumerDTO consumer;
    private Scheme scheme;

    private CertificateDTO identity;
    private CertificateDTO sca;

    @BeforeEach
    void setup() throws Exception {
        this.cryptoManager = CryptoUtil.getCryptoManager();
        this.identityCertificateCurator = mock(IdentityCertificateCurator.class);
        this.contentAccessCertificateCurator = mock(ContentAccessCertificateCurator.class);
        this.serialCurator = mock(CertificateSerialCurator.class);
        this.ownerCurator = mock(OwnerCurator.class);
        when(this.serialCurator.create(any())).thenAnswer(call -> call.getArgument(0));
        when(this.serialCurator.merge(any())).thenAnswer(call -> call.getArgument(0));
        when(this.identityCertificateCurator.create(any())).thenAnswer(call -> call.getArgument(0));
        when(this.contentAccessCertificateCurator.create(any())).thenAnswer(call -> call.getArgument(0));
        this.scheme = this.cryptoManager.getDefaultCryptoScheme();
        I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);
        this.installer = new DefaultManifestV2CertificateInstaller(CryptoUtil.getCertificateReader(),
            CryptoUtil.getPrivateKeyReader(), this.cryptoManager, this.identityCertificateCurator,
            this.contentAccessCertificateCurator, this.serialCurator, this.ownerCurator, i18n);

        this.consumer = new ConsumerDTO().setUuid(CONSUMER_UUID);

        KeyPair identityKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        X509Certificate identityCert = this.createCertificate(identityKeyPair, CONSUMER_UUID, this.scheme, 101L);
        this.identity = new CertificateDTO()
            .setCertificate(CryptoUtil.getPemEncoder().encodeAsString(identityCert))
            .setKey(CryptoUtil.getPemEncoder().encodeAsString(identityKeyPair.getPrivate()))
            .setSerial(new CertificateSerialDTO().setId(101L).setExpiration(new Date()));

        KeyPair scaKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        X509Certificate scaCert = this.createCertificate(scaKeyPair, CONSUMER_UUID, this.scheme, 102L);
        this.sca = new CertificateDTO()
            .setCertificate(CryptoUtil.getPemEncoder().encodeAsString(scaCert))
            .setKey(CryptoUtil.getPemEncoder().encodeAsString(scaKeyPair.getPrivate()))
            .setSerial(new CertificateSerialDTO().setId(102L).setExpiration(new Date()));
    }

    @Test
    void validatesMatchingCertificatesAndKeys() {
        assertDoesNotThrow(() -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void validatesPostQuantumMldsaCertificatesAndKeys() throws Exception {
        Scheme mldsaScheme = CryptoUtil.SUPPORTED_SCHEMES.get("mldsa65");
        if (mldsaScheme == null) {
            return;
        }

        KeyPair idPair = CryptoUtil.generateKeyPair(mldsaScheme);
        X509Certificate idCert = this.createCertificate(idPair, CONSUMER_UUID, mldsaScheme, 201L);
        CertificateDTO mldsaIdentity = new CertificateDTO()
            .setCertificate(CryptoUtil.getPemEncoder().encodeAsString(idCert))
            .setKey(CryptoUtil.getPemEncoder().encodeAsString(idPair.getPrivate()))
            .setSerial(new CertificateSerialDTO().setId(201L).setExpiration(new Date()));

        KeyPair scaPair = CryptoUtil.generateKeyPair(mldsaScheme);
        X509Certificate scaCert = this.createCertificate(scaPair, CONSUMER_UUID, mldsaScheme, 202L);
        CertificateDTO mldsaSca = new CertificateDTO()
            .setCertificate(CryptoUtil.getPemEncoder().encodeAsString(scaCert))
            .setKey(CryptoUtil.getPemEncoder().encodeAsString(scaPair.getPrivate()))
            .setSerial(new CertificateSerialDTO().setId(202L).setExpiration(new Date()));

        assertDoesNotThrow(() -> this.installer.validate(this.consumer, mldsaIdentity, mldsaSca));
    }

    @Test
    void rejectsIdentityCertificateWithMismatchedConsumerUuid() throws Exception {
        KeyPair otherKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        X509Certificate otherCert = this.createCertificate(otherKeyPair, OTHER_UUID, this.scheme, 101L);
        this.identity.setCertificate(CryptoUtil.getPemEncoder().encodeAsString(otherCert));
        this.identity.setKey(CryptoUtil.getPemEncoder().encodeAsString(otherKeyPair.getPrivate()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsScaCertificateWithMismatchedConsumerUuid() throws Exception {
        KeyPair otherKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        X509Certificate otherCert = this.createCertificate(otherKeyPair, OTHER_UUID, this.scheme, 102L);
        this.sca.setCertificate(CryptoUtil.getPemEncoder().encodeAsString(otherCert));
        this.sca.setKey(CryptoUtil.getPemEncoder().encodeAsString(otherKeyPair.getPrivate()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsIdentityCertificateWithMismatchedSerial() {
        this.identity.setSerial(new CertificateSerialDTO().setId(999L).setExpiration(new Date()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsScaCertificateWithMismatchedSerial() {
        this.sca.setSerial(new CertificateSerialDTO().setId(999L).setExpiration(new Date()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsIdentityCertificateWithMismatchedPrivateKey() throws Exception {
        KeyPair otherKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        this.identity.setKey(CryptoUtil.getPemEncoder().encodeAsString(otherKeyPair.getPrivate()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsScaCertificateWithMismatchedPrivateKey() throws Exception {
        KeyPair otherKeyPair = CryptoUtil.generateKeyPair(this.scheme);
        this.sca.setKey(CryptoUtil.getPemEncoder().encodeAsString(otherKeyPair.getPrivate()));

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void rejectsMissingConsumerOrUuid() {
        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(null, this.identity, this.sca));

        this.consumer.setUuid(null);
        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));

        this.consumer.setUuid("   ");
        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullIdentity", "blankCert", "blankKey"})
    void rejectsMissingOrIncompleteIdentityMaterial(String scenario) {
        CertificateDTO testIdentity = switch (scenario) {
            case "nullIdentity" -> null;
            case "blankCert" -> new CertificateDTO().setCertificate(" ").setKey(this.identity.getKey());
            case "blankKey" -> new CertificateDTO().setCertificate(this.identity.getCertificate()).setKey("");
            default -> throw new IllegalArgumentException(scenario);
        };

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, testIdentity, this.sca));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullSca", "blankCert", "blankKey"})
    void rejectsMissingOrIncompleteScaMaterial(String scenario) {
        CertificateDTO testSca = switch (scenario) {
            case "nullSca" -> null;
            case "blankCert" -> new CertificateDTO().setCertificate(" ").setKey(this.sca.getKey());
            case "blankKey" -> new CertificateDTO().setCertificate(this.sca.getCertificate()).setKey("");
            default -> throw new IllegalArgumentException(scenario);
        };

        assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, testSca));
    }

    @Test
    void rejectsCorruptedMaterialWithoutLeakingPrivateKey() {
        this.identity.setKey("-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC-private-key-secret\n" +
            "-----END PRIVATE KEY-----");
        this.identity.setCertificate("invalid-not-a-certificate");

        SyncDataFormatException ex = assertThrows(SyncDataFormatException.class,
            () -> this.installer.validate(this.consumer, this.identity, this.sca));

        assertFalse(ex.getMessage().contains("private-key-secret"));
        assertNull(ex.getCause());
    }

    @Test
    void rejectsUnsupportedKeyAlgorithm() {
        CryptoManager unconfiguredManager = mock(CryptoManager.class);
        when(unconfiguredManager.getCryptoSchemes()).thenReturn(List.of());
        DefaultManifestV2CertificateInstaller unconfiguredInstaller = new DefaultManifestV2CertificateInstaller(
            CryptoUtil.getCertificateReader(), CryptoUtil.getPrivateKeyReader(), unconfiguredManager,
            this.identityCertificateCurator, this.contentAccessCertificateCurator, this.serialCurator,
            this.ownerCurator, I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK));

        assertThrows(SyncDataFormatException.class,
            () -> unconfiguredInstaller.validate(this.consumer, this.identity, this.sca));
    }

    @Test
    void installsIdentityAndScaCertificatesPreservingExactSerials() throws Exception {
        Owner owner = new Owner().setId("owner");
        UpstreamConsumer upstream = new UpstreamConsumer(CONSUMER_UUID);
        owner.setUpstreamConsumer(upstream);

        this.installer.install(owner, this.identity, this.sca);

        IdentityCertificate installedId = upstream.getIdCert();
        assertNotNull(installedId);
        assertEquals(this.identity.getCertificate(), installedId.getCert());
        assertEquals(this.identity.getKey(), installedId.getKey());
        assertNotNull(installedId.getSerial());
        assertEquals(101L, installedId.getSerial().getId());
        assertEquals(BigInteger.valueOf(101L), installedId.getSerial().getSerial());
        assertEquals(this.identity.getSerial().getExpiration(), installedId.getSerial().getExpiration());
        verify(this.serialCurator).create(installedId.getSerial());
        verify(this.identityCertificateCurator).create(installedId);

        SCACertificate installedSca = upstream.getContentAccessCert();
        assertNotNull(installedSca);
        assertEquals(this.sca.getCertificate(), installedSca.getCert());
        assertEquals(this.sca.getKey(), installedSca.getKey());
        assertNotNull(installedSca.getSerial());
        assertEquals(102L, installedSca.getSerial().getId());
        assertEquals(BigInteger.valueOf(102L), installedSca.getSerial().getSerial());
        assertEquals(this.sca.getSerial().getExpiration(), installedSca.getSerial().getExpiration());
        verify(this.serialCurator).create(installedSca.getSerial());
        verify(this.contentAccessCertificateCurator).create(installedSca);

        verify(this.ownerCurator).merge(owner);
    }

    @Test
    void installUpdatesExistingSerialOnReimport() throws Exception {
        Owner owner = new Owner().setId("owner");
        UpstreamConsumer upstream = new UpstreamConsumer(CONSUMER_UUID);
        owner.setUpstreamConsumer(upstream);
        CertificateSerial existingIdSerial = new CertificateSerial(101L, new Date(0L));
        CertificateSerial existingScaSerial = new CertificateSerial(102L, new Date(0L));
        when(this.serialCurator.get(101L)).thenReturn(existingIdSerial);
        when(this.serialCurator.get(102L)).thenReturn(existingScaSerial);

        this.installer.install(owner, this.identity, this.sca);

        IdentityCertificate installedId = upstream.getIdCert();
        assertNotNull(installedId);
        assertSame(existingIdSerial, installedId.getSerial());
        assertEquals(101L, installedId.getSerial().getId());
        assertEquals(this.identity.getSerial().getExpiration(), existingIdSerial.getExpiration());
        verify(this.serialCurator).merge(existingIdSerial);
        verify(this.identityCertificateCurator).create(installedId);

        SCACertificate installedSca = upstream.getContentAccessCert();
        assertNotNull(installedSca);
        assertSame(existingScaSerial, installedSca.getSerial());
        assertEquals(102L, installedSca.getSerial().getId());
        assertEquals(this.sca.getSerial().getExpiration(), existingScaSerial.getExpiration());
        verify(this.serialCurator).merge(existingScaSerial);
        verify(this.contentAccessCertificateCurator).create(installedSca);

        verify(this.ownerCurator).merge(owner);
    }

    @Test
    void installRejectsMissingOwnerOrUpstreamConsumer() {
        assertThrows(SyncDataFormatException.class,
            () -> this.installer.install(null, this.identity, this.sca));
        assertThrows(SyncDataFormatException.class,
            () -> this.installer.install(new Owner().setId("owner"), this.identity, this.sca));
    }

    private X509Certificate createCertificate(KeyPair keyPair, String commonName, Scheme scheme,
        long serialId) throws Exception {
        X500Name distinguishedName = new X500NameBuilder()
            .addRDN(BCStyle.CN, commonName)
            .build();
        Instant now = Instant.now();
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
            distinguishedName,
            BigInteger.valueOf(serialId),
            Date.from(now.minus(1, ChronoUnit.HOURS)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            distinguishedName,
            SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
        ContentSigner signer = new JcaContentSignerBuilder(scheme.signatureAlgorithm())
            .setProvider(CryptoUtil.getSecurityProvider())
            .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
            .setProvider(CryptoUtil.getSecurityProvider())
            .getCertificate(certBuilder.build(signer));
    }
}
