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

import org.candlepin.model.Attribute;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * represents a read-only entitlement pool
 */
public class ReadOnlyPool {

    private Pool entPool;
    private ReadOnlyProductCache productCache;
    private Map<String, String> attributes = null;
    private Map<String, String> productAttributes = null;

    /**
     * @param entPool the read-write version of the EntitlementPool to copy.
     */
    public ReadOnlyPool(Pool entPool, ReadOnlyProductCache productCache) {
        this.entPool = entPool;
        this.productCache = productCache;
        initializeReadOnlyAttributes();
    }

    /**
     * Returns true if there are available entitlements remaining.
     *
     * @return true if there are available entitlements remaining.
     */
    public Boolean entitlementsAvailable(Integer quantityToConsume) {
        return entPool.entitlementsAvailable(quantityToConsume);
    }

    public String getId() {
        return entPool.getId();
    }

    public Long getMaxMembers() {
        return entPool.getQuantity();
    }

    public Long getCurrentMembers() {
        return entPool.getConsumed();
    }

    public Date getStartDate() {
        return entPool.getStartDate();
    }

    public Date getEndDate() {
        return entPool.getEndDate();
    }

    public String getAttribute(String name) {
        return entPool.getAttributeValue(name);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getProductAttribute(String name) {
        return productAttributes.get(name);
    }

    public Map<String, String> getProductAttributes() {
        return productAttributes;
    }

    public Set<ProvidedProduct> getProvidedProducts() {
        return entPool.getProvidedProducts();
    }

    public String getProductId() {
        return entPool.getProductId();
    }

    public String getRestrictedToUsername() {
        return entPool.getRestrictedToUsername();
    }

    public Long getQuantity() {
        return entPool.getQuantity();
    }

    public static List<ReadOnlyPool> fromCollection(Collection<Pool> pools,
        ReadOnlyProductCache productCache) {
        List<ReadOnlyPool> toReturn = new ArrayList<ReadOnlyPool>(pools.size());
        for (Pool pool : pools) {
            toReturn.add(new ReadOnlyPool(pool, productCache));
        }
        return toReturn;
    }

    /**
     * Check if either the 'main' product id matches the provided id, or if any
     * of the 'supplementary' product ids match
     *
     * @param productId the product id to search for
     * @return true if found, false if not
     */
    public boolean provides(String productId) {
        return entPool.provides(productId);
    }

    private void initializeReadOnlyAttributes() {
        attributes = new HashMap<String, String>();
        Set<PoolAttribute> attributeList = entPool.getAttributes();
        if (attributeList != null) {
            for (Attribute current : attributeList) {
                attributes.put(current.getName(), current.getValue());
            }
        }

        productAttributes = new HashMap<String, String>();
        Set<ProductPoolAttribute> productAttrList = entPool.getProductAttributes();
        if (productAttrList != null) {
            for (Attribute current : productAttrList) {
                productAttributes.put(current.getName(), current.getValue());
            }
        }
    }

    public ReadOnlyProduct[] getProducts() {
        Set<ReadOnlyProduct> products = new HashSet<ReadOnlyProduct>();

        products.add(productCache.getProductById(entPool.getProductId()));

        for (ProvidedProduct providedProduct : entPool.getProvidedProducts()) {
            products.add(productCache.getProductById(providedProduct
                .getProductId()));
        }

        return products.toArray(new ReadOnlyProduct[products.size()]);
    }

    public ReadOnlyProduct getTopLevelProduct() {
        return productCache.getProductById(entPool.getProductId());
    }
}
