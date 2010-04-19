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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERUTF8String;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.KeyPairCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;
import org.fedoraproject.candlepin.service.BaseEntitlementCertServiceAdapter;


import com.google.inject.Inject;
import com.redhat.candlepin.util.OIDUtil;

/**
 * DefaultEntitlementCertServiceAdapter
 */
public class DefaultEntitlementCertServiceAdapter extends 
    BaseEntitlementCertServiceAdapter {
    private PKIUtility pki;
    private KeyPairCurator keyPairCurator;
    private static Logger log = Logger
        .getLogger(DefaultEntitlementCertServiceAdapter.class);
    
    @Inject
    public DefaultEntitlementCertServiceAdapter(PKIUtility pki, 
        EntitlementCertificateCurator entCertCurator, KeyPairCurator keyPairCurator) {
        
        this.pki = pki;
        this.entCertCurator = entCertCurator;
        this.keyPairCurator = keyPairCurator;
    }

    @Override
    public EntitlementCertificate generateEntitlementCert(Consumer consumer,
        Entitlement entitlement, Subscription sub, Product product, Date endDate, 
        BigInteger serialNumber) throws GeneralSecurityException, IOException {
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: " + consumer.getUuid());
        log.debug("   product: " + product.getId());
        log.debug("   end date: " + endDate);
        
        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
        
        // oiduitl is busted at the moment, so do this manually
        OIDUtil OIDUtil;
        List <X509ExtensionWrapper> extensions = new LinkedList<X509ExtensionWrapper>();
        
        Long productHash = product.getHash();
        
        String productOid = "1.3.6.1.4.1.2312.9.1." + productHash.toString();
        // 10.10.10 is the product hash, arbitrary number atm
        // replace ith approriate hash for product, we can maybe get away with faking this
        extensions.add(new X509ExtensionWrapper(productOid + ".1", false, new DERUTF8String(product.getName()) ));
        extensions.add(new X509ExtensionWrapper(productOid + ".2", false, new DERUTF8String(product.getVariant()) ));
        // we don't have product attributes populated at the moment, so this doesnt work
        //        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.1.101010.3", false, new DERUTF8String(product.getAttribute("arch").getValue()) ));
        extensions.add(new X509ExtensionWrapper(productOid + ".3", false, new DERUTF8String(product.getArch())));
        extensions.add(new X509ExtensionWrapper(productOid + ".4", false, new DERUTF8String(product.getVersion()) ));
        
        
        Set<Content> content = product.getContent();
        for (Content con : content) {
            String contentOid = "1.3.6.1.4.1.2312.9.2." + con.getHash().toString() + ".1";
            extensions.add(new X509ExtensionWrapper(contentOid, false, new DERUTF8String("yum") ));
            extensions.add(new X509ExtensionWrapper(contentOid + ".1", false, new DERUTF8String(con.getName()) ));
            extensions.add(new X509ExtensionWrapper(contentOid + ".2", false, new DERUTF8String(con.getLabel()) ));
            extensions.add(new X509ExtensionWrapper(contentOid + ".5", false, new DERUTF8String(con.getVendor()) ));
            extensions.add(new X509ExtensionWrapper(contentOid + ".6", false, new DERUTF8String(con.getContentUrl()) )); 
            extensions.add(new X509ExtensionWrapper(contentOid + ".7", false, new DERUTF8String(con.getGpgUrl()) ));
            extensions.add(new X509ExtensionWrapper(contentOid + ".8", false, new DERUTF8String(con.getEnabled()) ));
        }

   
        // Subscription/order info
        //need the sub product name, not id here
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.4.1", false, new DERUTF8String(sub.getProductId()) ));
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.4.2", false, new DERUTF8String(sub.getId().toString()) ));
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.4.5", false, new DERUTF8String(sub.getQuantity().toString()) ));
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.4.6", false, new DERUTF8String(sub.getStartDate().toString() ) ));
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.4.7", false, new DERUTF8String(sub.getEndDate().toString() ) ));
        
        //1.3.6.1.4.1.2312.9.5.1 
        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.5.1", false, new DERUTF8String(consumer.getUuid() ) ));
        
        
        X509Certificate x509Cert = this.pki.createX509Certificate(createDN(consumer), 
            extensions, sub.getStartDate(), endDate, keyPair, serialNumber);
        
        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(serialNumber);
        cert.setKey(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCert(this.pki.getPemEncoded(x509Cert));
        cert.setEntitlement(entitlement);
        
        log.debug("Generated cert: " + serialNumber);
        log.debug("Key: " + cert.getKeyAsString());
        log.debug("Cert: " + cert.getCertAsString());
        
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
