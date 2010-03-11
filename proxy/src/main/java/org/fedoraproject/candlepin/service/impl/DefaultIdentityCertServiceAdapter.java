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
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.cert.BouncyCastlePKI;
import org.fedoraproject.candlepin.cert.X509ExtensionWrapper;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
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

    public void deleteIdentityCert(Consumer consumer) {
        ConsumerIdentityCertificate certificate = consumerIdentityCertificateCurator
            .find(consumer.getId());
        if (certificate != null) {
            consumerIdentityCertificateCurator.delete(certificate);
        }
    }

    @Override
    public ConsumerIdentityCertificate generateIdentityCert(Consumer consumer)
        throws GeneralSecurityException, IOException {
        log.debug("Generating identity cert for consumer: " +
            consumer.getUuid());
        Date startDate = new Date();
        Date endDate = getFutureDate(1);

        ConsumerIdentityCertificate certificate = consumerIdentityCertificateCurator
            .find(consumer.getId());

        if (certificate != null) {
            return certificate;
        }
        final List<X509ExtensionWrapper> extensions = Collections.emptyList();
        BigInteger serialNumber = BigInteger.valueOf(random.nextInt(1000000));
        while (consumerIdentityCertificateCurator
            .lookupBySerialNumber(serialNumber) != null) {
            serialNumber = BigInteger.valueOf(random.nextLong());
        }

        X509Certificate x509cert = this.pki.createX509Certificate(consumer
            .getUuid(), extensions, startDate, endDate, serialNumber);

        ConsumerIdentityCertificate identityCert = new ConsumerIdentityCertificate();
        identityCert.setPem(x509cert.getEncoded());
        identityCert.setKey(x509cert.getPublicKey().getEncoded());
        identityCert.setSerialNumber(serialNumber);

        identityCert = consumerIdentityCertificateCurator.create(identityCert);

        return identityCert;
    }

    private Date getFutureDate(int years) {
        Calendar future = Calendar.getInstance();
        future.setTime(new Date());
        future.set(Calendar.YEAR, future.get(Calendar.YEAR) + years);

        return future.getTime();
    }
}
