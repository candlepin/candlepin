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
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

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
@Table(name="cp_entitlement")
public class Entitlement {
    
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    
    @ManyToOne
    @JoinColumn(nullable=false)
    private Owner owner;
    
    @ManyToOne
    @JoinColumn(nullable=false)
    private EntitlementPool pool;

    @Transient
//    @OneToMany(targetEntity=Product.class, cascade=CascadeType.ALL)
//    @JoinTable(name="cp_product_hierarchy", 
//            joinColumns=@JoinColumn(name="PARENT_PRODUCT_ID"), 
//            inverseJoinColumns=@JoinColumn(name="CHILD_PRODUCT_ID"))
    private List<Entitlement> childEntitlements;
    
    private Date startDate;

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

    public Entitlement(EntitlementPool poolIn, Owner ownerIn, Date startDateIn) {
        pool = poolIn;
        owner = ownerIn;
        startDate = startDateIn;
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

    /**
     * @return the childEntitlements
     */
    public List<Entitlement> getChildEntitlements() {
        return childEntitlements;
    }

    /**
     * @param childEntitlements the childEntitlements to set
     */
    public void setChildEntitlements(List<Entitlement> childEntitlements) {
        this.childEntitlements = childEntitlements;
    }

    
    /**
     * @return Returns the product.
     */
    public Product getProduct() {
        return this.pool.getProduct();
    }

    
    /**
     * @return Returns the pool.
     */
    public EntitlementPool getPool() {
        return pool;
    }

    
    /**
     * @param poolIn The pool to set.
     */
    public void setPool(EntitlementPool poolIn) {
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

    
}
