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
package org.fedoraproject.candlepin.exporter;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionCurator;

/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static Logger log = Logger.getLogger(EntitlementImporter.class);
    
    private SubscriptionCurator subscriptionCurator;
    
    public EntitlementImporter(SubscriptionCurator subscriptionCurator) {
        this.subscriptionCurator = subscriptionCurator;
    }
    
    public Subscription importObject(ObjectMapper mapper, Reader reader, Owner owner,
        Map<String, Product> productsById) throws IOException, ImporterException {
        
        EntitlementDto entitlement = mapper.readValue(reader, EntitlementDto.class);
        Subscription subscription = new Subscription();
        
        subscription.setUpstreamEntitlmentId(entitlement.getId());
        
        subscription.setOwner(owner);
        
        subscription.setStartDate(entitlement.getStartDate());
        subscription.setEndDate(entitlement.getEndDate());
        
        subscription.setQuantity(entitlement.getQuantity().longValue());
        
        subscription.setProduct(findProduct(productsById, entitlement.getProductId()));
        
        Set<Product> products = new HashSet<Product>();
        for (String productId : entitlement.getProvidedProductIds()) {
            
            products.add(findProduct(productsById, productId));
        }
        subscription.setProvidedProducts(products);
        
        return subscription;
    }

    private Product findProduct(Map<String, Product> productsById,
        String productId) throws ImporterException {
        Product product = productsById.get(productId);
        if (product == null) {
            throw new ImporterException("Unable to find product with id: " + productId);
        }
        return product;
    }

    /**
     * @param subscriptionsToImport
     */
    public void store(Owner owner, Set<Subscription> subscriptionsToImport) {
        Map<Long, Subscription> existingSubByEntitlement =
            new HashMap<Long, Subscription>();
        for (Subscription subscription : subscriptionCurator.listByOwner(owner)) {
            // if the upstream entitlement id is null,
            // this must be a locally controlled sub.
            if (subscription.getUpstreamEntitlmentId() != null) {
                existingSubByEntitlement.put(subscription.getUpstreamEntitlmentId(),
                    subscription);
            }
        }
        
        for (Subscription subscription : subscriptionsToImport) {
            log.debug("Saving subscription for entitlement id: " +
                subscription.getUpstreamEntitlmentId());
            Subscription local =
                existingSubByEntitlement.get(subscription.getUpstreamEntitlmentId());
            
            if (local == null) {
                subscriptionCurator.create(subscription);
            }
            else {
                subscription.setId(local.getId());
                subscriptionCurator.merge(subscription);
                
                existingSubByEntitlement.remove(subscription.getUpstreamEntitlmentId());
            }
        }
        
        for (Subscription subscription : existingSubByEntitlement.values()) {
            subscriptionCurator.delete(subscription);
        }
    }
}
