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
package org.candlepin.controller.refresher.mappers;

import org.candlepin.model.Pool;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.util.Util;



/**
 * The PoolMapper class is a simple implementation of the EntityMapper specific to Pool.
 */
public class PoolMapper extends AbstractEntityMapper<Pool, SubscriptionInfo> {

    /**
     * Creates a new PoolMapper instance
     */
    public PoolMapper() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<Pool> getExistingEntityClass() {
        return Pool.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<SubscriptionInfo> getImportedEntityClass() {
        return SubscriptionInfo.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEntityId(Pool entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        // Impl note:
        // Local pools are mapped to upstream pools by the subscription ID (after reconciliation*).
        // If a subscription ID is present, use that. Otherwise, default to its local ID.
        String id = Util.firstOf(elem -> elem != null && !elem.isEmpty(),
            entity.getSubscriptionId(), entity.getId());

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("entity lacks a mappable ID: " + entity);
        }

        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getEntityId(SubscriptionInfo entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        String id = entity.getId();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("entity lacks a mappable ID: " + entity);
        }

        return id;
    }

}
