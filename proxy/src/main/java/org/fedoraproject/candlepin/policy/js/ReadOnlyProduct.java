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

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        for (Product toProxy : product.getChildProducts()) {
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
        for (Product toProxy : products) {
            toReturn.add(new ReadOnlyProduct(toProxy));
        }
        return toReturn;
    }

    private void initializeReadOnlyAttributes() {
        attributes = new HashMap<String, Long>();
        for (Attribute current : product.getAttributes()) {
            attributes.put(current.getName(), current.getQuantity());
        }
    }
}
