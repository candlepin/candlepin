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

import org.candlepin.model.Pool;

/**
 * PoolUpdate: Simple DTO object for passing information about what was updated
 * on a pool from the javascript back up to the application.
 */
public class PoolUpdate {

    private Pool pool;
    private Boolean datesChanged;
    private Boolean quantityChanged;
    private Boolean productsChanged;

    public PoolUpdate(Pool pool, Boolean datesChanged, Boolean quantityChanged,
        Boolean productsChanged) {
        this.pool = pool;
        this.datesChanged = datesChanged;
        this.quantityChanged = quantityChanged;
        this.productsChanged = productsChanged;
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
}
