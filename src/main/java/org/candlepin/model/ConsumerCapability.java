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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

/**
 * ConsumerCapability
 *
 * Represents a product attribute that a consumer's rules engine can appreciate
 * and process. It is attached to a consumer record in the candlepin instance
 * upstream from the consumer. Entitlements which include the attribute can only
 * be bound to the consumer if the consumer has the capability.
 */
@XmlRootElement(name = "consumercapability")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_capability")

public class ConsumerCapability {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid2")
    @Column(length = 37)
    private String id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_capability_consumer")
    @JoinColumn(nullable = false)
    @Index(name = "cp_consumer_capability_consumer_fk_idx")
    private Consumer consumer;

    public ConsumerCapability() {
    }

    public ConsumerCapability(Consumer consumer, String name) {
        this.consumer = consumer;
        this.name = name;
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
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param consumer the consumer to set
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof ConsumerCapability)) {
            return false;
        }
        ConsumerCapability another = (ConsumerCapability) anObject;
        return name.equals(another.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
