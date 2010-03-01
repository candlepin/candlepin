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

import org.fedoraproject.candlepin.DateSource;

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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a pool of products eligible to be consumed (entitled).
 * For every Product there will be a corresponding Pool.
 */
@XmlRootElement(name = "pool")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_entitlement_pool")
@SequenceGenerator(name = "seq_entitlement_pool",
        sequenceName = "seq_entitlement_pool", allocationSize = 1)
public class EntitlementPool implements Persisted {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_entitlement_pool")
    private Long id;
    
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    private Boolean activeSubscription = Boolean.TRUE;

    // An identifier for the subscription this pool is associated with. Note that
    // this is not a database foreign key. The subscription identified could exist
    // in another system only accessible to us as a service. Actual implementations
    // of our SubscriptionService will be used to use this data.
    private Long subscriptionId;

    /* Indicates this pool was created as a result of granting an entitlement.
     * Allows us to know that we need to clean this pool up if that entitlement
     * if ever revoked. */
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_pool_source_entitlement")
    @JoinColumn(nullable = true)
    private Entitlement sourceEntitlement;

    @Column(nullable = false)
    private Long maxMembers;

    @Column(nullable = false)
    private Long currentMembers;

    @Column(nullable = false)
    private Date startDate;
    
    @Column(nullable = false)
    private Date endDate;
    
    @Column(nullable = true)
    private String productId;    

    @CollectionOfElements
    @JoinTable(name = "ENTITLEMENT_POOL_ATTRIBUTE")
    private Set<Attribute> attributes;

    /**
     * default ctor
     */
    public EntitlementPool() {
    }

    /**
     * ctor
     * @param ownerIn owner of the pool
     * @param productIdIn product id associated with the pool.
     * @param maxMembersIn maximum members of the pool.
     * @param startDateIn when the pool started.
     * @param endDateIn when the pool expires.
     */
    public EntitlementPool(Owner ownerIn, String productIdIn, Long maxMembersIn, 
            Date startDateIn, Date endDateIn) {
        this.owner = ownerIn;
        this.productId = productIdIn;
        this.maxMembers = maxMembersIn;
        this.startDate = startDateIn;
        this.endDate = endDateIn;
        
        // Always assume no current members if creating a new pool.
        this.currentMembers = new Long(0);
    }
    
    /** {@inheritDoc} */
    public Long getId() {
        return id;
    }

    /**
     * @param id new db id.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the product id associated with this pool.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * @param productId Id of product to be associated.
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }
    
    /**
     * @return when the pool became active.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate set the pool active date.
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return when the pool expires.
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate set the pool expiration date.
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    /**
     * @return quantity
     */
    public Long getMaxMembers() {
        return maxMembers;
    }

    /**
     * @param maxMembers quantity
     */
    public void setMaxMembers(Long maxMembers) {
        this.maxMembers = maxMembers;
    }

    /**
     * @return number of active members (uses).
     */
    public Long getCurrentMembers() {
        return currentMembers;
    }

    /**
     * @param currentMembers set the activate uses.
     * TODO: is this really needed?
     */
    public void setCurrentMembers(long currentMembers) {
        this.currentMembers = currentMembers;
    }
    
    /**
     * @return owner of the pool.
     */
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner changes the owner of the pool.
     */
    @XmlTransient
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Add 1 to the current members.
     */
    public void bumpCurrentMembers() {
        this.currentMembers = this.currentMembers + 1;
    }

    /**
     * @return attributes associated with the pool.
     */
    public Set<Attribute> getAttributes() {
        return attributes;
    }

    /**
     * Replaces all of the attributes of this pool.
     * @param attributes attributes to change.
     */
    public void setAttributes(Set<Attribute> attributes) {
        this.attributes = attributes;
    }

    /**
     * returns true if the pool is considered expired based on the given date.
     * @param dateSource date to compare to.
     * @return true if the pool is considered expired based on the given date.
     */
    public boolean isExpired(DateSource dateSource) {
        return getEndDate().before(dateSource.currentDate());
    }

    /**
     * Returns true if there are entitlements available in this pool, basically
     * if the current members is less than the max members.
     * @return true if current members is less than max members.
     */
    public boolean entitlementsAvailable() {
        if (isUnlimited()) {
            return true;
        }

        if (getCurrentMembers() < getMaxMembers()) {
            return true;
        }
        return false;
    }
    /**
     * @return True if entitlement pool is unlimited.
     */
    public boolean isUnlimited() {
        if (this.getMaxMembers() < 0) {
            return true;
        }
        return false;
    }

    /**
     * @return source entitlement.
     */
    public Entitlement getSourceEntitlement() {
        return sourceEntitlement;
    }

    /**
     * @param sourceEntitlement source entitlement
     */
    public void setSourceEntitlement(Entitlement sourceEntitlement) {
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * @return subscription id associated with this pool.
     */
    public Long getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * @param subscriptionId associates the given subscription.
     */
    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    /**
     * @return true if this pool represents an active subscription.
     */
    public Boolean getActiveSubscription() {
        return activeSubscription;
    }

    /**
     * @param activeSubscription TODO
     */
    public void setActiveSubscription(Boolean activeSubscription) {
        this.activeSubscription = activeSubscription;
    }

    /**
     * @return true TODO
     */
    public Boolean isActive() {
        return activeSubscription;
    }
}
