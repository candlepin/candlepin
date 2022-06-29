/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateCurator;
import org.candlepin.model.Consumer;
import org.candlepin.pki.PKIUtility;
import org.candlepin.service.IdentityCertServiceAdapter;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;



/**
 * DefaultIdentityCertServiceAdapter
 */
public class DefaultIdentityCertServiceAdapter implements IdentityCertServiceAdapter {
    private static Logger log = LoggerFactory.getLogger(DefaultIdentityCertServiceAdapter.class);

    private final PKIUtility pki;
    private final CertificateCurator certificateCurator;
    private final Function<Date, Date> endDateGenerator;

    @Inject
    @SuppressWarnings("unchecked")
    public DefaultIdentityCertServiceAdapter(PKIUtility pki, CertificateCurator certificateCurator,
        @Named("endDateGenerator") Function endDtGen) {

        this.pki = Objects.requireNonNull(pki);
        this.certificateCurator = Objects.requireNonNull(certificateCurator);
        this.endDateGenerator = Objects.requireNonNull(endDtGen); // Why did we abstract this out like this?
    }

    private Certificate resolveCertificate(Certificate certificate) {
        return certificate != null ? this.certificateCurator.get(certificate.getId()) : null;
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        if (consumer.getIdCert() == null) {
            log.warn("Unable to delete null identity cert for consumer: {}", consumer.getUuid());
            return;
        }

        Certificate certificate = this.resolveCertificate(consumer.getIdCert());
        if (certificate != null) {
            this.certificateCurator.delete(certificate);
        }
    }

    @Override
    public Certificate generateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {

        log.debug("Generating identity cert for consumer: {}", consumer.getUuid());

        Certificate certificate = this.resolveCertificate(consumer.getIdCert());
        return certificate != null ? certificate : this.generate(consumer);
    }

    @Override
    public Certificate regenerateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {

        Certificate certificate = this.resolveCertificate(consumer.getIdCert());

        if (certificate != null) {
            consumer.setIdCert(null);
            this.certificateCurator.delete(certificate);
        }

        return this.generate(consumer);
    }

    private Certificate generate(Consumer consumer) throws GeneralSecurityException, IOException {
        Instant startDate = Instant.now()
            .truncatedTo(ChronoUnit.HOURS)
            .minus(1, ChronoUnit.HOURS);

        Date endDate = this.endDateGenerator.apply(new Date());

        BigInteger serial = this.pki.generateCertificateSerial();
        KeyPair keyPair = this.pki.getConsumerKeyPair(consumer);
        X509Certificate x509Cert = this.pki.createX509Certificate(this.createDN(consumer), null, null,
            Date.from(startDate), endDate, keyPair, serial, consumer.getName());

        Certificate certificate = new Certificate()
            .setType(Certificate.Type.IDENTITY)
            .setSerial(serial)
            .setCertificate(this.pki.getPemEncoded(x509Cert))
            .setPrivateKey(this.pki.getPemEncoded(keyPair.getPrivate()))
            // Start date?
            .setExpiration(x509Cert.getNotAfter().toInstant());

        this.certificateCurator.create(certificate);
        consumer.setIdCert(certificate);

        return certificate;
    }

    private String createDN(Consumer consumer) {
        return new StringBuilder("CN=")
            .append(consumer.getUuid())
            .append(", O=")
            .append(consumer.getOwner().getKey())
            .toString();
    }
}
