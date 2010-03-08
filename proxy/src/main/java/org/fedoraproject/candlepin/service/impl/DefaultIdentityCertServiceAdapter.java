/**
 * Copyright (c) 2010 Red Hat, Inc.
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

import com.google.inject.Inject;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import org.fedoraproject.candlepin.cert.util.BouncyCastlePKI;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificate;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
import org.fedoraproject.candlepin.service.IdentityCertServiceAdapter;

public class DefaultIdentityCertServiceAdapter implements IdentityCertServiceAdapter {

    private BouncyCastlePKI pki;
    private ConsumerIdentityCertificateCurator consumerIdentityCertificateCurator;
    
    
    @Inject
    public DefaultIdentityCertServiceAdapter(BouncyCastlePKI pki, ConsumerIdentityCertificateCurator consumerIdentityCertificateCurator) {
        this.pki = pki;
        this.consumerIdentityCertificateCurator = consumerIdentityCertificateCurator;
    }

    @Override
    public ConsumerIdentityCertificate generateIdentityCert(Consumer consumer) {
        try {
            Date startDate = new Date();
            Date endDate = getFutureDate(1);


            ConsumerIdentityCertificate certificate = consumerIdentityCertificateCurator.find(consumer.getId());
            
            if (certificate != null) return certificate;
            
            
            BigInteger serialNumber = BigInteger.valueOf(36208234);
            X509Certificate x509cert = this.pki.createX509Certificate(consumer.getUuid(), null, startDate, endDate, serialNumber);

            ConsumerIdentityCertificate identityCert = new ConsumerIdentityCertificate();
            identityCert.setPem(x509cert.getEncoded());
            identityCert.setKey(x509cert.getPublicKey().getEncoded());

            identityCert = consumerIdentityCertificateCurator.create(identityCert);
            
            return identityCert;
        } catch (Exception e) {
            return null;
        }
    }

    private Date getFutureDate(int years) {
        Calendar future = Calendar.getInstance();
        future.setTime(new Date());
        future.set(Calendar.YEAR, future.get(Calendar.YEAR) + years);

        return future.getTime();
    }
}