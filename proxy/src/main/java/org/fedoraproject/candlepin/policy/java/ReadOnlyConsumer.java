package org.fedoraproject.candlepin.policy.java;

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
        return ReadOnlyProduct.fromProducts(consumer.getConsumedProducts());
    }
    
    public String getFact(String factKey) {
        return consumer.getFact(factKey);
    }
    
    public boolean hasEntitlement(String productLabel) {
        for (Entitlement e : consumer.getEntitlements()) {
            if (e.getProduct().getLabel().equals(productLabel)) {
                return true;
            }
        }
        return false;
    }
}
