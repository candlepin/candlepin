/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.candlepin.model.Attribute;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;

/**
 * AttributeHelper
 *
 * Helper class for dealing with {@link Attribute}s.
 */
public class AttributeHelper {
    /**
     * Both products and pools can carry attributes, we need to
     * trigger rules for each. In this map, pool attributes will
     * override product attributes, should the same key be set
     * for both.
     *
     * @param pool Pool can be null.
     * @return Map of all attribute names and values. Pool attributes
     *         have priority.
     */
    public Map<String, String> getFlattenedAttributes(Pool pool) {
        Map<String, String> allAttributes = new HashMap<String, String>();
        if (pool != null) {
            allAttributes.putAll(getFlattenedAttributes(pool.getProductAttributes()));
            allAttributes.putAll(getFlattenedAttributes(pool.getAttributes()));
        }
        return allAttributes;
    }

    public Map<String, String> getFlattenedAttributes(Set<? extends Attribute> attrs) {
        Map<String, String> flattened = new HashMap<String, String>();
        for (Attribute a : attrs) {
            flattened.put(a.getName(), a.getValue());
        }
        return flattened;
    }

    public Map<String, String> getFlattenedAttributes(Product product) {
        Map<String, String> attributes = new HashMap<String, String>();
        for (ProductAttribute attr : product.getAttributes()) {
            attributes.put(attr.getName(), attr.getValue());
        }
        return attributes;
    }
}
