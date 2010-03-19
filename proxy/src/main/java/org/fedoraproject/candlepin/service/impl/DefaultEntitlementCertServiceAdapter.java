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

import java.math.BigInteger;
import java.security.KeyPair;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.pki.BouncyCastlePKI;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerEntitlementCertificate;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;

import com.google.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter implements 
    EntitlementCertServiceAdapter {
    private BouncyCastlePKI pki;
    private static Logger log = Logger
        .getLogger(DefaultEntitlementCertServiceAdapter.class);
    
    @Inject
    public DefaultEntitlementCertServiceAdapter(BouncyCastlePKI pki) {
        this.pki = pki;
    }

    @Override
    public ConsumerEntitlementCertificate generateEntitlementCert(Consumer consumer,
        Subscription sub, Product product, Date endDate, KeyPair keypair, 
        BigInteger serialNumber) throws GeneralSecurityException, IOException {
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: " + consumer.getUuid());
        log.debug("   product: " + product.getId());
        log.debug("   end date: " + endDate);
        
        X509Certificate x509Cert = this.pki.createX509Certificate(createDN(consumer), 
            null, sub.getStartDate(), endDate, serialNumber);
        
        ConsumerEntitlementCertificate cert = new ConsumerEntitlementCertificate();
        cert.setSerialNumber(serialNumber);
        cert.setKey(x509Cert.getPublicKey().getEncoded());
        cert.setPem(this.pki.getPemEncoded(x509Cert));
        
        return cert;
    }
    
    private String createDN(Consumer consumer) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getName());
        sb.append(", UID=");
        sb.append(consumer.getUuid());
        return sb.toString();
    }

}
