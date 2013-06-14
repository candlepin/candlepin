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
import java.util.Arrays;
import java.util.Comparator;
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
import org.candlepin.model.SubProvidedProduct;
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

        subscription.setAccountNumber(entitlement.getPool().getAccountNumber());
        subscription.setContractNumber(entitlement.getPool().getContractNumber());

        subscription.setQuantity(entitlement.getQuantity().longValue());

        subscription.setProduct(findProduct(productsById, entitlement.getProductId()));

        Set<Product> products = new HashSet<Product>();
        for (ProvidedProduct providedProduct : entitlement.getPool().
            getProvidedProducts()) {
            products.add(findProduct(productsById, providedProduct.getProductId()));
        }
        subscription.setProvidedProducts(products);

        // Add any sub product data to the subscription.
        if (entitlement.getPool().getSubProductId() != null) {
            subscription.setSubProduct(findProduct(productsById,
                entitlement.getPool().getSubProductId()));
        }

        Set<Product> subProvProds = new HashSet<Product>();
        for (SubProvidedProduct subProvProd : entitlement.getPool().
            getSubProvidedProducts()) {
            subProvProds.add(findProduct(productsById, subProvProd.getProductId()));
        }
        subscription.setSubProvidedProducts(subProvProds);

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
     * @param subsToImport
     *
     *  Reconciles incoming entitlements to existing subscriptions.
     *  Each set is mapped against the upstream pool id.
     *  First match attempt will use entitlement id from incoming
     *   entitlements for comparison to existing subscriptions.
     *  Next attempt will use the exact quantity for comparison. This is to
     *   cover scenarios where the intent is to re-establish the distributor
     *   from the host.
     *  The final attempt will use ordering of the remaining incoming entitlements
     *   and of remaining existing subscriptions in descending order by quantity.
     *  Either the remaining subscriptions will be deleted, or the unmatched incoming
     *   entitlements will be turned into new subscriptions.
     */
    public void store(Owner owner, Set<Subscription> subsToImport) {

        Map<String, Map<String, Subscription>> existingSubsByUpstreamPool =
            mapSubsByUpstreamPool(owner);

        // if we can match to the entitlement id do it.
        // we need a new list to hold the ones that are left
        Set<Subscription> subscriptionsStillToImport = new HashSet<Subscription>();
        for (Subscription subscription : subsToImport) {
            Subscription local = null;
            Map<String, Subscription> map = existingSubsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
                createSubscription(subscription);
                log.info("Creating new subscription for incoming entitlement with id [" +
                    subscription.getUpstreamEntitlementId() +
                    "]");
                continue;
            }
            local = map.get(subscription.getUpstreamEntitlementId());
            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] into subscription with existing entitlement id [" +
                    local.getUpstreamEntitlementId() +
                    "]. Entitlement id match.");
            }
            else {
                subscriptionsStillToImport.add(subscription);
                log.warn("Subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] does not have an entitlement id match " +
                    "in the current subscriptions for the upstream pool id [" +
                    subscription.getUpstreamPoolId() +
                    "]");
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // we need a new list to hold the ones that are left
        List<Subscription> subscriptionsNeedQuantityMatch = new ArrayList<Subscription>();
        for (Subscription subscription : subscriptionsStillToImport) {
            Subscription local = null;
            Map<String, Subscription> map = existingSubsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null) {
                map = new HashMap<String, Subscription>();
            }
            for (Subscription localSub : map.values()) {
                if (localSub.getQuantity().equals(subscription.getQuantity())) {
                    local = localSub;
                    break;
                }
            }
            if (local != null) {
                mergeSubscription(subscription, local, map);
                log.info("Merging subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] into subscription with existing entitlement id [" +
                    local.getUpstreamEntitlementId() +
                    "]. Exact quantity match.");
            }
            else {
                subscriptionsNeedQuantityMatch.add(subscription);
                log.warn("Subscription for incoming entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "] does not have an exact quantity match " +
                    "in the current subscriptions for the upstream pool id [" +
                    subscription.getUpstreamPoolId() +
                    "]");
            }
        }

        // matches will be made against the upstream pool id and quantity.
        // quantities will just match by position from highest to lowest
        // we need a new list to hold the ones that are left
        Subscription[] inNeed = subscriptionsNeedQuantityMatch.toArray(
            new Subscription[0]);
        Arrays.sort(inNeed, new QuantityComparator());
        for (Subscription subscription : inNeed) {
            Subscription local = null;
            Map<String, Subscription> map = existingSubsByUpstreamPool.get(
                subscription.getUpstreamPoolId());
            if (map == null || map.isEmpty()) {
                createSubscription(subscription);
                log.info("Creating new subscription for incoming entitlement with id [" +
                    subscription.getUpstreamEntitlementId() +
                    "]");
                continue;
            }
            Subscription[] locals = map.values().toArray(new Subscription[0]);
            Arrays.sort(locals, new QuantityComparator());
            local = locals[0];
            mergeSubscription(subscription, local, map);
            log.info("Merging subscription for incoming entitlement id [" +
                subscription.getUpstreamEntitlementId() +
                "] into subscription with existing entitlement id [" +
                local.getUpstreamEntitlementId() +
                "]. Ordered quantity match.");
        }
        deleteRemainingLocalSubscriptions(existingSubsByUpstreamPool);
    }

    private Map<String, Map<String, Subscription>> mapSubsByUpstreamPool(Owner owner) {
        Map<String, Map<String, Subscription>> existingSubsByUpstreamPool =
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
                Map<String, Subscription> subs = existingSubsByUpstreamPool.get(
                    subscription.getUpstreamPoolId());
                if (subs == null) {
                    subs = new HashMap<String, Subscription>();
                }
                subs.put(subscription.getUpstreamEntitlementId(), subscription);
                existingSubsByUpstreamPool.put(subscription.getUpstreamPoolId(),
                    subs);
            }
        }
        return existingSubsByUpstreamPool;
    }

    private void deleteRemainingLocalSubscriptions(
        Map<String, Map<String, Subscription>> existingSubsByUpstreamPool) {
        for (Map<String, Subscription> map : existingSubsByUpstreamPool.values()) {
            for (Subscription subscription : map.values()) {
                Event e = sink.createSubscriptionDeleted(subscription);
                subscriptionCurator.delete(subscription);
                sink.sendEvent(e);
                log.info("Delete subscription with entitlement id [" +
                    subscription.getUpstreamEntitlementId() +
                    "]");
            }
        }
    }

    private void mergeSubscription(Subscription subscription, Subscription local,
        Map<String, Subscription> map) {
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

    /**
     *
     * QuantityComparator
     *
     * descending quantity sort on Subscriptions
     */

    public static class QuantityComparator implements Comparator<Subscription> {
        @Override
        public int compare(Subscription s1, Subscription s2) {
            return s2.getQuantity().compareTo(s1.getQuantity());
        }
    }
}
