/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.impl;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.pki.DistinguishedName;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.PemEncoder;
import org.candlepin.pki.certs.X509CertificateBuilder;
import org.candlepin.service.IdentityCertServiceAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;


public class DefaultIdentityCertServiceAdapter implements IdentityCertServiceAdapter {
    private static final Logger log = LoggerFactory.getLogger(DefaultIdentityCertServiceAdapter.class);
    private final PKIUtility pki;
    private final PemEncoder pemEncoder;
    private final IdentityCertificateCurator idCertCurator;
    private final CertificateSerialCurator serialCurator;
    private final Provider<X509CertificateBuilder> certBuilder;
    private final int yearAddendum;

    @Inject
    public DefaultIdentityCertServiceAdapter(
        Configuration config,
        PemEncoder pemEncoder,
        PKIUtility pki,
        IdentityCertificateCurator identityCertCurator,
        CertificateSerialCurator serialCurator,
        Provider<X509CertificateBuilder> certBuilder) {
        this.pki = Objects.requireNonNull(pki);
        this.pemEncoder = Objects.requireNonNull(pemEncoder);
        this.idCertCurator = Objects.requireNonNull(identityCertCurator);
        this.serialCurator = Objects.requireNonNull(serialCurator);
        this.certBuilder = Objects.requireNonNull(certBuilder);
        this.yearAddendum = config.getInt(ConfigProperties.IDENTITY_CERT_YEAR_ADDENDUM);
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        if (consumer.getIdCert() == null) {
            log.warn("Unable to delete null identity cert for consumer: {}", consumer.getUuid());
            return;
        }

        IdentityCertificate certificate = idCertCurator.get(consumer.getIdCert().getId());
        if (certificate != null) {
            idCertCurator.delete(certificate);
        }
    }

    @Override
    public IdentityCertificate generateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {

        if (log.isDebugEnabled()) {
            log.debug("Generating identity cert for consumer: {}", consumer.getUuid());
        }

        IdentityCertificate certificate = null;

        if (consumer.getIdCert() != null) {
            certificate = idCertCurator.get(consumer.getIdCert().getId());
        }

        if (certificate != null) {
            return certificate;
        }

        return generate(consumer);
    }

    @Override
    public IdentityCertificate regenerateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {

        IdentityCertificate certificate = null;

        if (consumer.getIdCert() != null) {
            certificate = idCertCurator.get(consumer.getIdCert().getId());
        }

        if (certificate != null) {
            consumer.setIdCert(null);
            idCertCurator.delete(certificate);
        }

        return generate(consumer);
    }

    private IdentityCertificate generate(Consumer consumer)
        throws GeneralSecurityException {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Instant from = now.minusHours(1).toInstant();
        Instant to = now.plusYears(this.yearAddendum).toInstant();
        DistinguishedName dn = new DistinguishedName(consumer.getUuid(), consumer.getOwner());
        CertificateSerial serial = new CertificateSerial(Date.from(to));
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        this.serialCurator.create(serial);
        KeyPair keyPair = this.pki.getConsumerKeyPair(consumer);

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
        consumer.setIdCert(identityCertificate);

        return idCertCurator.create(identityCertificate);
    }

}
