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
package org.fedoraproject.candlepin.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERUTF8String;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.X509ExtensionWrapper;


/**
 * X509ExtensionUtil
 */
public class X509ExtensionUtil {

    
    private static Logger log = Logger.getLogger(X509ExtensionUtil.class);
    
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
        // NOTE: order ~= subscriptio
        //       entitlement == entitlement

        String subscriptionOid = OIDUtil.REDHAT_OID + "." + 
                OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        if (sub.getProduct().getId() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                    OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_NAME_KEY), 
                    false, new DERUTF8String(sub.getProduct().getId())));
        }
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
        if (sub.getContractNumber() != null) {
            toReturn.add(new X509ExtensionWrapper(subscriptionOid + "." + 
                OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_CONTRACT_NUMBER_KEY),
                false, new DERUTF8String(sub.getContractNumber())));
        }
      
        return toReturn;
    }
    
    public List<X509ExtensionWrapper> entitlementExtensions(Entitlement entitlement) {
        String entitlementOid = OIDUtil.REDHAT_OID + "." + 
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.ORDER_NAMESPACE_KEY);
        return Collections.singletonList(
                   new X509ExtensionWrapper(entitlementOid + "." + 
                       OIDUtil.ORDER_OIDS.get(OIDUtil.ORDER_QUANTITY_USED),
                       false, new DERUTF8String(entitlement.getQuantity().toString())));
        
    }
        
    public List<X509ExtensionWrapper> productExtensions(Product product) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        
        String productCertOid = OIDUtil.REDHAT_OID + "." + 
            OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.PRODUCT_CERT_NAMESPACE_KEY);
        
        // XXX need to deal with non hash style IDs
        String productOid = productCertOid  + "." + product.getId();
        // 10.10.10 is the product hash, arbitrary number atm
        // replace ith approriate hash for product, we can maybe get away with faking this
        toReturn.add(new X509ExtensionWrapper(productOid + "." +
            OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_NAME_KEY), 
            false, new DERUTF8String(product.getName())));
        
        if (product.getAttribute("variant") != null) {
            toReturn.add(new X509ExtensionWrapper(productOid + "." +
                OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_DESC_KEY),
                false, new DERUTF8String(product.getAttributeValue("variant"))));
        }
        if (product.getAttribute("arch") != null) {
            toReturn.add(new X509ExtensionWrapper(productOid + "." + 
                OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_ARCH_KEY),
                false, new DERUTF8String(product.getAttributeValue("arch"))));
        }
        if (product.getAttribute("version") != null) {
            toReturn.add(new X509ExtensionWrapper(productOid + "." + 
                OIDUtil.ORDER_PRODUCT_OIDS.get(OIDUtil.OP_VERSION_KEY),
                false, new DERUTF8String(product.getAttributeValue("version"))));
        }
        return toReturn;
    }

    public List<X509ExtensionWrapper> contentExtensions(Product product) {
        List<X509ExtensionWrapper> toReturn = new LinkedList<X509ExtensionWrapper>();
        Set<Content> content = product.getContent();
        Set<Content> enabledContent = product.getEnabledContent();
        
        for (Content con : content) {
            String contentOid = OIDUtil.REDHAT_OID + "." +  
                   OIDUtil.TOPLEVEL_NAMESPACES.get(OIDUtil.CHANNEL_FAMILY_NAMESPACE_KEY) + 
                   "." + con.getId().toString() + "." + 
                   OIDUtil.CF_REPO_TYPE.get(con.getType());
            toReturn.add(new X509ExtensionWrapper(contentOid, 
                    false, new DERUTF8String(con.getType())));
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
                    false,  new DERUTF8String(
                        (enabledContent.contains(con) ? "0" : "1"))));
        }
        return toReturn;
    }
}
