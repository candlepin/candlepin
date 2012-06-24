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
package org.candlepin.model;

import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement(name = "poolquantity")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PoolQuantity {

    private Pool pool;
    private Integer quantity;

    public PoolQuantity(Pool pool, Integer quantity) {
        this.pool = pool;
        this.quantity = quantity;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public Pool getPool() {
        return pool;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getQuantity() {
        return quantity;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof PoolQuantity)) {
            return false;
        }

        PoolQuantity another = (PoolQuantity) anObject;

        return
            pool.equals(another.getPool()) &&
            quantity.equals(another.getQuantity());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7).append(this.pool)
            .append(this.quantity).toHashCode();
    }

    public String toString() {
        return pool.toString() + ", Quantity: " + quantity;
    }
}
