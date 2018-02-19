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
package org.candlepin.model.activationkeys;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Pool;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * SubscriptionToken
 */
@XmlRootElement
@Entity
@XmlAccessorType(XmlAccessType.PROPERTY)
@Table(name = ActivationKeyPool.DB_TABLE,
    uniqueConstraints = {@UniqueConstraint(columnNames = {"key_id", "pool_id"})})
public class ActivationKeyPool extends AbstractHibernateObject implements Comparable<ActivationKeyPool> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_activationkey_pool";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private ActivationKey key;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Pool pool;

    @Column(nullable = true, name = "quantity")
    private Long quantity;

    public ActivationKeyPool() {
    }

    public ActivationKeyPool(ActivationKey key, Pool pool, Long quantity) {
        this.key = key;
        this.pool = pool;
        this.quantity = quantity;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the key
     */
    @XmlTransient
    public ActivationKey getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKeyId(ActivationKey key) {
        this.key = key;
    }

    /**
     * @return the pool_Id
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * @param pool the pool to set
     */
    public void setPool(Pool pool) {
        this.pool = pool;
    }

    /**
     * @return the quantity
     */
    public Long getQuantity() {
        return quantity;
    }

    /**
     * @param quantity the quantity to set
     */
    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    @Override
    public int compareTo(ActivationKeyPool other) {
        int compare = this.getPool().compareTo(other.getPool());
        if (compare == 0) {
            return (this.getId() == null ^ other.getId() == null) ?
                (this.getId() == null ? -1 : 1) :
                    this.getId() == other.getId() ? 0 :
                        this.getId().compareTo(other.getId());
        }
        return compare;
    }

    @Override
    public String toString() {
        return "Activation key: " + this.getKey().getName() + ", Pool ID: " + this.getPool().getId();
    }

}
