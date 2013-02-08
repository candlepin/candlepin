/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.sync;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventSink;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.model.SubscriptionsCertificate;
import org.codehaus.jackson.map.ObjectMapper;
import org.xnap.commons.i18n.I18n;

/**
 * EntitlementImporter - turn an upstream Entitlement into a local subscription
 */
public class EntitlementImporter {
    private static Logger log = Logger.getLogger(EntitlementImporter.class);

    private SubscriptionCurator subscriptionCurator;
    private CertificateSerialCurator csCurator;
    private EventSink sink;
    private I18n i18n;

    public EntitlementImporter(SubscriptionCurator subscriptionCurator,
        CertificateSerialCurator csCurator, EventSink sink, I18n i18n) {

        this.subscriptionCurator = subscriptionCurator;
        this.csCurator = csCurator;
        this.sink = sink;
        this.i18n = i18n;
    }

    public Subscription importObject(ObjectMapper mapper, Reader reader, Owner owner,
        Map<String, Product> productsById, ConsumerDto consumer)
        throws IOException, SyncDataFormatException {

        Entitlement entitlement = mapper.readValue(reader, Entitlement.class);
        Subscription subscription = new Subscription();

        subscription.setUpstreamPoolId(entitlement.getPool().getId());
        subscription.setUpstreamEntitlementId(entitlement.getId());
        subscription.setUpstreamConsumerId(consumer.getUuid());

        subscription.setOwner(owner);

        subscription.setStartDate(entitlement.getStartDate());
        subscription.setEndDate(entitlement.getEndDate());

        subscription.setAccountNumber(entitlement.getAccountNumber());
        subscription.setContractNumber(entitlement.getContractNumber());

        subscription.setQuantity(entitlement.getQuantity().longValue());

        subscription.setProduct(findProduct(productsById, entitlement.getProductId()));

        Set<Product> products = new HashSet<Product>();
        for (ProvidedProduct providedProduct : entitlement.getPool().
            getProvidedProducts()) {

            products.add(findProduct(productsById, providedProduct.getProductId()));
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
            throw new SyncDataFormatException(i18n.tr("Unable to find product with ID: " +
                productId));
        }
        return product;
    }

    /**
     * @param subscriptionsToImport
     */
    public void store(Owner owner, Set<Subscription> subscriptionsToImport) {
        Map<String, Map<String, Subscription>> existingSubByEntitlement =
            new HashMap<String, Map<String, Subscription>>();
        int idx = 0;
        for (Subscription subscription : subscriptionCurator.listByOwner(owner)) {
            // if the upstream pool id is null,
            // this must be a locally controlled sub.
            if (subscription.getUpstreamPoolId() != null) {
                // if the existing sub does not have the ent id yet,
                //  just assign a placeholder to differentiate.
                if (subscription.getUpstreamEntitlementId() == null ||
                    subscription.getUpstreamEntitlementId().trim().equals("")) {
                    subscription.setUpstreamEntitlementId("" + idx++);
                }
                Map<String, Subscription> subs = existingSubByEntitlement.get(
                    subscription.getUpstreamPoolId());
                if (subs == null) {
                    subs = new HashMap<String, Subscription>();
                }
                subs.put(subscription.getUpstreamEntitlementId(), subscription);
                existingSubByEntitlement.put(subscription.getUpstreamPoolId(),
                    subs);
            }
        }

        // if we can match to the entitlement id do it.
        // we need a new list to hold the ones that are left
        Set<Subscription> subscriptionsStillToImport = new HashSet<Subscription>();
        for (Subscription subscription : subscriptionsToImport) {
            log.debug("Saving subscription for upstream pool id: " +
                subscription.getUpstreamPoolId());
            Subscription local = null;
            Map<String, Subscription> map = existingSubByEntitlement.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
                createSubscription(subscription);
                continue;
            }
            local = map.get(subscription.getUpstreamEntitlementId());
            if (local != null) {
                mergeSubscription(subscription, local, map);
            }
            else {
                subscriptionsStillToImport.add(subscription);
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // we need a new list to hold the ones that are left
        Set<Subscription> subscriptionsNeedQuantityMatch = new HashSet<Subscription>();
        for (Subscription subscription : subscriptionsStillToImport) {
            Subscription local = null;
            Map<String, Subscription> map = existingSubByEntitlement.get(
                subscription.getUpstreamPoolId());
            if (map == null) {
                map = new HashMap<String, Subscription>();
            }
            for (Subscription localSub : map.values()) {
                if (localSub.getQuantity() == subscription.getQuantity()) {
                    local = localSub;
                    break;
                }
            }
            if (local != null) {
                mergeSubscription(subscription, local, map);
            }
            else {
                subscriptionsNeedQuantityMatch.add(subscription);
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // quantities will just match by position from highest to lowest
        // we need a new list to hold the ones that are left
        for (Subscription subscription : sort(subscriptionsNeedQuantityMatch
            .toArray(new Subscription[0]))) {
            Subscription local = null;
            Map<String, Subscription> map = existingSubByEntitlement.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
                createSubscription(subscription);
                continue;
            }
            local = sort(map.values().toArray(new Subscription[0])).get(0);
            mergeSubscription(subscription, local, map);
        }

        for (Map<String, Subscription> map : existingSubByEntitlement.values()) {
            for (Subscription subscription : map.values()) {
                Event e = sink.createSubscriptionDeleted(subscription);
                subscriptionCurator.delete(subscription);
                sink.sendEvent(e);
            }
        }
    }
    private List<Subscription> sort(Subscription[] subs) {
        List<Subscription> result = new ArrayList<Subscription>();
        for (Subscription s : subs) {
            if (result.isEmpty()) {
                result.add(s);
            }
            else {
                boolean added = false;
                for (int i = 0; i < result.size(); i++) {
                    if (result.get(i).getQuantity() <= s.getQuantity()) {
                        result.add(i, s);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    private void mergeSubscription(Subscription subscription, Subscription local, Map map) {
        subscription.setId(local.getId());
        subscriptionCurator.merge(subscription);
        map.remove(local.getUpstreamEntitlementId());
        // send updated event
        sink.emitSubscriptionModified(local, subscription);
    }

    private void createSubscription(Subscription subscription) {
        subscriptionCurator.create(subscription);
        // send out created event
        log.debug("emitting subscription event");
        sink.emitSubscriptionCreated(subscription);
    }
}
