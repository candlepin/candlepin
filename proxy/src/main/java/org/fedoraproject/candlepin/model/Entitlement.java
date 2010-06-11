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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.ParamDef;



/**
 * Entitlements are documents either signed XML or other certificate which 
 * control what a particular Consumer can use. There are a number of types
 * of Entitlements:
 * 
 *  1. Quantity Limited (physical & virtual)
 *  2. Version Limited
 *  3. Hardware Limited (i.e # of sockets, # of cores, etc)
 *  4. Functional Limited (i.e. Update, Management, Provisioning, etc)
 *  5. Site License
 *  6. Floating License
 *  7. Value-Based or "Metered" (i.e. per unit of time, per hardware
 *     consumption, etc)
 *  8. Draw-Down (i.e. 100 hours or training classes to be consumed over
 *     some period of time or limited number of support calls)
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@FilterDefs({
    @FilterDef(
        name = "Entitlement_OWNER_FILTER", 
        parameters = @ParamDef(name = "owner_id", type = "long")
    ),
    @FilterDef(
        name = "Entitlement_CONSUMER_FILTER", 
        parameters = @ParamDef(name = "consumer_id", type = "long")
    )
})
@Filters({
    @Filter(name = "Entitlement_OWNER_FILTER", 
        condition = "owner_id = :owner_id"
    ),
    @Filter(name = "Entitlement_CONSUMER_FILTER", 
        condition = "id in (select e.id from cp_entitlement e " +
            "inner join cp_consumer_entitlements con_en on e.id = con_en.entitlement_id " + 
            "and con_en.consumer_id = :consumer_id)"
    )
})
@Table(name = "cp_entitlement")
@SequenceGenerator(name = "seq_entitlement", sequenceName = "seq_entitlement",
        allocationSize = 1)
public class Entitlement extends AbstractHibernateObject implements AccessControlEnforced {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_entitlement")
    private Long id;
    
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    @ManyToOne
    @ForeignKey(name = "fk_consumer_id",
                inverseName = "fk_entitlement_id")
    @JoinTable(name = "cp_consumer_entitlements",
            joinColumns = @JoinColumn(name = "entitlement_id"),
            inverseJoinColumns = @JoinColumn(name = "consumer_id"))
    private Consumer consumer;
    
    @ManyToOne
    @ForeignKey(name = "fk_entitlement_entitlement_pool")
    @JoinColumn(nullable = false)
    private Pool pool;

    private Date startDate;
    
    // Not positive this should be mapped here, not all entitlements will have
    // certificates.
    @OneToMany(mappedBy = "entitlement", cascade = CascadeType.ALL)
    private Set<EntitlementCertificate> certificates = 
        new HashSet<EntitlementCertificate>();
    
    // Was this entitlement created for free, or did it consume a slot in it's pool:
    // TODO: Find a better way to represent this, we can't really clean it up properly 
    // like this.
    private Boolean isFree = Boolean.FALSE;
    
    private Integer quantity;

    /**
     * default ctor
     */
    public Entitlement() {
    }
    
    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * ctor
     * @param poolIn pool associated with the entitlement
     * @param consumerIn consumer associated with the entitlement
     * @param startDateIn when the entitlement starts.
     */
    public Entitlement(Pool poolIn, Consumer consumerIn, Date startDateIn,
            Integer quantityIn) {
        pool = poolIn;
        owner = consumerIn.getOwner();
        consumer = consumerIn;
        startDate = startDateIn;
        quantity = quantityIn == null || quantityIn.intValue() < 1 ? 
            new Integer(1) : quantityIn;
    }
    
    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param ownerIn the owner to set
     */
    public void setOwner(Owner ownerIn) {
        this.owner = ownerIn;
    }

    // REMOVE
    /**
     * @return Returns the product.
     */
    public String getProductId() {
        return this.pool.getProvidedProductIds().iterator().next();
    }

    /**
     * @return Returns the pool.
     */
    public Pool getPool() {
        return pool;
    }

    /**
     * @param poolIn The pool to set.
     */
    public void setPool(Pool poolIn) {
        pool = poolIn;
    }

    /**
     * @return Returns the startDate.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDateIn The startDate to set.
     */
    public void setStartDate(Date startDateIn) {
        startDate = startDateIn;
    }

    /**
     * @return return the associated Consumer
     */
    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * associates the given consumer with this entitlement.
     * @param consumer consumer to associate.
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    /**
     * @return returns true if the entitlement is free.
     */
    public Boolean isFree() {
        return getIsFree();
    }
    
    /**
     * @return true if the entitlement is free.
     * TODO: why do we have this method?
     */
    public Boolean getIsFree() {
        return isFree;
    }

    /**
     * @param isFree true if entitlement should be available.
     */
    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @XmlTransient
    public Set<EntitlementCertificate> getCertificates() {
        return certificates;
    }

    public void setCertificates(Set<EntitlementCertificate> certificates) {
        this.certificates = certificates;
    } 
    
    public String toString() {
        return "Entitlement[id=" + getId() + ", product=" + getProductId() + 
            ", consumer= " + consumer.getUuid() + "]";
    }

    @Override
    public boolean shouldGrantAccessTo(Owner owner) {
        return this.consumer.getOwner().equals(owner);
    }

    @Override
    public boolean shouldGrantAccessTo(Consumer consumer) {
        return this.consumer.equals(consumer);
    }
}
