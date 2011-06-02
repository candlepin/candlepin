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
package org.fedoraproject.candlepin.model;


import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Event - Base class for Candlepin events. Serves as both our semi-permanent
 * audit history in the database, as well as an integral part of the event
 * queue.
 */
@Entity
@Table(name = "cp_stat_history")
public class Statistic extends AbstractHibernateObject {

    private static final long serialVersionUID = 1L;

    /**
     * EntryType - Constant representing the type of this stat entry.
     */
    public enum EntryType {
        TotalConsumers, ConsumersBySocketCount, TotalSubscriptionCount, TotalSubscriptionConsumed, PerProduct, PerPool
    }

    /**
     * ValueType the type of value.
     */
    public enum ValueType {
        Raw, Percentage, Used, Consumed
    }

    // Uniquely identifies the statistic:
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    //
    @Column(nullable = false, name = "entry_type")
    @Enumerated(EnumType.STRING)
    private EntryType entryType;

    //
    @Column(nullable = true, name = "value_type")
    @Enumerated(EnumType.STRING)
    private ValueType valueType;

    //
    @Column(nullable = true, name = "value_reference")
    private String valueReference;

    //
    @Column(nullable = false)
    private int value;

    //
    @Column(nullable = true, name = "owner_id")
    private String ownerId;

    public Statistic() {
    }

    public Statistic(EntryType entryType, ValueType valueType,
        String valueReference, int value, String ownerId) {
        this.entryType = entryType;
        this.valueType = valueType;
        this.valueReference = valueReference;
        this.value = value;
        this.ownerId = ownerId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

    public String getValueReference() {
        return valueReference;
    }

    public void setValueReference(String valueReference) {
        this.valueReference = valueReference;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String toString() {
        return "Event [" + "id=" + getId() + ", entry type=" + getEntryType() +
            ", value type=" + getValueType() + ", value=" + getValue() +
            ", created=" + getCreated() + "]";
    }

}
