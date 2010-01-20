package org.fedoraproject.candlepin.policy.java;

import java.util.HashSet;
import java.util.Set;

import org.fedoraproject.candlepin.model.Consumer;

public class ReadOnlyConsumer {

    private final Consumer consumer;

    public ReadOnlyConsumer(Consumer consumer) {
        this.consumer = consumer;
    }
    
    public String type() {
        return consumer.getType().getLabel();
    }
    
    public String name() {
        return consumer.getName();
    }
    
    public String uuid() {
        return consumer.getUuid();
    }
    
    public ReadOnlyConsumer parent() {
        return new ReadOnlyConsumer(consumer.getParent());
    }
    
    public Set<ReadOnlyConsumer> childConsumers() {
        Set<ReadOnlyConsumer> toReturn = new HashSet<ReadOnlyConsumer>();
        for (Consumer toProxy: consumer.getChildConsumers()) {
            toReturn.add(new ReadOnlyConsumer(toProxy));
        }
        return toReturn;
    }
    
    public Set<ReadOnlyProduct> consumedProducts() {
        return ReadOnlyProduct.fromProducts(consumer.getConsumedProducts());
    }
    
    public String consumerFact(String factKey) {
        return consumer.getFact(factKey);
    }
}
