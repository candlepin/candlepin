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
package org.fedoraproject.candlepin.controller;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

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
                    "No free entitlements are available for the pool with " +
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
            else {
                msg = i18n.tr("Unable to entitle consumer to the pool with " +
                    "id ''{0}''.: {1}", pool.getId().toString(), error);
            }
            throw new ForbiddenException(msg);
        }
    }

    public List<Entitlement> bindByProducts(String[] productIds,
        String consumeruuid, Integer quantity) {
        Consumer c = consumerCurator.findByUuid(consumeruuid);
        return bindByProducts(productIds, c, quantity);
    }

    /**
     * Entitles the given Consumer to the given Product. Will seek out pools
     * which provide access to this product, either directly or as a child, and
     * select the best one based on a call to the rules engine.
     *
     * @param productIds List of product ids.
     * @param consumer The consumer being entitled.
     * @param quantity number of entitlements requested.
     * @return List of Entitlements
     */
    public List<Entitlement> bindByProducts(String[] productIds,
        Consumer consumer, Integer quantity) {
        // Attempt to create entitlements:
        try {
            List<Entitlement> entitlements = poolManager.entitleByProducts(
                consumer, productIds, quantity);
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
                msg = i18n.tr("There are not enough free entitlements " +
                    "available for the product ''{0}''", productId);
            }
            else if (error.equals("rulefailed.consumer.type.mismatch")) {
                msg = i18n.tr("Consumers of this type are not allowed to the " +
                    "product ''{0}''", productId);
            }
            else if (error.equals("rulefailed.virt.only")) {
                msg = i18n.tr(
                    "Only virtual systems can consume the product ''{0}''",
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

    public void sendEvents(List<Entitlement> entitlements) {
        if (entitlements != null) {
            for (Entitlement e : entitlements) {
                Event event = evtFactory.entitlementCreated(e);
                sink.sendEvent(event);
            }
        }
    }
}
