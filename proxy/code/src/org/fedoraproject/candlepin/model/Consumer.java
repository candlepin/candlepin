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
import java.util.HashSet;
import java.util.Set;

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
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
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
    @JoinColumn(nullable=false)
    private ConsumerType type;
    
    @ManyToOne
    private Owner owner;
    
    // TODO: Is this worth mapping? Do we need a hierarchy amidst consumers?
    @OneToMany(targetEntity=Consumer.class, cascade=CascadeType.ALL)
    @JoinTable(name="cp_consumer_hierarchy",
            joinColumns=@JoinColumn(name="PARENT_CONSUMER_ID"),
            inverseJoinColumns=@JoinColumn(name="CHILD_CONSUMER_ID"))
    private Set<Consumer> childConsumers;

    // TODO: Are we sure we want to track this explicitly? Wouldn't we examine the 
    // entitlements we're consuming and the products associated to them for this info?
    @OneToMany
    @JoinTable(name="cp_consumer_products",
            joinColumns=@JoinColumn(name="consumer_id"),
            inverseJoinColumns=@JoinColumn(name="product_id"))
    private Set<Product> consumedProducts;
    
    @Transient // TODO
    private Set<Entitlement> entitlements;
    
    @OneToOne(cascade=CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private ConsumerInfo info;
    
    public Consumer(String name, Owner owner, ConsumerType type) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        
        this.info = new ConsumerInfo();
        this.info.setConsumer(this); // TODO: ???
        this.childConsumers = new HashSet<Consumer>();
        this.consumedProducts = new HashSet<Product>();
        this.entitlements = new HashSet<Entitlement>();
    }

    public Consumer() {
        this.info = new ConsumerInfo();
        this.info.setConsumer(this); // TODO: ???
        this.childConsumers = new HashSet<Consumer>();
        this.consumedProducts = new HashSet<Product>();
        this.entitlements = new HashSet<Entitlement>();
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
     * @return Returns the type.
     */
    public ConsumerType getType() {
        return type;
    }
    
    /**
     * @param typeIn The type to set.
     */
    public void setType(ConsumerType typeIn) {
        type = typeIn;
    }

    public Set<Consumer> getChildConsumers() {
        return childConsumers;
    }

    public void setChildConsumers(Set<Consumer> childConsumers) {
        this.childConsumers = childConsumers;
    }

    public void addChildConsumer(Consumer child) {
        this.childConsumers.add(child);
    }

    /**
     * @return the consumedProducts
     */
    public Set<Product> getConsumedProducts() {
        return consumedProducts;
    }

    /**
     * @param consumedProducts the consumedProducts to set
     */
    public void setConsumedProducts(Set<Product> consumedProducts) {
        this.consumedProducts = consumedProducts;
    }

    /**
     * Add a Product to this Consumer.
     * @param p Product to be consumed.
     */
    public void addConsumedProduct(Product p) {
        this.consumedProducts.add(p);
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
    public Set<Entitlement> getEntitlements() {
        return entitlements;
    }

    
    /**
     * @param entitlementsIn The entitlements to set.
     */
    public void setEntitlements(Set<Entitlement> entitlementsIn) {
        entitlements = entitlementsIn;
    }

    /**
     * Add an Entitlement to this Consumer
     * @param entitlementIn to add to this consumer
     * 
     */
    public void addEntitlement(Entitlement entitlementIn) {
        this.entitlements.add(entitlementIn);
        
    }


}
