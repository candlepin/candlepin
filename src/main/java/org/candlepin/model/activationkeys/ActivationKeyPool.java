/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;


// TODO: This really should be immutable


@Entity
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

    @Column(name = "key_id", nullable = false)
    private String activationKeyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_id", insertable = false, updatable = false)
    private ActivationKey activationKey;

    @Column(name = "pool_id", nullable = false)
    private String poolId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    private Pool pool;

    @Column(nullable = true, name = "quantity")
    private Long quantity;

    public ActivationKeyPool() {
        // Intentionally left empty
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKeyId() {
        return this.activationKeyId;
    }

    public ActivationKey getKey() {
        return this.activationKey;
    }

    public ActivationKeyPool setKey(ActivationKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        this.activationKeyId = key.getId();
        this.activationKey = key;

        return this;
    }

    public String getPoolId() {
        return this.poolId;
    }

    public Pool getPool() {
        return this.pool;
    }

    public ActivationKeyPool setPool(Pool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool is null");
        }

        this.poolId = pool.getId();
        this.pool = pool;

        return this;
    }

    /**
     * @return the quantity
     */
    public Long getQuantity() {
        return quantity;
    }

    public ActivationKeyPool setQuantity(Long quantity) {
        this.quantity = quantity;
        return this;
    }

    private void resolveActivationKeyId() {
        if (this.activationKeyId == null) {
            if (this.activationKey == null) {
                throw new IllegalStateException("No activation key provided for activation key pool");
            }

            if (this.activationKey.getId() == null) {
                throw new IllegalStateException("activation key for activation key pool lacks a valid ID");
            }

            this.activationKeyId = this.activationKey.getId();
        }
    }

    private void resolvePoolId() {
        if (this.poolId == null) {
            if (this.pool == null) {
                throw new IllegalStateException("No pool provided for activation key pool");
            }

            if (this.pool.getId() == null) {
                throw new IllegalStateException("pool for activation key pool lacks a valid ID");
            }

            this.poolId = this.pool.getId();
        }
    }

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        this.resolveActivationKeyId();
        this.resolvePoolId();
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
        ActivationKey key = this.getKey();
        Pool pool = this.getPool();

        return String.format("ActivationKeyPool [key: %s, pool: %s, quantity: %s]",
            (key != null ? key.getName() : null), (pool != null ? pool.getId() : null), this.getQuantity());
    }

}
