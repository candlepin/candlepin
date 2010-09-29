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
package org.fedoraproject.candlepin.sync;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.model.SubscriptionsCertificate;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static Logger log = Logger.getLogger(EntitlementImporter.class);
    
    private SubscriptionCurator subscriptionCurator;
    private CertificateSerialCurator csCurator;
    private EventSink sink;
    
    public EntitlementImporter(SubscriptionCurator subscriptionCurator, 
        CertificateSerialCurator csCurator, EventSink sink) {
        
        this.subscriptionCurator = subscriptionCurator;
        this.csCurator = csCurator;
        this.sink = sink;
    }
    
    public Subscription importObject(ObjectMapper mapper, Reader reader, Owner owner,
        Map<String, Product> productsById) throws IOException, SyncDataFormatException {
        
        Entitlement entitlement = mapper.readValue(reader, Entitlement.class);
        Subscription subscription = new Subscription();
        
        subscription.setUpstreamPoolId(entitlement.getPool().getId());
        
        subscription.setOwner(owner);
        
        subscription.setStartDate(entitlement.getStartDate());
        subscription.setEndDate(entitlement.getEndDate());
        
        subscription.setQuantity(entitlement.getQuantity().longValue());
        
        subscription.setProduct(findProduct(productsById, entitlement.getProductId()));
        
        Set<Product> products = new HashSet<Product>();
        for (String productId : entitlement.getPool().getProvidedProductIds()) {
            
            products.add(findProduct(productsById, productId));
        }
        subscription.setProvidedProducts(products);
        Set<EntitlementCertificate> certs = entitlement.getCertificates();

        // subscriptions have one cert
        int entcnt = 0;
        for (EntitlementCertificate cert : certs) {
            entcnt++;
            CertificateSerial cs = new CertificateSerial();
            cs.setCollected(cert.getSerial().isCollected());
            cs.setExpiration(cert.getSerial().getExpiration());
            cs.setRevoked(cert.getSerial().isRevoked());
            cs.setUpdated(cert.getSerial().getUpdated());
            cs.setCreated(cert.getSerial().getCreated());
            csCurator.create(cs);
            SubscriptionsCertificate sc = new SubscriptionsCertificate();
            sc.setKey(cert.getKey());
            sc.setCertAsBytes(cert.getCertAsBytes());
            sc.setSerial(cs);
            subscription.setCertificate(sc);
        }

        if (entcnt > 1) {
            log.error("More than one entitlement cert found for subscription");
        }
        
        return subscription;
    }

    private Product findProduct(Map<String, Product> productsById,
        String productId) throws SyncDataFormatException {
        Product product = productsById.get(productId);
        if (product == null) {
            throw new SyncDataFormatException("Unable to find product with id: " +
                productId);
        }
        return product;
    }

    /**
     * @param subscriptionsToImport
     */
    public void store(Owner owner, Set<Subscription> subscriptionsToImport) {
        Map<String, Subscription> existingSubByEntitlement =
            new HashMap<String, Subscription>();
        for (Subscription subscription : subscriptionCurator.listByOwner(owner)) {
            // if the upstream pool id is null,
            // this must be a locally controlled sub.
            if (subscription.getUpstreamPoolId() != null) {
                existingSubByEntitlement.put(subscription.getUpstreamPoolId(),
                    subscription);
            }
        }
        
        for (Subscription subscription : subscriptionsToImport) {
            log.debug("Saving subscription for upstream pool id: " +
                subscription.getUpstreamPoolId());
            Subscription local =
                existingSubByEntitlement.get(subscription.getUpstreamPoolId());
            
            if (local == null) {
                subscriptionCurator.create(subscription);

                // send out created event
                log.debug("emitting subscription event");
                sink.emitSubscriptionCreated(subscription);
            }
            else {
                subscription.setId(local.getId());
                subscriptionCurator.merge(subscription);
                
                existingSubByEntitlement.remove(subscription.getUpstreamPoolId());

                // send updated event
                sink.emitSubscriptionModified(null, subscription);
            }
        }
        
        for (Subscription subscription : existingSubByEntitlement.values()) {
            Event e = sink.createSubscriptionDeleted(subscription);
            subscriptionCurator.delete(subscription);
            sink.sendEvent(e);
        }
    }
}
