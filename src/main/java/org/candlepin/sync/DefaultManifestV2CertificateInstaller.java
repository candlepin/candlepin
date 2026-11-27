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
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.Scheme;
import org.candlepin.service.model.CertificateInfo;
import org.candlepin.service.model.CertificateSerialInfo;

import org.apache.commons.lang3.StringUtils;
import org.xnap.commons.i18n.I18n;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import jakarta.inject.Inject;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * Validates version 2 manifest certificate ownership, key matching, and exact serial consistency
 * <p>
 * Enforces that both the identity certificate and SCA certificate belong to the exported upstream consumer,
 * that their private keys match the public keys in the certificates, and that certificate serials match
 * the exact upstream X.509 serial numbers. Preserving exact serials ensures CRL lookups and revocations
 * remain cryptographically valid.
 */
public class DefaultManifestV2CertificateInstaller implements ManifestV2CertificateInstaller {
    private final CertificateReader certificateReader;
    private final PrivateKeyReader privateKeyReader;
    private final CryptoManager cryptoManager;
    private final IdentityCertificateCurator identityCertificateCurator;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final CertificateSerialCurator serialCurator;
    private final OwnerCurator ownerCurator;
    private final I18n i18n;

    @Inject
    public DefaultManifestV2CertificateInstaller(CertificateReader certificateReader,
        PrivateKeyReader privateKeyReader, CryptoManager cryptoManager,
        IdentityCertificateCurator identityCertificateCurator,
        ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator serialCurator, OwnerCurator ownerCurator, I18n i18n) {
        this.certificateReader = Objects.requireNonNull(certificateReader);
        this.privateKeyReader = Objects.requireNonNull(privateKeyReader);
        this.cryptoManager = Objects.requireNonNull(cryptoManager);
        this.identityCertificateCurator = Objects.requireNonNull(identityCertificateCurator);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.i18n = Objects.requireNonNull(i18n);
    }

    @Override
    public void validate(ConsumerDTO consumer, CertificateInfo identity, CertificateInfo sca)
        throws ImporterException {
        if (consumer == null || StringUtils.isBlank(consumer.getUuid())) {
            throw new SyncDataFormatException(this.i18n.tr("Upstream consumer is missing or lacks UUID"));
        }
        if (identity == null || StringUtils.isBlank(identity.getCertificate()) ||
            StringUtils.isBlank(identity.getKey())) {
            throw new SyncDataFormatException(this.i18n.tr("Missing or incomplete v2 identity certificate material"));
        }
        if (sca == null || StringUtils.isBlank(sca.getCertificate()) ||
            StringUtils.isBlank(sca.getKey())) {
            throw new SyncDataFormatException(this.i18n.tr("Missing or incomplete v2 SCA certificate material"));
        }

        X509Certificate identityCert;
        PrivateKey identityKey;
        X509Certificate scaCert;
        PrivateKey scaKey;

        try {
            identityCert = this.certificateReader.read(new ByteArrayInputStream(
                identity.getCertificate().getBytes(StandardCharsets.UTF_8)));
            identityKey = this.privateKeyReader.read(new ByteArrayInputStream(
                identity.getKey().getBytes(StandardCharsets.UTF_8)), null);
            scaCert = this.certificateReader.read(new ByteArrayInputStream(
                sca.getCertificate().getBytes(StandardCharsets.UTF_8)));
            scaKey = this.privateKeyReader.read(new ByteArrayInputStream(
                sca.getKey().getBytes(StandardCharsets.UTF_8)), null);
        }
        catch (CertificateException | KeyException | RuntimeException e) {
            // Keep keys out of logs, exception messages, and cause chains
            throw new SyncDataFormatException(this.i18n.tr("Invalid v2 certificate or private key material"));
        }

        this.validateKeyMatching(identityCert, identityKey);
        this.validateKeyMatching(scaCert, scaKey);

        this.validateSerialMatching(identityCert, identity);
        this.validateSerialMatching(scaCert, sca);

        this.validateOwnership(identityCert, consumer.getUuid());
        this.validateOwnership(scaCert, consumer.getUuid());
    }

    private void validateSerialMatching(X509Certificate certificate, CertificateInfo info)
        throws SyncDataFormatException {
        BigInteger expectedSerial = this.extractExpectedSerial(info);
        BigInteger certSerial = certificate.getSerialNumber();
        if (!certSerial.equals(expectedSerial)) {
            throw new SyncDataFormatException(
                this.i18n.tr("Certificate serial does not match certificate metadata"));
        }
    }

    private BigInteger extractExpectedSerial(CertificateInfo info) throws SyncDataFormatException {
        if (info.getSerial() == null) {
            throw new SyncDataFormatException(this.i18n.tr("Certificate serial metadata is missing"));
        }
        BigInteger expectedSerial = info.getSerial().getSerial();
        if (expectedSerial == null && info.getSerial() instanceof CertificateSerialDTO dto &&
            dto.getId() != null) {
            expectedSerial = BigInteger.valueOf(dto.getId());
        }
        if (expectedSerial == null) {
            throw new SyncDataFormatException(this.i18n.tr("Certificate serial metadata is missing"));
        }
        return expectedSerial;
    }

    private void validateKeyMatching(X509Certificate certificate, PrivateKey privateKey)
        throws SyncDataFormatException {
        PublicKey publicKey = certificate.getPublicKey();
        if (!publicKey.getAlgorithm().equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw new SyncDataFormatException(
                this.i18n.tr("Certificate public key algorithm does not match private key"));
        }

        Scheme scheme = this.findScheme(publicKey.getAlgorithm());

        try {
            byte[] challenge = "candlepin-v2-key-matching-challenge".getBytes(StandardCharsets.UTF_8);

            Signature signer = Signature.getInstance(scheme.signatureAlgorithm(),
                this.cryptoManager.getSecurityProvider());
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance(scheme.signatureAlgorithm(),
                this.cryptoManager.getSecurityProvider());
            verifier.initVerify(publicKey);
            verifier.update(challenge);

            if (!verifier.verify(signature)) {
                throw new SyncDataFormatException(this.i18n.tr("Private key does not match certificate public key"));
            }
        }
        catch (GeneralSecurityException | RuntimeException e) {
            throw new SyncDataFormatException(this.i18n.tr("Private key does not match certificate public key"));
        }
    }

    private Scheme findScheme(String keyAlgorithm) throws SyncDataFormatException {
        return this.cryptoManager.getCryptoSchemes().stream()
            .filter(scheme -> scheme.keyAlgorithm().equalsIgnoreCase(keyAlgorithm))
            .findFirst()
            .orElseThrow(() -> new SyncDataFormatException(
                this.i18n.tr("Unsupported certificate key algorithm: {0}", keyAlgorithm)));
    }

    private void validateOwnership(X509Certificate certificate, String expectedConsumerUuid)
        throws SyncDataFormatException {
        String commonName = this.extractCommonName(certificate);
        if (commonName == null || !commonName.equals(expectedConsumerUuid)) {
            throw new SyncDataFormatException(
                this.i18n.tr("Certificate does not belong to exported upstream consumer"));
        }
    }

    private String extractCommonName(X509Certificate certificate) {
        try {
            LdapName ldapName = new LdapName(certificate.getSubjectX500Principal().getName());
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        }
        catch (InvalidNameException e) {
            return null;
        }
        return null;
    }

    @Override
    public void install(Owner owner, CertificateInfo identity, CertificateInfo sca) throws ImporterException {
        if (owner == null || owner.getUpstreamConsumer() == null) {
            throw new SyncDataFormatException(this.i18n.tr("Upstream consumer is missing"));
        }
        if (identity == null || StringUtils.isBlank(identity.getCertificate()) ||
            StringUtils.isBlank(identity.getKey())) {
            throw new SyncDataFormatException(this.i18n.tr("Missing or incomplete v2 identity certificate material"));
        }
        if (sca == null || StringUtils.isBlank(sca.getCertificate()) ||
            StringUtils.isBlank(sca.getKey())) {
            throw new SyncDataFormatException(this.i18n.tr("Missing or incomplete v2 SCA certificate material"));
        }

        CertificateSerial idSerial = this.resolveOrPersistSerial(identity);
        CertificateSerial scaSerial = this.resolveOrPersistSerial(sca);

        IdentityCertificate idCert = new IdentityCertificate();
        idCert.setKey(identity.getKey());
        idCert.setCert(identity.getCertificate());
        idCert.setSerial(idSerial);
        if (identity instanceof CertificateDTO dto) {
            idCert.setCreated(dto.getCreated());
            idCert.setUpdated(dto.getUpdated());
        }
        idCert = this.identityCertificateCurator.create(idCert);

        SCACertificate scaCert = new SCACertificate();
        scaCert.setKey(sca.getKey());
        scaCert.setCert(sca.getCertificate());
        scaCert.setSerial(scaSerial);
        if (sca instanceof CertificateDTO dto) {
            scaCert.setCreated(dto.getCreated());
            scaCert.setUpdated(dto.getUpdated());
        }
        scaCert = this.contentAccessCertificateCurator.create(scaCert);

        UpstreamConsumer upstream = owner.getUpstreamConsumer();
        upstream.setIdCert(idCert);
        upstream.setContentAccessCert(scaCert);
        this.ownerCurator.merge(owner);
    }

    private CertificateSerial resolveOrPersistSerial(CertificateInfo info) throws SyncDataFormatException {
        BigInteger expectedSerial = this.extractExpectedSerial(info);
        long serialId;
        try {
            serialId = expectedSerial.longValueExact();
        }
        catch (ArithmeticException e) {
            throw new SyncDataFormatException(this.i18n.tr("Certificate serial is out of range"));
        }
        if (serialId <= 0L) {
            throw new SyncDataFormatException(this.i18n.tr("Certificate serial metadata is missing"));
        }

        CertificateSerialInfo serialInfo = info.getSerial();
        CertificateSerial serial = this.serialCurator.get(serialId);
        if (serial == null) {
            serial = new CertificateSerial(serialId, serialInfo.getExpiration());
            serial.setRevoked(serialInfo.isRevoked());
            if (serialInfo instanceof CertificateSerialDTO dto) {
                serial.setCreated(dto.getCreated());
                serial.setUpdated(dto.getUpdated());
            }
            return this.serialCurator.create(serial);
        }

        serial.setExpiration(serialInfo.getExpiration());
        serial.setRevoked(serialInfo.isRevoked());
        if (serialInfo instanceof CertificateSerialDTO dto) {
            if (dto.getCreated() != null) {
                serial.setCreated(dto.getCreated());
            }
            if (dto.getUpdated() != null) {
                serial.setUpdated(dto.getUpdated());
            }
        }
        return this.serialCurator.merge(serial);
    }
}
