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

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Represents a guest ID running on a virt host consumer.
 *
 * NOTE: this is a guest ID, not a Candlepin consumer UUID. The guest may not be
 * registered.
 */
@Entity
@Table(name = GuestId.DB_TABLE)
public class GuestId extends AbstractHibernateObject implements Owned, Named, ConsumerProperty, Eventful {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_consumer_guests";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(name = "guest_id", nullable = false)
    @Size(max = 255)
    @NotNull
    private String guestId;

    @Column(name = "guest_id_lower", nullable = false)
    @Size(max = 255)
    @NotNull
    private String guestIdLower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    @NotNull
    private Consumer consumer;

    @ElementCollection
    @CollectionTable(name = "cp_consumer_guests_attributes",
        joinColumns = @JoinColumn(name = "cp_consumer_guest_id"))
    @MapKeyColumn(name = "mapkey")
    @Column(name = "element")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Size(max = 255)
    private Map<String, String> attributes;

    public GuestId() {
        attributes = new HashMap<>();
    }

    public GuestId(String guestId) {
        this();
        setGuestId(guestId);
    }

    public GuestId(String guestId, Consumer consumer) {
        this(guestId);
        this.consumer = consumer;
    }

    public GuestId(String guestId, Consumer consumer, Map<String, String> attributes) {
        this(guestId, consumer);
        if (attributes != null) {
            this.setAttributes(attributes);
        }
    }

    public String getGuestId() {
        return guestId;
    }

    public void setGuestId(String guestId) {
        this.guestId = guestId;

        if (guestId != null) {
            guestIdLower = guestId.toLowerCase();
        }
        else {
            guestIdLower = null;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
        this.consumer.updateRHCloudProfileModified();
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GuestId)) {
            return false;
        }
        GuestId that = (GuestId) other;
        if (this.getGuestId().equalsIgnoreCase(that.getGuestId()) &&
            this.getAttributes().equals(that.getAttributes())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 23)
            .append(getGuestId()).toHashCode();
    }

    @Override
    public String getName() {
        return guestId;
    }

    @Override
    public String getOwnerId() {
        if (consumer != null) {
            return consumer.getOwnerId();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        Consumer consumer = this.getConsumer();
        return consumer == null ? null : consumer.getOwnerKey();
    }

    public String toString() {
        return "GuestID " + getGuestId();
    }
}
