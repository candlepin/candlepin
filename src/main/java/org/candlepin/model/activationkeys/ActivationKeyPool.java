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
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
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
@Table(name = "cp_activationkey_pool",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"key_id", "pool_id"})}
)
public class ActivationKeyPool extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @ManyToOne
    @ForeignKey(name = "fk_activation_key_pool_k")
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_pool_k_fk_idx")
    private ActivationKey key;

    @ManyToOne
    @ForeignKey(name = "fk_activation_key_pool_p")
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_pool_p_fk_idx")
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
}
