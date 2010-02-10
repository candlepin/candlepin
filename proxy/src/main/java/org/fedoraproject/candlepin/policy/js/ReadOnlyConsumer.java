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

import java.util.HashSet;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;

public class ReadOnlyConsumer {

    private final Consumer consumer;

    public ReadOnlyConsumer(Consumer consumer) {
        this.consumer = consumer;
    }
    
    public String getType() {
        return consumer.getType().getLabel();
    }
    
    public String getName() {
        return consumer.getName();
    }
    
    public String getUuid() {
        return consumer.getUuid();
    }
    
    public ReadOnlyConsumer getParent() {
        return new ReadOnlyConsumer(consumer.getParent());
    }
    
    public Set<ReadOnlyConsumer> getChildConsumers() {
        Set<ReadOnlyConsumer> toReturn = new HashSet<ReadOnlyConsumer>();
        for (Consumer toProxy: consumer.getChildConsumers()) {
            toReturn.add(new ReadOnlyConsumer(toProxy));
        }
        return toReturn;
    }
    
    public Set<ReadOnlyProduct> getConsumedProducts() {
        //TODO HOW DO I FIX THIS
        //return ReadOnlyProduct.fromProducts(consumer.getConsumedProducts());
        return null ;
    }
    
    public String getFact(String factKey) {
        return consumer.getFact(factKey);
    }
    
    //TODO Is this correct?
    public boolean hasEntitlement(String productLabel) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProduct().equals(productLabel)) {
                return true;
            }
        }
        return false;
    }
}
