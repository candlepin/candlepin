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
package org.candlepin.pki.certs;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.PemEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;


/**
 * This generator is responsible for generation of consumer identity certificates.
 */
@Singleton
public class IdentityCertificateGenerator {
    private static final Logger log = LoggerFactory.getLogger(IdentityCertificateGenerator.class);
    private final KeyPairGenerator keyPairGenerator;
    private final PemEncoder pemEncoder;
    private final IdentityCertificateCurator idCertCurator;
    private final CertificateSerialCurator serialCurator;
    private final Provider<X509CertificateBuilder> certBuilder;
    private final int yearAddendum;

    @Inject
    public IdentityCertificateGenerator(
        Configuration config,
        PemEncoder pemEncoder,
        KeyPairGenerator keyPairGenerator,
        IdentityCertificateCurator identityCertCurator,
        CertificateSerialCurator serialCurator,
        Provider<X509CertificateBuilder> certBuilder) {
        this.keyPairGenerator = Objects.requireNonNull(keyPairGenerator);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.idCertCurator = Objects.requireNonNull(identityCertCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.certBuilder = Objects.requireNonNull(certBuilder);
        this.yearAddendum = config.getInt(ConfigProperties.IDENTITY_CERT_YEAR_ADDENDUM);
    }

    /**
     * Method creates identity certificate. If certificate already exists
     * the cached one is returned otherwise it creates a new one.
     *
     * @param consumer A consumer for which to create certificate
     * @return Identity certificate
     */
    public IdentityCertificate generate(Consumer consumer) {
        return generateCertificate(consumer, false);
    }

    /**
     * Method creates identity certificate. If certificate already exists
     * it is deleted and a new one is created.
     *
     * @param consumer A consumer for which to create certificate
     * @return Identity certificate
     */
    public IdentityCertificate regenerate(Consumer consumer) {
        return generateCertificate(consumer, true);
    }

    private IdentityCertificate generateCertificate(Consumer consumer, boolean regenerate) {
        IdentityCertificate certificate = null;

        if (consumer.getIdCert() != null) {
            certificate = this.idCertCurator.get(consumer.getIdCert().getId());
        }

        if (certificate != null) {
            if (regenerate) {
                consumer.setIdCert(null);
                this.idCertCurator.delete(certificate);
            }
            else {
                return certificate;
            }
        }

        IdentityCertificate newCertificate = this.createCertificate(consumer);
        consumer.setIdCert(newCertificate);

        return newCertificate;
    }

    private IdentityCertificate createCertificate(Consumer consumer) {
        log.debug("Generating identity cert for consumer: {}", consumer.getUuid());
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Instant from = now.minusHours(1).toInstant();
        Instant to = now.plusYears(this.yearAddendum).toInstant();
        DistinguishedName dn = new DistinguishedName(consumer.getUuid(), consumer.getOwner());
        CertificateSerial serial = new CertificateSerial(Date.from(to));
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        this.serialCurator.create(serial);
        KeyPair keyPair = this.keyPairGenerator.getKeyPair(consumer);

        X509Certificate certificate = this.certBuilder.get()
            .withDN(dn)
            .withValidity(from, to)
            .withSerial(serial.getSerial())
            .withKeyPair(keyPair)
            .withSubjectAltName(consumer.getName())
            .build();

        IdentityCertificate identityCertificate = new IdentityCertificate();
        identityCertificate.setCert(this.pemEncoder.encodeAsString(certificate));
        identityCertificate.setKey(this.pemEncoder.encodeAsString(keyPair.getPrivate()));
        identityCertificate.setSerial(serial);

        return this.idCertCurator.create(identityCertificate);
    }

}
