/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.service.impl;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.IdentityCertificate;
import org.fedoraproject.candlepin.model.IdentityCertificateCurator;
import org.fedoraproject.candlepin.model.KeyPairCurator;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;

import com.google.inject.Inject;

/**
 * DefaultIdentityCertServiceAdapter
 */
public class DefaultIdentityCertServiceAdapter implements
    IdentityCertServiceAdapter {
    private PKIUtility pki;
    private static Logger log = Logger
        .getLogger(DefaultIdentityCertServiceAdapter.class);
    private IdentityCertificateCurator identityCertCurator;
    private KeyPairCurator keyPairCurator;
    private CertificateSerialCurator serialCurator;

    @Inject
    public DefaultIdentityCertServiceAdapter(PKIUtility pki,
        IdentityCertificateCurator identityCertCurator,
        KeyPairCurator keyPairCurator,
        CertificateSerialCurator serialCurator) {
        this.pki = pki;
        this.identityCertCurator = identityCertCurator;
        this.keyPairCurator = keyPairCurator;
        this.serialCurator = serialCurator;
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        IdentityCertificate certificate = identityCertCurator
            .find(consumer.getIdCert().getId());
        if (certificate != null) {
            identityCertCurator.delete(certificate);
        }
    }

    @Override
    public IdentityCertificate generateIdentityCert(Consumer consumer,
        String username) throws GeneralSecurityException, IOException {
        log.debug("Generating identity cert for consumer: " +
            consumer.getUuid());
        Date startDate = new Date();
        Date endDate = getFutureDate(1);

        IdentityCertificate certificate = identityCertCurator
            .find(consumer.getId());

        if (certificate != null) {
            return certificate;
        }

        CertificateSerial serial = new CertificateSerial(endDate);
        // We need the sequence generated id before we create the EntitlementCertificate,
        // otherwise we could have used cascading create
        serialCurator.create(serial);
        
        String dn = createDN(consumer, username);
        IdentityCertificate identityCert = new IdentityCertificate();
        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
        X509Certificate x509cert = pki.createX509Certificate(dn, null,
            startDate, endDate, keyPair, BigInteger.valueOf(serial.getId()));
        
        identityCert.setCert(new String(pki.getPemEncoded(x509cert)));
        identityCert.setKey(new String(pki.getPemEncoded(keyPair.getPrivate())));
        identityCert.setSerial(serial);
        identityCert.setConsumer(consumer);
        consumer.setIdCert(identityCert);

        return identityCertCurator.create(identityCert);
    }

    private String createDN(Consumer consumer, String username) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getName());
        sb.append(", UID=");
        sb.append(consumer.getUuid());
        sb.append(", OU=");
        sb.append(username);

        return sb.toString();
    }

    private Date getFutureDate(int years) {
        Calendar future = Calendar.getInstance();
        future.setTime(new Date());
        future.set(Calendar.YEAR, future.get(Calendar.YEAR) + years);

        return future.getTime();
    }
}
