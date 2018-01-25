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

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * PoolSourceStack represents the source of a derived pool from a stack.
 *
 * This allows us to enforce one-subpool-per-stack with a database constraint.
 */
@Entity
@Table(name = SourceStack.DB_TABLE)
public class SourceStack extends AbstractHibernateObject<SourceStack> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_pool_source_stack";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    /**
     * Signifies that this pool is a derived pool linked to this stack (only one
     * sub pool per stack allowed)
     */
    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String sourceStackId;

    /**
     * Derived pools belong to a consumer who owns the entitlement(s) which created them.
     * In cases where a pool is linked to a stack of entitlements, the consumer is only
     * loosely linked in the database, so instead we will link directly for any derived
     * pool.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @NotNull
    private Consumer sourceConsumer;

    /**
     * pool derived from the source
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, unique = true)
    @NotNull
    private Pool derivedPool;

    public SourceStack() {
    }

    public SourceStack(Consumer consumer, String stackId) {
        this();
        this.setSourceStackId(stackId);
        this.setSourceConsumer(consumer);
    }

    public SourceStack(Pool pool, Consumer consumer, String stackId) {
        this(consumer, stackId);
        this.setDerivedPool(pool);
    }

    /**
     * @return the sourceStackId
     */
    public String getSourceStackId() {
        return sourceStackId;
    }

    /**
     * @param sourceStackId the sourceStackId to set
     */
    public void setSourceStackId(String sourceStackId) {
        this.sourceStackId = sourceStackId;
    }

    /**
     * @return the sourceConsumer
     */
    public Consumer getSourceConsumer() {
        return sourceConsumer;
    }

    /**
     * @param sourceConsumer the sourceConsumer to set
     */
    public void setSourceConsumer(Consumer sourceConsumer) {
        this.sourceConsumer = sourceConsumer;
    }

    /**
     * @return the derivedPool
     */
    public Pool getDerivedPool() {
        return derivedPool;
    }

    /**
     * @param derivedPool the derivedPool to set
     */
    public void setDerivedPool(Pool derivedPool) {
        this.derivedPool = derivedPool;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
}
