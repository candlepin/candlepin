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

import org.apache.commons.lang.builder.HashCodeBuilder;
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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a guest ID running on a virt host consumer.
 *
 * NOTE: this is a guest ID, not a Candlepin consumer UUID. The guest may not be
 * registered.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_guests")
public class GuestId extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(name = "guest_id", nullable = false)
    private String guestId;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_guests")
    @JoinColumn(nullable = false)
    @XmlTransient
    @Index(name = "cp_consumerguest_consumer_fk_idx")
    private Consumer consumer;

    public GuestId() {
    }

    public GuestId(String guestId) {
        this.guestId = guestId;
    }

    public GuestId(String guestId, Consumer consumer) {
        this.guestId = guestId;
        this.consumer = consumer;
    }

    public String getGuestId() {
        return guestId;
    }

    public void setGuestId(String guestId) {
        this.guestId = guestId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GuestId)) {
            return false;
        }
        GuestId that = (GuestId) other;
        if (this.getGuestId().equals(that.getGuestId())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 23).append(getConsumer().getId())
            .append(getGuestId()).toHashCode();
    }
}
