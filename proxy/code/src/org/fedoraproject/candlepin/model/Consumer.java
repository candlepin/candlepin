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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * A Consumer is the entity that uses a given Entitlement. It can be a user,
 * system, or anything else we want to track as using the Entitlement.
 * 
 * Every Consumer has an Owner which may or may not own the Entitlement. The
 * Consumer's attributes or metadata is stored in a ConsumerInfo object which
 * boils down to a series of name/value pairs.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name="cp_consumer")
public class Consumer {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    
    @Column(nullable=false)
    private String name;
    
    @ManyToOne
    private Owner owner;
    
    // TODO: Is this worth mapping? Do we need a hierarchy amidst consumers?
    @Transient
    private Consumer parent;
    
    @Transient // TODO
    private List<Product> consumedProducts;
    
    @Transient // TODO
    private List<Entitlement> entitlements;
    
    @Transient // TODO
    private ConsumerInfo info;
    
    public Consumer(String name, Owner owner) {
        this.name = name;
        this.owner = owner;
        
        this.info = new ConsumerInfo();
        this.info.setParent(this); // TODO: ???
    }

    public Consumer() {
        this.info = new ConsumerInfo();
        this.info.setParent(this); // TODO: ???
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
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the type
     */
    public ConsumerType getType() {
        if (this.info == null) {
            return null;
        }
        else {
            return info.getType();
        }
    }

    /**
     * Set the type of this Consumer.  
     * @param typeIn to set
     */
    public void setType(ConsumerType typeIn) {
        if (this.info == null) {
            this.info = new ConsumerInfo();
        }
        this.info.setType(typeIn);
    }
    
    /**
     * @return the parent
     */
    public Consumer getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(Consumer parent) {
        this.parent = parent;
    }

    /**
     * @return the consumedProducts
     */
    public List<Product> getConsumedProducts() {
        return consumedProducts;
    }

    /**
     * @param consumedProducts the consumedProducts to set
     */
    public void setConsumedProducts(List<Product> consumedProducts) {
        this.consumedProducts = consumedProducts;
    }

    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Add a Product to this Consumer.
     * @param p Product to be consumed.
     */
    public void addConsumedProduct(Product p) {
        if (this.consumedProducts == null) {
            this.consumedProducts = new LinkedList<Product>();
        }
        this.consumedProducts.add(p);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Consumer [type=" + this.getType() + ", getName()=" +
            getName() + "]";
    }

    
    /**
     * @return Returns the info.
     */
    public ConsumerInfo getInfo() {
        return info;
    }

    
    /**
     * @param infoIn The info to set.
     */
    public void setInfo(ConsumerInfo infoIn) {
        info = infoIn;
    }
    
    /**
     * Set a metadata field
     * @param name to set
     * @param value to set
     */
    public void setMetadataField(String name, String value) {
        if (this.getInfo().getMetadata() == null) {
            this.getInfo().setMetadata(new HashMap<String, String>());
        }
        this.getInfo().getMetadata().put(name, value);
    }
    
    /**
     * Get a metadata field value
     * @param name of field to fetch
     * @return String field value.
     */
    public String getMetadataField(String name) {
       if (this.getInfo().getMetadata() != null) {
           return getInfo().getMetadata().get(name);
       }
       return null;
    }

    /**
     * @return Returns the entitlements.
     */
    public List<Entitlement> getEntitlements() {
        return entitlements;
    }

    
    /**
     * @param entitlementsIn The entitlements to set.
     */
    public void setEntitlements(List<Entitlement> entitlementsIn) {
        entitlements = entitlementsIn;
    }

    /**
     * Add an Entitlement to this Consumer
     * @param entitlementIn to add to this consumer
     * 
     */
    public void addEntitlement(Entitlement entitlementIn) {
        if (this.entitlements == null) {
            this.entitlements = new LinkedList<Entitlement>();
        }
        this.entitlements.add(entitlementIn);
        
    }


}
