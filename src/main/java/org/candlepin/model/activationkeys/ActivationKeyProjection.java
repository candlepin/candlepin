/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.model.activationkeys;

import org.candlepin.model.Owner;
import org.candlepin.model.Release;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembly of detached activation keys from Hibernate query projections for read-only use.
 */
final class ActivationKeyProjection {
    private final Map<String, ActivationKey> keys = new LinkedHashMap<>();

    /**
     * Builds detached activation keys from the scalar rows.
     *
     * @param owner
     *  the owner of the activation keys
     * @param rows
     *  the projected activation key rows
     */
    ActivationKeyProjection(Owner owner, List<ActivationKeyRow> rows) {
        for (ActivationKeyRow row : rows) {
            ActivationKey key = row.toActivationKey(owner);
            this.keys.put(key.getId(), key);
        }
    }

    /**
     * Checks whether there are any activation keys to populate.
     *
     * @return
     *  true if there are no activation keys
     */
    boolean isEmpty() {
        return this.keys.isEmpty();
    }

    /**
     * Attaches projected product IDs to the matching activation keys.
     *
     * @param rows
     *  the projected product rows
     */
    void addProducts(List<ProductRow> rows) {
        for (ProductRow row : rows) {
            ActivationKey key = this.keys.get(row.keyId());
            if (key != null && row.productId() != null) {
                key.getProductIds().add(row.productId());
            }
        }
    }

    /**
     * Attaches projected content overrides to the matching activation keys.
     *
     * @param rows
     *  the projected content override rows
     */
    void addContentOverrides(List<ContentOverrideRow> rows) {
        for (ContentOverrideRow row : rows) {
            ActivationKey key = this.keys.get(row.keyId());
            if (key != null && row.contentLabel() != null) {
                key.getContentOverrides().add(row.toContentOverride(key));
            }
        }
    }

    /**
     * Attaches projected pool references to the matching activation keys.
     *
     * @param rows
     *  the projected pool rows
     */
    void addPools(List<PoolRow> rows) {
        for (PoolRow row : rows) {
            ActivationKey key = this.keys.get(row.keyId());
            if (key != null && row.poolId() != null) {
                key.getPools().add(row.toActivationKeyPool(key));
            }
        }
    }

    /**
     * Attaches projected add-ons to the matching activation keys.
     *
     * @param rows
     *  the projected add-on rows
     */
    void addAddOns(List<AddOnRow> rows) {
        for (AddOnRow row : rows) {
            ActivationKey key = this.keys.get(row.keyId());
            if (key != null && row.addOn() != null) {
                key.getAddOns().add(row.addOn());
            }
        }
    }

    /**
     * Collects the assembled activation keys in their original query order.
     *
     * @return
     *  the detached activation keys
     */
    List<ActivationKey> toActivationKeys() {
        return new ArrayList<>(this.keys.values());
    }

    /**
     * Scalar fields of an activation key.
     *
     * @param id
     *  the activation key ID
     * @param name
     *  the activation key name
     * @param description
     *  the activation key description
     * @param created
     *  the creation date
     * @param updated
     *  the last update date
     * @param serviceLevel
     *  the service level
     * @param autoAttach
     *  whether auto-attach is enabled
     * @param usage
     *  the system purpose usage
     * @param role
     *  the system purpose role
     * @param releaseVer
     *  the release version
     */
    record ActivationKeyRow(String id, String name, String description, Date created,
        Date updated, String serviceLevel, Boolean autoAttach, String usage, String role, String releaseVer) {

        private ActivationKey toActivationKey(Owner owner) {
            ActivationKey key = new ActivationKey()
                .setId(this.id)
                .setName(this.name)
                .setDescription(this.description)
                .setCreated(this.created)
                .setUpdated(this.updated)
                .setServiceLevel(this.serviceLevel)
                .setAutoAttach(this.autoAttach)
                .setUsage(this.usage)
                .setRole(this.role)
                .setOwner(owner);

            if (this.releaseVer != null) {
                key.setReleaseVer(new Release(this.releaseVer));
            }

            return key;
        }
    }

    /**
     * Product reference belonging to an activation key.
     *
     * @param keyId
     *  the owning activation key ID
     * @param productId
     *  the referenced product ID
     */
    record ProductRow(String keyId, String productId) {
        // No additional members.
    }

    /**
     * Content override belonging to an activation key.
     *
     * @param keyId
     *  the owning activation key ID
     * @param contentLabel
     *  the overridden content label
     * @param name
     *  the override name
     * @param value
     *  the override value
     * @param created
     *  the creation date
     * @param updated
     *  the last update date
     */
    record ContentOverrideRow(String keyId, String contentLabel, String name,
        String value, Date created, Date updated) {

        private ActivationKeyContentOverride toContentOverride(ActivationKey key) {
            return new ActivationKeyContentOverride()
                .setContentLabel(this.contentLabel)
                .setName(this.name)
                .setValue(this.value)
                .setCreated(this.created)
                .setUpdated(this.updated)
                .setKey(key);
        }
    }

    /**
     * Pool reference belonging to an activation key.
     *
     * @param keyId
     *  the owning activation key ID
     * @param poolId
     *  the referenced pool ID
     * @param quantity
     *  the configured pool quantity
     */
    record PoolRow(String keyId, String poolId, Long quantity) {
        private ActivationKeyPool toActivationKeyPool(ActivationKey key) {
            return new ActivationKeyPool()
                .setKey(key)
                .setPoolId(this.poolId)
                .setQuantity(this.quantity);
        }
    }

    /**
     * System purpose add-on belonging to an activation key.
     *
     * @param keyId
     *  the owning activation key ID
     * @param addOn
     *  the system purpose add-on
     */
    record AddOnRow(String keyId, String addOn) {
        // No additional members.
    }
}
