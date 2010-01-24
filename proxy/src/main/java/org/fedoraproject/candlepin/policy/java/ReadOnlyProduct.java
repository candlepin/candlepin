package org.fedoraproject.candlepin.policy.java;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;

public class ReadOnlyProduct {

    private final Product product;
    private Map<String, Long> attributes = null;

    public ReadOnlyProduct(Product product) {
        this.product = product;
    }
    
    public String getLabel() {
        return product.getLabel();
    }
    
    public String getName() {
        return product.getName();
    }
    
    public Set<ReadOnlyProduct> getChildProducts() {
        Set<ReadOnlyProduct> toReturn = new HashSet<ReadOnlyProduct>();
        for(Product toProxy: product.getChildProducts()) {
            toReturn.add(new ReadOnlyProduct(toProxy));
        }
        return toReturn;
    }
    
    public Long getAttribute(String name) {
        if (attributes == null) {
            initializeReadOnlyAttributes();
        }
        return attributes.get(name);
    }
    
    public static Set<ReadOnlyProduct> fromProducts(Set<Product> products) {
        Set<ReadOnlyProduct> toReturn = new HashSet<ReadOnlyProduct>();
        for (Product toProxy: products) {
            toReturn.add(new ReadOnlyProduct(toProxy));
        }
        return toReturn;
    }

    private void initializeReadOnlyAttributes() {
        attributes = new HashMap<String, Long>();
        for(Attribute current: product.getAttributes()) {
            attributes.put(current.getName(), current.getQuantity());
        }
    }
}
