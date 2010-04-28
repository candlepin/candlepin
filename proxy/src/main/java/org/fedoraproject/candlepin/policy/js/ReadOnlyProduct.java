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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;

/**
 * Represents a read-only copy of a Product.
 */
public class ReadOnlyProduct {

    private final Product product;
    private Map<String, String> attributes = null;

    /**
     * read-only product constructor.
     * @param product read/write product to copy
     */
    public ReadOnlyProduct(Product product) {
        this.product = product;
    }
   
    /**
     * Return the product label
     * @return the product label
     */
    public String getLabel() {
        return product.getLabel();
    }
   
    /**
     * Return the product name
     * @return the product name
     */
    public String getName() {
        return product.getName();
    }
   
    /**
     * Return the read-only copies of the child Products.
     * @return the read-only copies of the child Products.
     */
    public Set<ReadOnlyProduct> getChildProducts() {
        Set<ReadOnlyProduct> toReturn = new HashSet<ReadOnlyProduct>();
        for (Product toProxy : product.getChildProducts()) {
            toReturn.add(new ReadOnlyProduct(toProxy));
        }
        return toReturn;
    }
   
    /**
     * Return product attribute matching the given name.
     * @param name attribute name
     * @return attribute value
     */
    public String getAttribute(String name) {
        if (attributes == null) {
            initializeReadOnlyAttributes();
        }
        return attributes.get(name);
    }
   
    /**
     * Return a list of read-only products from the given set of products.
     * @param products read/write version of products.
     * @return read-only versions of products.
     */
    public static Set<ReadOnlyProduct> fromProducts(Set<Product> products) {
        Set<ReadOnlyProduct> toReturn = new HashSet<ReadOnlyProduct>();
        for (Product toProxy : products) {
            toReturn.add(new ReadOnlyProduct(toProxy));
        }
        return toReturn;
    }

    private void initializeReadOnlyAttributes() {
        attributes = new HashMap<String, String>();
        Set<Attribute> attributeList = product.getAttributes();
        if (attributeList != null) {
            for (Attribute current : attributeList) {
                attributes.put(current.getName(), current.getValue());
            }
        }
    }
}
