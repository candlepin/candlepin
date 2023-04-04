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
package org.candlepin.model;

import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;


/**
 * ConsumerCapability
 *
 * Represents a product attribute that a consumer's rules engine can appreciate
 * and process. It is attached to a consumer record in the candlepin instance
 * upstream from the consumer. Entitlements which include the attribute can only
 * be bound to the consumer if the consumer has the capability.
 */
@Entity
@Table(name = ConsumerCapability.DB_TABLE)
public class ConsumerCapability implements Serializable {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_consumer_capability";

    private static final long serialVersionUID = -7690166510977579116L;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @NotNull
    private Consumer consumer;

    public ConsumerCapability() {
        // intentionally left empty
    }

    public ConsumerCapability(String name) {
        this.setName(name);
    }

    /**
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    /**
     * @return the capability name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the capability name to set
     *
     * @return
     *  a reference to this ConsumerCapability instance
     */
    public ConsumerCapability setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param consumer the consumer to set
     *
     * @return
     *  a reference to this ConsumerCapability instance
     */
    public ConsumerCapability setConsumer(Consumer consumer) {
        this.consumer = consumer;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ConsumerCapability)) {
            return false;
        }

        return Objects.equals(this.getName(), ((ConsumerCapability) obj).getName());
    }

    @Override
    public int hashCode() {
        String name = this.getName();
        return name != null ? name.hashCode() : 0;
    }
}
