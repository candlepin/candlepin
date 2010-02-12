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

import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.ForeignKey;

import java.util.Date;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "cp_subscription")
@SequenceGenerator(name="seq_subscription", sequenceName="seq_subscription", allocationSize=1)
public class Subscription implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_subscription")
    private Long id;

    @ManyToOne
    @ForeignKey(name = "fk_subscription_owner")
    @JoinColumn(nullable = false)
    private Owner owner;

    private String productId;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Date startDate;

    @Column(nullable = false)
    private Date endDate;

    @CollectionOfElements
    @JoinTable(name="SUBSCRIPTION_ATTRIBUTE")
    private Set<Attribute> attributes;
    
    private Date modified;

    public Subscription() {
    }

    public Subscription(Owner ownerIn, String productIdIn, Long maxMembersIn,
            Date startDateIn, Date endDateIn, Date modified) {
        this.owner = ownerIn;
        this.productId = productIdIn;
        this.quantity = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        this.modified = modified;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Set<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }
}
