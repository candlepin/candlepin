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
import java.util.Random;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
import org.fedoraproject.candlepin.pki.BouncyCastlePKI;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;

import com.google.inject.Inject;

/**
 * DefaultIdentityCertServiceAdapter
 */
public class DefaultIdentityCertServiceAdapter implements
    IdentityCertServiceAdapter {
    private BouncyCastlePKI pki;
    private static Logger log = Logger
        .getLogger(DefaultIdentityCertServiceAdapter.class);
    private ConsumerIdentityCertificateCurator consumerIdentityCertificateCurator;
    // Seeded with this(System.currentTimeMillis()
    private Random random = new Random();

    @Inject
    public DefaultIdentityCertServiceAdapter(BouncyCastlePKI pki,
        ConsumerIdentityCertificateCurator consumerIdentityCertificateCurator) {
        this.pki = pki;
        this.consumerIdentityCertificateCurator = consumerIdentityCertificateCurator;
    }

    @Override
    public void deleteIdentityCert(Consumer consumer) {
        ConsumerIdentityCertificate certificate = consumerIdentityCertificateCurator
            .find(consumer.getId());
        if (certificate != null) {
            consumerIdentityCertificateCurator.delete(certificate);
        }
    }

    @Override
    public ConsumerIdentityCertificate generateIdentityCert(Consumer consumer,
        String username) throws GeneralSecurityException, IOException {
        log.debug("Generating identity cert for consumer: " +
            consumer.getUuid());
        Date startDate = new Date();
        Date endDate = getFutureDate(1);

        ConsumerIdentityCertificate certificate = consumerIdentityCertificateCurator
            .find(consumer.getId());

        if (certificate != null) {
            return certificate;
        }

        BigInteger serialNumber = nextSerialNumber();
        String dn = createDN(consumer, username);
        ConsumerIdentityCertificate identityCert = new ConsumerIdentityCertificate();
        KeyPair keyPair = pki.generateNewKeyPair();
        X509Certificate x509cert = pki.createX509Certificate(dn, null,
            startDate, endDate, keyPair, serialNumber);
        
        identityCert.setPem(pki.getPemEncoded(x509cert));
        identityCert.setKey(pki.toPem(keyPair.getPrivate()));
        identityCert.setSerialNumber(x509cert.getSerialNumber());

        return consumerIdentityCertificateCurator.create(identityCert);
    }

    private BigInteger nextSerialNumber() {
        BigInteger serialNumber = BigInteger.valueOf(random.nextInt(1000000));
        while (consumerIdentityCertificateCurator
            .lookupBySerialNumber(serialNumber) != null) {
            serialNumber = BigInteger.valueOf(random.nextLong());
        }
        return serialNumber;
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
