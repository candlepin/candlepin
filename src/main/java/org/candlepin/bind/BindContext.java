/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.bind;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.util.Util;

import org.xnap.commons.i18n.I18n;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;



/**
 * - Container class for holding bind information.
 * - Only meant for entities that are shared by operations.
 * other entities can be encapsulating them within the operation.
 * - Each getter checks if the collection field is already populated,
 * and only if it is not we make a database call of populate it. This way
 * an operation is not dependant on other to populate the entities, and we make
 * one call per entity type.
 */
public class BindContext {
    private Consumer consumer;
    private Owner owner;
    private Consumer lockedConsumer;
    private Map<String, PoolQuantity> poolQuantities;
    private Map<String, Entitlement> entitlementMap;
    private Map<String, Integer> quantities;
    private boolean quantityRequested = false;
    //change to generic type in future if needed
    private EntitlementRefusedException exception;
    private PoolCurator poolCurator;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private OwnerCurator ownerCurator;
    private I18n i18n;

    @Inject
    public BindContext(PoolCurator poolCurator,
        ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator,
        OwnerCurator ownerCurator,
        I18n i18n,
        Consumer consumer,
        Map<String, Integer> quantities) {

        this.poolCurator = poolCurator;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.ownerCurator = ownerCurator;
        this.i18n = i18n;
        this.consumer = consumer;
        this.quantities = quantities;
    }

    public boolean isQuantityRequested() {
        return quantityRequested;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public ConsumerType getConsumerType() {
        return this.consumerTypeCurator.getConsumerType(this.getConsumer());
    }

    public Owner getOwner() {
        if (owner == null) {
            owner = ownerCurator.findOwnerById(consumer.getOwnerId());
        }
        return owner;
    }

    public Map<String, PoolQuantity> getPoolQuantities() {
        if (poolQuantities == null) {
            poolQuantities = new HashMap<>();

            for (Pool pool : poolCurator.listAllByIds(quantities.keySet())) {
                Integer quantity = quantities.get(pool.getId());
                if (quantity > 0) {
                    quantityRequested = true;
                }

                poolQuantities.put(pool.getId(), new PoolQuantity(pool, quantity));
                quantities.remove(pool.getId());
            }

            if (!quantities.isEmpty()) {
                throw new IllegalArgumentException(i18n.tr("Subscription pool(s) {0} do not exist.",
                    quantities.keySet()));
            }
        }
        return poolQuantities;
    }

    /**
     * locks the pools and replaces the existing entities in poolQuantities.
     */
    public void lockPools() {
        Collection<Pool> pools = poolCurator.lockAndLoad(poolQuantities.keySet());
        this.poolCurator.refresh(pools);
        for (Pool pool : pools) {
            poolQuantities.get(pool.getId()).setPool(pool);
        }
    }

    public Consumer getLockedConsumer() {
        if (lockedConsumer == null) {
            lockedConsumer = consumerCurator.lock(consumer);
        }

        return lockedConsumer;
    }

    public Map<String, Entitlement> getEntitlementMap() {
        if (entitlementMap == null) {
            entitlementMap = new HashMap<>();

            for (PoolQuantity poolQuantity : poolQuantities.values()) {
                Pool pool = poolQuantity.getPool();
                Entitlement ent = new Entitlement(consumer, getOwner(), poolQuantity.getQuantity());
                // we manually generate ids as they are needed before the entitlement
                // can be persisted.
                ent.setId(Util.generateDbUUID());
                ent.setUpdatedOnStart(pool.getStartDate().after(new Date()));
                entitlementMap.put(pool.getId(), ent);
            }
        }
        return entitlementMap;
    }

    public EntitlementRefusedException getException() {
        return exception;
    }

    public void setException(EntitlementRefusedException e, StackTraceElement[] trace) {
        this.exception = e;
        this.exception.setStackTrace(trace);
    }
}
