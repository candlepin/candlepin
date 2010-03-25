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
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerEntitlementCertificate;
import org.fedoraproject.candlepin.model.ConsumerEntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;

import com.google.inject.Inject;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter implements 
    EntitlementCertServiceAdapter {
    private PKIUtility pki;
    private static Logger log = Logger
        .getLogger(DefaultEntitlementCertServiceAdapter.class);
    private ConsumerEntitlementCertificateCurator entCertCurator;
    
    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki, 
        ConsumerEntitlementCertificateCurator entCertCurator) {
        
        this.pki = pki;
        this.entCertCurator = entCertCurator;
    }

    @Override
    public ConsumerEntitlementCertificate generateEntitlementCert(Consumer consumer,
        Entitlement entitlement, Subscription sub, Product product, Date endDate, 
        BigInteger serialNumber) throws GeneralSecurityException, IOException {
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: " + consumer.getUuid());
        log.debug("   product: " + product.getId());
        log.debug("   end date: " + endDate);
        
        // TODO: re-use keypair for the owner?
        KeyPair keyPair = pki.generateNewKeyPair();
        X509Certificate x509Cert = this.pki.createX509Certificate(createDN(consumer), 
            null, sub.getStartDate(), endDate, keyPair, serialNumber);
        
        ConsumerEntitlementCertificate cert = new ConsumerEntitlementCertificate();
        cert.setSerialNumber(serialNumber);
        cert.setKey(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCert(this.pki.getPemEncoded(x509Cert));
        cert.setEntitlement(entitlement);
        entitlement.getCertificates().add(cert);
        
        log.debug("Generated cert: " + serialNumber);
        log.debug("Key: " + cert.getKeyAsString());
        log.debug("Cert: " + cert.getCertAsString());
        entCertCurator.create(cert);
        
        return cert;
    }
    
    private String createDN(Consumer consumer) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getName());
        sb.append(", UID=");
        sb.append(consumer.getUuid());
        return sb.toString();
    }

    @Override
    public List<ConsumerEntitlementCertificate> listForConsumer(
        Consumer consumer) {
        return entCertCurator.listForConsumer(consumer);
    }

}
