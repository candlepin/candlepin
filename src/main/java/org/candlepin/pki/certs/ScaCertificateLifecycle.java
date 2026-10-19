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
package org.candlepin.pki.certs;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.SCACertificate;
import org.candlepin.pki.CryptoCapabilitiesException;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.util.ConsumerKeyPairGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyException;
import java.security.KeyPair;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Persistence, reuse, and renewal of SCA X.509 certificates
 */
@Singleton
public class ScaCertificateLifecycle {
    private static final Logger log = LoggerFactory.getLogger(ScaCertificateLifecycle.class);

    private final PemEncoder pemEncoder;
    private final ConsumerKeyPairGenerator keyPairGenerator;
    private final ScaCertificateDataProvider dataProvider;
    private final ScaCertificateEncoder encoder;
    private final ContentAccessCertificateCurator contentAccessCertificateCurator;
    private final CertificateSerialCurator serialCurator;
    private final int x509CertExpirationThreshold;

    @Inject
    public ScaCertificateLifecycle(Configuration configuration, PemEncoder pemEncoder,
        ConsumerKeyPairGenerator keyPairGenerator, ScaCertificateDataProvider dataProvider,
        ScaCertificateEncoder encoder, ContentAccessCertificateCurator contentAccessCertificateCurator,
        CertificateSerialCurator serialCurator) {

        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.dataProvider = Objects.requireNonNull(dataProvider);
        this.encoder = Objects.requireNonNull(encoder);
        this.contentAccessCertificateCurator = Objects.requireNonNull(contentAccessCertificateCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.x509CertExpirationThreshold = Objects.requireNonNull(configuration)
            .getInt(ConfigProperties.SCA_X509_CERT_EXPIRY_THRESHOLD);
    }

    private boolean hasCertificateExpired(SCACertificate certificate) {
        CertificateSerial serial = certificate.getSerial();

        Date regenerationCutoff = Date.from(Instant.now()
            .plus(this.x509CertExpirationThreshold, ChronoUnit.DAYS));

        return serial == null ||
            serial.getExpiration() == null ||
            regenerationCutoff.after(serial.getExpiration());
    }

    private boolean hasCertificatePrivateKeyChanged(SCACertificate certificate, byte[] privateKeyBytes) {
        byte[] currentKeyBytes = certificate.getKeyAsBytes();


        return currentKeyBytes == null ||
            !Arrays.equals(currentKeyBytes, privateKeyBytes);
    }

    /**
     * Retrieves or regenerates a consumer certificate while maintaining its persisted serial and key
     *
     * @return persisted SCA certificate without an appended content payload
     */
    /* pkg-private */ SCACertificate getCertificate(Scheme scheme, Consumer consumer,
        List<Environment> environments) throws CryptoCapabilitiesException, KeyException {

        Objects.requireNonNull(scheme);
        Objects.requireNonNull(consumer);
        Objects.requireNonNull(environments);
        SCACertificate scaCertificate = consumer.getContentAccessCert();
        if (scaCertificate == null) {
            log.info("Generating new SCA certificate key for consumer: \"{}\"", consumer.getUuid());

            scaCertificate = new SCACertificate();
            scaCertificate.setConsumer(consumer);
            scaCertificate.setKey("");  // we'll correct these later
            scaCertificate.setCert("");

            scaCertificate = this.contentAccessCertificateCurator.create(scaCertificate);
            consumer.setContentAccessCert(scaCertificate);
        }

        KeyPair keypair = this.keyPairGenerator.getConsumerKeyPair(consumer);
        byte[] privateKeyBytes = this.pemEncoder.encodeAsBytes(keypair.getPrivate());

        if (this.hasCertificateExpired(scaCertificate) ||
            this.hasCertificatePrivateKeyChanged(scaCertificate, privateKeyBytes)) {

            log.info("Generating new SCA x509 certificate for consumer: \"{}\"", consumer.getUuid());

            CertificateSerial serial = scaCertificate.getSerial();
            if (serial != null) {
                this.serialCurator.revokeById(serial.getId());
            }

            OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
            OffsetDateTime end = start.plusYears(1L);

            serial = this.createSerial(end);

            String certificate = this.encoder.encode(this.dataProvider.prepare(consumer, environments), scheme,
                serial.getSerial(), keypair, start.toInstant(), end.toInstant());

            scaCertificate.setSerial(serial);
            scaCertificate.setKeyAsBytes(privateKeyBytes);
            scaCertificate.setCert(certificate);
        }

        return scaCertificate;
    }

    /**
     * Creates an organization-level export artifact without replacing the consumer certificate
     *
     * @return export certificate with a persisted serial
     */
    /* pkg-private */ SCACertificate createExportV2Certificate(Scheme scheme, Consumer consumer)
        throws CryptoCapabilitiesException, KeyException {

        KeyPair keypair = this.keyPairGenerator.getConsumerKeyPair(consumer);
        OffsetDateTime start = OffsetDateTime.now().minusHours(1L);
        OffsetDateTime end = start.plusYears(1L);
        CertificateSerial serial = this.createSerial(end);
        SCACertificate certificate = new SCACertificate();
        certificate.setSerial(serial);
        certificate.setKeyAsBytes(this.pemEncoder.encodeAsBytes(keypair.getPrivate()));
        certificate.setCert(this.encoder.encode(this.dataProvider.prepare(consumer, List.of()), scheme,
            serial.getSerial(), keypair, start.toInstant(), end.toInstant()));
        return certificate;
    }

    private CertificateSerial createSerial(OffsetDateTime end) {
        CertificateSerial serial = new CertificateSerial(Date.from(end.toInstant()));
        // We need the sequence generated id before we create the Certificate,
        // otherwise we could have used cascading create
        return this.serialCurator.create(serial);
    }

}
