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
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
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
        Long serialNumber) throws GeneralSecurityException, IOException {
        log.debug("Generating entitlement cert for:");
        log.debug("   consumer: " + consumer.getUuid());
        log.debug("   product: " + product.getId());
        log.debug("   end date: " + endDate);
        
        KeyPair keyPair = keyPairCurator.getConsumerKeyPair(consumer);
        
        X509Certificate x509Cert = createX509Certificate(consumer, sub,
            product, endDate, serialNumber, keyPair);
        
        EntitlementCertificate cert = new EntitlementCertificate();
        cert.setSerial(new BigInteger(serialNumber.toString()));
        cert.setKey(pki.getPemEncoded(keyPair.getPrivate()));
        cert.setCert(this.pki.getPemEncoded(x509Cert));
        cert.setEntitlement(entitlement);
        
        log.debug("Generated cert: " + serialNumber);
        log.debug("Key: " + cert.getKeyAsString());
        log.debug("Cert: " + cert.getCertAsString());
        
        return cert;
    }

    public X509Certificate createX509Certificate(Consumer consumer,
        Subscription sub, Product product, Date endDate, Long serialNumber,
        KeyPair keyPair) throws GeneralSecurityException, IOException {
        // oiduitl is busted at the moment, so do this manually
        List<X509ExtensionWrapper> extensions = new LinkedList<X509ExtensionWrapper>();
        
        extensions.addAll(productExtensions(product));
        extensions.addAll(contentExtensions(product));
        extensions.addAll(subscriptionExtensions(sub));
        extensions.addAll(consumerExtensions(consumer));
        
        X509Certificate x509Cert = this.pki.createX509Certificate(createDN(consumer), 
            extensions, sub.getStartDate(), endDate, keyPair, serialNumber);
        return x509Cert;
    }

    public List<X509ExtensionWrapper> consumerExtensions(Consumer consumer) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        
        //1.3.6.1.4.1.2312.9.5.1 
        // REDHAT_OID here seems wrong...
        String consumerOid = OIDUtil.REDHAT_OID + "." + 
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.SYSTEM_NAMESPACE_KEY);
        toReturn.add(new X509ExtensionWrapper(consumerOid + "." + 
                OIDUtil.SYSTEM_OIDS.get(OIDUtil.UUID_KEY),
                false, new DERUTF8String(consumer.getUuid())));
        
        return toReturn;
    }

    public List<X509ExtensionWrapper> subscriptionExtensions(Subscription sub) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        // Subscription/order info
        //need the sub product name, not id here
        String subscriptionOid = OIDUtil.REDHAT_OID + "." + 
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NAME_KEY), 
                false, new DERUTF8String(sub.getProductId())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NUMBER_KEY), 
                false, new DERUTF8String(sub.getId().toString())));
        //TODO: regnum? virtlimit/socketlimit?
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_KEY),
                false, new DERUTF8String(sub.getQuantity().toString())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "."  + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_STARTDATE_KEY),
                false, new DERUTF8String(sub.getStartDate().toString())));
        toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_ENDDATE_KEY),
                false, new DERUTF8String(sub.getEndDate().toString())));
        
        return toReturn;
    }

    public List<X509ExtensionWrapper> productExtensions(Product product) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        
        String productCertOid = OIDUtil.REDHAT_OID + "." + 
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.PRODUCT_CERT_NAMESPACE_KEY);
    
        String productOid = productCertOid  + "." + product.getHash().toString();
        // 10.10.10 is the product hash, arbitrary number atm
        // replace ith approriate hash for product, we can maybe get away with faking this
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
                    OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_NAME_KEY), 
                    false, new DERUTF8String(product.getName())));
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
                    OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_DESC_KEY),
                    false, new DERUTF8String(product.getVariant())));
        // we don't have product attributes populated at the moment, so this doesnt work
        //        extensions.add(new X509ExtensionWrapper("1.3.6.1.4.1.2312.9.1.101010.3",
        //false, new DERUTF8String(product.getAttribute("arch").getValue()) ));
        toReturn.add(new X509ExtensionWrapper(productOid + "." + 
                    OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_ARCH_KEY),
                    false, new DERUTF8String(product.getArch())));
        toReturn.add(new X509ExtensionWrapper(productOid + "." + 
                    OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_VERSION_KEY),
                    false, new DERUTF8String(product.getVersion())));
        
        return toReturn;
    }

    public List<X509ExtensionWrapper> contentExtensions(Product product) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        Set<Content> content = product.getContent();
        for (Content con : content) {
            String contentOid = OIDUtil.REDHAT_OID + "." +  
                   OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.CHANNEL_FAMILY_NAMESPACE_KEY) + 
                   "." + con.getHash().toString() + "." + 
                   OIDUtil.CF_REPO_TYPE.get(con.getType());
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_NAME_KEY),
                    false, new DERUTF8String(con.getName())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                   OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_LABEL_KEY),
                   false, new DERUTF8String(con.getLabel())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_VENDOR_ID_KEY) ,
                    false, new DERUTF8String(con.getVendor())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_DOWNLOAD_URL_KEY) ,
                    false, new DERUTF8String(con.getContentUrl()))); 
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_GPG_URL_KEY),
                    false, new DERUTF8String(con.getGpgUrl())));
            toReturn.add(new X509ExtensionWrapper(contentOid + "." + 
                    OIDUtil.CHANNEL_FAMILY_OIDS.get(OIDUtil.CF_ENABLED),
                    false, new DERUTF8String(con.getEnabled())));
        }
        return toReturn;
    }
    
    private String createDN(Consumer consumer) {
        StringBuilder sb = new StringBuilder("CN=");
        sb.append(consumer.getName());
        sb.append(", UID=");
        sb.append(consumer.getUuid());
        return sb.toString();
    }

}
