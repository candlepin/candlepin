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
package org.candlepin.policy.js;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.policy.MissingFactException;

/**
 * Read-only copy of a Consumer.
 */
public class ReadOnlyConsumer {

    private final Consumer consumer;
    private String serviceLevelOverride = "";
    private Consumer host = null;

    /**
     * ctor
     * @param consumer read-write consumer to be copied.
     */
    public ReadOnlyConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    /**
     * ctor
     * @param consumer read-write consumer to be copied.
     * @param host the host of the consumer
     */
    public ReadOnlyConsumer(Consumer consumer, Consumer host) {
        this.consumer = consumer;
        this.host = host;
    }

    /**
     * ctor
     * @param consumer read-write consumer to be copied.
     */
    public ReadOnlyConsumer(Consumer consumer, String serviceLevelOverride) {
        this.consumer = consumer;
        this.setServiceLevelOverride(serviceLevelOverride);
    }

    /**
     * ctor
     * @param consumer read-write consumer to be copied.
     * @param host the host of the consumer
     */
    public ReadOnlyConsumer(Consumer consumer, Consumer host, String serviceLevelOverride) {
        this.host = host;
        this.consumer = consumer;
        this.setServiceLevelOverride(serviceLevelOverride);

    }

    /**
     * Return the consumer type
     * @return the consumer type
     */
    public String getType() {
        return consumer.getType().getLabel();
    }

    /**
     * Return the consumer name
     * @return the consumer name
     */
    public String getName() {
        return consumer.getName();
    }

    /**
     * Return the consumer uuid
     * @return the consumer uuid
     */
    public String getUuid() {
        return consumer.getUuid();
    }

    /**
     * Return the value of the fact assigned to the given key.
     * @param factKey Fact key
     * @return Fact value assigned to the given key.
     */
    public String getFact(String factKey) {
        String result = consumer.getFact(factKey);
        if (result == null) {
            throw new MissingFactException(consumer.getUuid(), factKey);
        }
        return result;
    }

    /**
     * Check if the consumer has the given fact specified.
     * @param factKey Fact to look up.
     * @return true if consumer has this fact set, false otherwise.
     */
    public boolean hasFact(String factKey) {
        String result = consumer.getFact(factKey);
        if (result == null) {
            return false;
        }
        return true;
    }

    /**
     * Return true if this Consumer has any entitlements for the given product
     * label.
     * @param poolId id of the pool
     * @return true if this Consumer has any entitlements for the given product
     * label
     */
    //TODO Is this correct? Do we need to check if the entitlement product provides
    // the requested product?
    public boolean hasEntitlement(String poolId) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getPool().getId().equals(poolId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEntitlement(ReadOnlyProduct product) {
        return hasEntitlement(product.getId());
    }

    public String getUsername() {
        return consumer.getUsername();
    }

    public boolean isManifest() {
        return consumer.getType().isManifest();
    }

    public Consumer getHost() {
        return host;
    }

    public String getServiceLevel() {
        if (serviceLevelOverride != null &&
            !serviceLevelOverride.trim().equals("")) {
            return serviceLevelOverride;
        }
        else {
            return consumer.getServiceLevel();
        }
    }

    protected void setServiceLevelOverride(String serviceLevelOverride) {
        if (serviceLevelOverride == null) {
            this.serviceLevelOverride = "";
        }
        else {
            this.serviceLevelOverride = serviceLevelOverride;
        }
    }
}
