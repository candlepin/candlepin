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

import org.fedoraproject.candlepin.util.Util;

import org.hibernate.annotations.ForeignKey;

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
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
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
@Table(name = "cp_consumer")
@SequenceGenerator(name="seq_consumer", sequenceName="seq_consumer", allocationSize=1)
public class Consumer implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="seq_consumer")
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(nullable = false)
    private String name;
    
    @ManyToOne
    @JoinColumn(nullable = false)
    @ForeignKey(name = "fk_consumer_consumer_type")
    private ConsumerType type;
    
    @ManyToOne
    @ForeignKey(name = "fk_consumer_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    // Consumers *can* be organized into a hierarchy, could be useful in cases
    // such as host/guests. 
    @ManyToOne(targetEntity = Consumer.class)
    @JoinColumn(name = "parent_consumer_id")
    private Consumer parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private Set<Consumer> childConsumers;
    
    @OneToMany(cascade = CascadeType.ALL, mappedBy="consumer")
    private Set<Entitlement> entitlements;
    
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "consumer_fact_id")
    private ConsumerFacts facts;
    
    // Separate mapping because in theory, a consumer could be consuming products they're
    // not entitled to.    
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_consumer_product_owner")
    private Set<ConsumerProduct> consumedProducts;    
    
    public Consumer(String name, Owner owner, ConsumerType type) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        
        // This constructor is for creating a new Consumer in the DB, so we'll
        // generate a UUID at this point.
        this.uuid = Util.generateUUID();

        this.facts = new ConsumerFacts(this);
        this.childConsumers = new HashSet<Consumer>();
        this.consumedProducts = new HashSet<ConsumerProduct>();
        this.entitlements = new HashSet<Entitlement>();
    }
    
    public static Consumer createFromConsumer(Consumer copyFrom, Owner owner, ConsumerType type) {
        Consumer toReturn = new Consumer(copyFrom.name, owner, type);
        toReturn.getFacts().setMetadata(copyFrom.getFacts().getMetadata());
        
        toReturn.childConsumers = copyFrom.childConsumers;
        toReturn.consumedProducts = copyFrom.consumedProducts;
        toReturn.entitlements = copyFrom.entitlements;
        
        return toReturn;
    }

    public Consumer() {
        this.uuid = Util.generateUUID();
        this.facts = new ConsumerFacts(this);
        this.childConsumers = new HashSet<Consumer>();
        this.consumedProducts = new HashSet<ConsumerProduct>();
        this.entitlements = new HashSet<Entitlement>();
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConsumerType getType() {
        return type;
    }
    
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
        child.setParent(this);
        this.childConsumers.add(child);
    }

    public Consumer getParent() {
        return parent;
    }

    public void setParent(Consumer parent) {
        this.parent = parent;
    }

    public Set<ConsumerProduct> getConsumedProducts() {
        return consumedProducts;
    }

    public void setConsumedProducts(Set<ConsumerProduct> consumedProducts) {
        this.consumedProducts = consumedProducts;
    }

    public void addConsumedProduct(ConsumerProduct p) {
        this.consumedProducts.add(p);
    }
    
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "Consumer [type = " + this.getType() + ", getName() = " +
            getName() + "]";
    }

    
    public ConsumerFacts getFacts() {
        return facts;
    }
    
    public String getFact(String factKey) {
        return facts.getFact(factKey);
    }

    
    public void setFacts(ConsumerFacts factsIn) {
        facts = factsIn;
    }
    
    /**
     * Set a metadata field
     * @param name to set
     * @param value to set
     */
    public void setMetadataField(String name, String value) {
        if (this.getFacts().getMetadata() ==  null) {
            this.getFacts().setMetadata(new HashMap<String, String>());
        }
        this.getFacts().getMetadata().put(name, value);
    }
    
    /**
     * Get a metadata field value
     * @param name of field to fetch
     * @return String field value.
     */
    public String getMetadataField(String name) {
       if (this.getFacts().getMetadata() !=  null) {
           return getFacts().getMetadata().get(name);
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

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) return true;
        if (!(anObject instanceof Consumer)) return false;
        
        Consumer another = (Consumer) anObject;
        
        return uuid.equals(another.getUuid());
    }
    
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
    
}
