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
package org.candlepin.policy.js.pool;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.candlepin.model.Pool;

/**
 * PoolUpdate: Simple DTO object for passing information about what was updated
 * on a pool from the javascript back up to the application.
 */
public class PoolUpdate {

    private Pool pool;

    /**
     * True if start/end dates for the subscription changed.
     */
    private Boolean datesChanged = false;

    /**
     * True if quantity for the pool has changed. Can be a complex calculation for some
     * pools depending on the attributes involved.
     */
    private Boolean quantityChanged = false;

    /**
     * True if product attributes have changed.
     */
    private Boolean productAttributesChanged = false;

    /**
     * True if product ID or name has changed for the pool's primary product, or on
     * any provided products. Also covers addition/removal of provided products.
     */
    private Boolean productsChanged = false;

    /**
     * True if order information has changed on the subscription.
     */
    private Boolean orderChanged = false;

    /**
     * True if derived product ID or name has changed for the pool's derived product,
     * or any
     * provided derived-products. (also covers addition/removal of derived
     * provided products.
     */
    private Boolean derivedProductsChanged = false;

    /**
     * True if derived product attributes changed. Will be false in situations where we're
     * refreshing a derived pool, and the subscription's derived product attributes have
     * changed because that update would show up as a productAttributesChanged.
     */
    private Boolean derivedProductAttributesChanged = false;

    /**
     * True if the brand mapping info on the subscription changed.
     */
    private Boolean brandingChanged = false;

    public PoolUpdate(Pool p) {
        this.pool = p;
    }

    /**
     * @return true if any subscription change was detected and applied to this pool.
     */
    public boolean changed() {
        return datesChanged || quantityChanged || productsChanged ||
            productAttributesChanged ||
            orderChanged || derivedProductsChanged || derivedProductAttributesChanged ||
            brandingChanged;
    }

    /**
     * @return a string representing the changes to the pool. Mainly used for
     * logging purposes.
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        List<String> changes = new LinkedList<String>();

        buffer.append("PoolUpdate[pool: ");
        buffer.append(pool.getId());
        buffer.append(", changes:");

        if (datesChanged) {
            changes.add("dates");
        }
        if (quantityChanged) {
            changes.add("quantity");
        }
        if (productsChanged) {
            changes.add("products");
        }
        if (productAttributesChanged) {
            changes.add("productattributes");
        }
        if (orderChanged) {
            changes.add("order");
        }
        if (derivedProductsChanged) {
            changes.add("derivedproducts");
        }
        if (derivedProductAttributesChanged) {
            changes.add("derivedproductattributes");
        }
        if (brandingChanged) {
            changes.add("branding");
        }
        buffer.append(StringUtils.join(changes, " "));
        buffer.append("]");

        return buffer.toString();

    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public Boolean getDatesChanged() {
        return datesChanged;
    }


    public void setDatesChanged(Boolean datesChanged) {
        this.datesChanged = datesChanged;
    }

    public Boolean getQuantityChanged() {
        return quantityChanged;
    }

    public void setQuantityChanged(Boolean quantityChanged) {
        this.quantityChanged = quantityChanged;
    }

    public Boolean getProductsChanged() {
        return productsChanged;
    }

    public void setProductsChanged(Boolean productsChanged) {
        this.productsChanged = productsChanged;
    }

    public Boolean getOrderChanged() {
        return orderChanged;
    }

    public void setOrderChanged(Boolean orderChanged) {
        this.orderChanged = orderChanged;
    }

    public Boolean getDerivedProductsChanged() {
        return derivedProductsChanged;
    }

    public void setDerivedProductsChanged(Boolean derivedProductsChanged) {
        this.derivedProductsChanged = derivedProductsChanged;
    }

    public Boolean getDerivedProductAttributesChanged() {
        return derivedProductAttributesChanged;
    }

    public void setDerivedProductAttributesChanged(
        Boolean derivedProductAttributesChanged) {
        this.derivedProductAttributesChanged = derivedProductAttributesChanged;
    }

    public Boolean getProductAttributesChanged() {
        return productAttributesChanged;
    }

    public void setProductAttributesChanged(Boolean productAttributesChanged) {
        this.productAttributesChanged = productAttributesChanged;
    }

    public Boolean getBrandingChanged() {
        return brandingChanged;
    }

    public void setBrandingChanged(Boolean brandingChanged) {
        this.brandingChanged = brandingChanged;
    }
}
