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
package org.fedoraproject.candlepin.policy.js;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;

import java.util.HashSet;
import java.util.Set;

/**
 * Read-only copy of a Consumer.
 */
public class ReadOnlyConsumer {

    private final Consumer consumer;

    /**
     * ctor
     * @param consumer read-write consumer to be copied.
     */
    public ReadOnlyConsumer(Consumer consumer) {
        this.consumer = consumer;
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
     * Return the Consumer's parent
     * @return the Consumer's parent
     */
    public ReadOnlyConsumer getParent() {
        return new ReadOnlyConsumer(consumer.getParent());
    }
   
    /**
     * Return read-only versions of the child consumers.
     * @return read-only versions of the child consumers.
     */
    public Set<ReadOnlyConsumer> getChildConsumers() {
        Set<ReadOnlyConsumer> toReturn = new HashSet<ReadOnlyConsumer>();
        for (Consumer toProxy : consumer.getChildConsumers()) {
            toReturn.add(new ReadOnlyConsumer(toProxy));
        }
        return toReturn;
    }
    
    /**
     * Return the list of consumed product IDs.
     * 
     * For now, just IDs rather than actual Product objects, as these would
     * potentially require a service call. 
     * @return the list of consumed product IDs.
     */
    public Set<String> getConsumedProductIds() {
        return consumer.getConsumedProductIds();
    }
   
    /**
     * Return the value of the fact assigned to the given key.
     * @param factKey Fact key
     * @return Fact value assigned to the given key.
     */
    public String getFact(String factKey) {
        return consumer.getFact(factKey);
    }

    /**
     * Return true if this Consumer has any entitlements for the given product
     * label.
     * @param productLabel label of the product to lookup.
     * @return true if this Consumer has any entitlements for the given product
     * label
     */
    //TODO Is this correct?
    public boolean hasEntitlement(String productLabel) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProductId().equals(productLabel)) {
                return true;
            }
        }
        return false;
    }
}
