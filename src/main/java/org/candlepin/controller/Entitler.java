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
package org.candlepin.controller;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.EntitlementRefusedException;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * entitler
 */
public class Entitler {
    private static Logger log = Logger.getLogger(Entitler.class);

    private PoolManager poolManager;
    private I18n i18n;
    private EventFactory evtFactory;
    private EventSink sink;
    private ConsumerCurator consumerCurator;

    @Inject
    public Entitler(PoolManager pm, ConsumerCurator cc, I18n i18n,
        EventFactory evtFactory, EventSink sink) {

        this.poolManager = pm;
        this.i18n = i18n;
        this.evtFactory = evtFactory;
        this.sink = sink;
        this.consumerCurator = cc;
    }

    public List<Entitlement> bindByPool(String poolId, String consumeruuid,
        Integer quantity) {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        return bindByPool(poolId, c, quantity);
    }

    public List<Entitlement> bindByPool(String poolId, Consumer consumer,
        Integer quantity) {
        Pool pool = poolManager.find(poolId);
        List<Entitlement> entitlementList = new LinkedList<Entitlement>();

        if (log.isDebugEnabled() && pool != null) {
            log.debug("pool: id[" + pool.getId() + "], consumed[" +
                pool.getConsumed() + "], qty [" + pool.getQuantity() + "]");
        }

        if (pool == null) {
            throw new BadRequestException(i18n.tr(
                "Subscription pool {0} does not exist.", poolId));
        }

        // Attempt to create an entitlement:
        entitlementList.add(createEntitlementByPool(consumer, pool, quantity));
        return entitlementList;
    }

    private Entitlement createEntitlementByPool(Consumer consumer, Pool pool,
        Integer quantity) {
        // Attempt to create an entitlement:
        try {
            Entitlement e = poolManager.entitleByPool(consumer, pool, quantity);
            log.debug("Created entitlement: " + e);
            return e;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for
            // now:
            // TODO: multiple checks here for the errors will get ugly, but the
            // returned
            // string is dependent on the caller (ie pool vs product)
            String msg;
            String error = e.getResult().getErrors().get(0).getResourceKey();

            if (error.equals("rulefailed.consumer.already.has.product")) {
                msg = i18n.tr(
                    "This consumer is already subscribed to the product " +
                    "matching pool with id ''{0}''.", pool.getId());
            }
            else if (error.equals("rulefailed.no.entitlements.available")) {
                msg = i18n.tr(
                    "No subscriptions are available from the pool with " +
                    "id ''{0}''.", pool.getId());
            }
            else if (error.equals("rulefailed.consumer.type.mismatch")) {
                msg = i18n.tr(
                    "Consumers of this type are not allowed to subscribe to " +
                    "the pool with id ''{0}''.", pool.getId());
            }
            else if (error.equals("rulefailed.pool.does.not.support.multi-entitlement")) {
                msg = i18n.tr("Multi-entitlement not supported for pool with id ''{0}''.",
                    pool.getId());
            }
            else if (error.equals("virt.guest.host.does.not.match.pool.owner")) {
                msg = i18n.tr("Guest''s host does not match owner of pool: ''{0}''.",
                    pool.getId());
            }
            else if (error.equals("pool.not.available.to.manifest.consumers")) {
                msg = i18n.tr("Pool not available to manifest consumers: ''{0}''.",
                    pool.getId());
            }
            else if (error.equals("rulefailed.virt.only")) {
                msg = i18n.tr("Pool is restricted to virtual guests: ''{0}''.",
                    pool.getId());
            }
            else if (error.equals("rulefailed.quantity.mismatch")) {
                String multip = null;
                if (pool.hasProductAttribute("instance_multiplier")) {
                    multip = pool.getProductAttribute("instance_multiplier").getValue();
                }
                msg = i18n.tr(
                    "Quantity ''{0}'' is not a multiple of instance multiplier ''{1}''",
                    quantity, multip);
            }
            else if (error.equals("rulefailed.instance.unsupported.by.consumer")) {
                msg = i18n.tr("Consumer does not support instance based calculation " +
                    "required by pool ''{0}''", pool.getId());
            }
            else if (error.equals("rulefailed.cores.unsupported.by.consumer")) {
                msg = i18n.tr("Consumer does not support core calculaton " +
                    "required by pool ''{0}''", pool.getId());
            }
            else if (error.equals("rulefailed.ram.unsupported.by.consumer")) {
                msg = i18n.tr("Consumer does not support RAM calculaton " +
                    "required by pool ''{0}''", pool.getId());
            }
            else {
                msg = i18n.tr("Unable to entitle consumer to the pool with " +
                    "id ''{0}''.: {1}", pool.getId().toString(), error);
            }
            throw new ForbiddenException(msg);
        }
    }

    public void adjustEntitlementQuantity(Consumer consumer, Entitlement ent,
        Integer quantity) {
        // Attempt to adjust an entitlement:
        try {
            poolManager.adjustEntitlementQuantity(consumer, ent, quantity);
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for
            // now:
            String msg;
            String error = e.getResult().getErrors().get(0).getResourceKey();

            if (error.equals("rulefailed.no.entitlements.available")) {
                msg = i18n.tr(
                    "Insufficient pool quantity available for adjustment to entitlement " +
                    "''{0}''.",
                    ent.getId());
            }
            else if (error.equals("rulefailed.pool.does.not.support.multi-entitlement")) {
                msg = i18n.tr("Multi-entitlement not supported for pool connected with " +
                              "entitlement ''{0}''.",
                    ent.getId());
            }
            else if (error.equals("rulefailed.consumer.already.has.product")) {
                msg = i18n.tr("Multi-entitlement not supported for pool connected with " +
                              "entitlement ''{0}''.",
                    ent.getId());
            }
            else {
                msg = i18n.tr("Unable to adjust quantity for the entitlement with " +
                    "id ''{0}'': {1}", ent.getId(), error);
            }
            throw new ForbiddenException(msg);
        }
    }

    public List<Entitlement> bindByProducts(String[] productIds,
        String consumeruuid, Date entitleDate) {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        return bindByProducts(productIds, c, entitleDate);
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param productIds List of product ids.
     * @param consumer The consumer being entitled.
     * @param entitleDate specific date to entitle by.
     * @return List of Entitlements
     */
    public List<Entitlement> bindByProducts(String[] productIds,
        Consumer consumer, Date entitleDate) {

        // Attempt to create entitlements:
        try {
            List<Entitlement> entitlements = poolManager.entitleByProducts(
                consumer, productIds, entitleDate);
            log.debug("Created entitlements: " + entitlements);
            return entitlements;
        }
        catch (EntitlementRefusedException e) {
            // Could be multiple errors, but we'll just report the first one for
            // now:
            // TODO: Convert resource key to user friendly string?
            // See below for more TODOS
            String productId = productIds[0];
            String msg;
            String error = e.getResult().getErrors().get(0).getResourceKey();
            if (error.equals("rulefailed.consumer.already.has.product")) {
                msg = i18n.tr("This consumer is already subscribed to the " +
                    "product ''{0}''", productId);
            }
            else if (error.equals("rulefailed.no.entitlements.available")) {
                msg = i18n.tr("There are not enough free subscriptions " +
                    "available for the product ''{0}''", productId);
            }
            else if (error.equals("rulefailed.consumer.type.mismatch")) {
                msg = i18n.tr("Consumers of this type are not allowed to the " +
                    "product ''{0}''", productId);
            }
            else if (error.equals("rulefailed.virt.only")) {
                msg = i18n.tr(
                    "Only virtual systems can have subscription ''{0}'' attached.",
                    productId);
            }
            else {
                msg = i18n.tr(
                    "Unable to entitle consumer to the product ''{0}'': {1}",
                    productId, error);
            }
            throw new ForbiddenException(msg);
        }
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param consumer The consumer being entitled.
     * @return List of Entitlements
     */
    public List<PoolQuantity> getDryRun(Consumer consumer,
        String serviceLevelOverride) {

        List<PoolQuantity> result = new ArrayList<PoolQuantity>();
        try {
            Owner owner = consumer.getOwner();
            Date entitleDate = new Date();

            result = poolManager.getBestPools(
                consumer, null, entitleDate, owner, serviceLevelOverride);
            log.debug("Created Pool Quantity list: " + result);
        }
        catch (EntitlementRefusedException e) {
            // If we catch an exception we will just return an empty list
            // The dry run just reports that an autobind will have no pools
            // We will debug log the message, but returning does not seem to add
            // to the process
            if (log.isDebugEnabled()) {
                String message = e.getResult().getErrors().get(0).getResourceKey();
                log.debug("consumer dry-run " + consumer.getUuid() + ": " + message);
            }
        }
        return result;
    }

    public void sendEvents(List<Entitlement> entitlements) {
        if (entitlements != null) {
            for (Entitlement e : entitlements) {
                Event event = evtFactory.entitlementCreated(e);
                sink.sendEvent(event);
            }
        }
    }
}
