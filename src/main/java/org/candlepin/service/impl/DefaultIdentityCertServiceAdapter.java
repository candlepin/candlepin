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

import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.IdentityCertificateCurator;
import org.candlepin.model.KeyPairCurator;
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
import java.util.Date;

/**
 * DefaultIdentityCertServiceAdapter
 */
public class DefaultIdentityCertServiceAdapter implements
    IdentityCertServiceAdapter {
    private PKIUtility pki;
    private static Logger log =
        LoggerFactory.getLogger(DefaultIdentityCertServiceAdapter.class);
    private IdentityCertificateCurator idCertCurator;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;
    private Function<Date, Date> endDateGenerator;

    @SuppressWarnings("unchecked")
    @Inject
    public DefaultIdentityCertServiceAdapter(PKIUtility pki,
        IdentityCertificateCurator identityCertCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator,
        @Named("endDateGenerator") Function endDtGen) {
        this.pki = pki;
        this.idCertCurator = identityCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
        this.endDateGenerator = endDtGen;
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        IdentityCertificate certificate = idCertCurator
            .find(consumer.getIdCert().getId());
        if (certificate != null) {
            idCertCurator.delete(certificate);
        }
    }

    @Override
    public IdentityCertificate generateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {

        if (log.isDebugEnabled()) {
            log.debug("Generating identity cert for consumer: " +
                consumer.getUuid());
        }

        IdentityCertificate certificate = null;

        if (consumer.getIdCert() != null) {
            certificate = idCertCurator.find(consumer.getIdCert().getId());
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
            certificate = idCertCurator.find(consumer.getIdCert().getId());
        }

        if (certificate != null) {
            consumer.setIdCert(null);
            idCertCurator.delete(certificate);
        }

        return generate(consumer);
    }

    private IdentityCertificate generate(Consumer consumer)
        throws GeneralSecurityException, IOException {
        Date startDate = new Date();
        Date endDate = this.endDateGenerator.apply(startDate);

        CertificateSerial serial = new CertificateSerial(endDate);
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);

        String dn = createDN(consumer);
        IdentityCertificate identityCert = new IdentityCertificate();
        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
        X509Certificate x509cert = pki.createX509Certificate(dn, null, null,
            startDate, endDate, keyPair, BigInteger.valueOf(serial.getId()),
            consumer.getName());

        identityCert.setCert(new String(pki.getPemEncoded(x509cert)));
        identityCert.setKey(new String(pki.getPemEncoded(keyPair.getPrivate())));
        identityCert.setSerial(serial);
        identityCert.setConsumer(consumer);
        consumer.setIdCert(identityCert);

        return idCertCurator.create(identityCert);
    }

    private String createDN(Consumer consumer) {
        StringBuilder sb = new StringBuilder("CN=");

        sb.append(consumer.getUuid());
        sb.append(",O=" + consumer.getOwner().getKey());


        return sb.toString();
    }
}
