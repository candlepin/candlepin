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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.fedoraproject.candlepin.dto.CertificateDto;
import org.fedoraproject.candlepin.util.Util;
import org.hibernate.annotations.ForeignKey;


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
@SequenceGenerator(name = "seq_consumer", sequenceName = "seq_consumer", allocationSize = 1)
public class Consumer implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_consumer")
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(nullable = false)
    private String name;
    
    /* 
     * Because this object is used both as a Hibernate object, as well as a DTO to be
     * serialized and sent to callers, we do some magic with these two cert related 
     * fields. The idCert is a database certificated that carries bytes, the identity
     * field is a DTO for transmission to the client carrying PEM in plain text, and is
     * not stored in the database.
     */
//    @OneToOne
    // FIXME: shouldn't be transient...
    @Transient
    private ConsumerIdentityCertificate idCert;
    
    // This is DTO'ish copy of the idCert so we can present PEM format strings.
    // This never ends up in the DB.
    @Transient
    private CertificateDto identity;
    
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
    
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "consumer")
    private Set<Entitlement> entitlements;
    
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "consumer_fact_id")
    private ConsumerFacts facts;
    
    /**
     * ctor
     * @param name name of consumer
     * @param owner owner of the consumer
     * @param type the type
     */
    public Consumer(String name, Owner owner, ConsumerType type) {
        this.name = name;
        this.owner = owner;
        this.type = type;
        
        // This constructor is for creating a new Consumer in the DB, so we'll
        // generate a UUID at this point.
        this.uuid = Util.generateUUID();

        this.facts = new ConsumerFacts(this);
        this.childConsumers = new HashSet<Consumer>();
        this.entitlements = new HashSet<Entitlement>();
    }
    
    /**
     * Utility method to construct a Consumer.
     * @param copyFrom consumer to copy
     * @param owner owner of the consumer.
     * @param type the type
     */
    public Consumer(Consumer copyFrom, Owner owner, ConsumerType type) {
        this(copyFrom.name, owner, type);
        if (copyFrom.uuid != null && copyFrom.uuid.length() > 0) {
            this.uuid = copyFrom.uuid;
        }
        getFacts().setMetadata(copyFrom.getFacts().getMetadata());
        childConsumers = copyFrom.childConsumers;
        entitlements = copyFrom.entitlements;
    }

    /**
     * default ctor
     */
    public Consumer() {
        this.uuid = Util.generateUUID();
        this.facts = new ConsumerFacts(this);
        this.childConsumers = new HashSet<Consumer>();
        this.entitlements = new HashSet<Entitlement>();
    }

    /**
     * @return the Consumer's uuid
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * @param uuid the uuid of this consumer.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * {@inheritDoc}
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the db id.
     */
    public void setId(Long id) {
        this.id = id;
    }

    @XmlTransient
    public ConsumerIdentityCertificate getIdCert() {
        return idCert;
    }

    public void setIdCert(ConsumerIdentityCertificate idCert) {
        this.idCert = idCert;
    }
    
    /**
     * @return the name of this consumer.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name of this consumer.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return this consumers type.
     */
    public ConsumerType getType() {
        return type;
    }
    
    /**
     * @param typeIn consumer type
     */
    public void setType(ConsumerType typeIn) {
        type = typeIn;
    }

    /**
     * @return child consumers.
     */
    public Set<Consumer> getChildConsumers() {
        return childConsumers;
    }

    /**
     * @param childConsumers children consumers.
     */
    public void setChildConsumers(Set<Consumer> childConsumers) {
        this.childConsumers = childConsumers;
    }
    
    /**
     * @param child child consumer.
     */
    public void addChildConsumer(Consumer child) {
        child.setParent(this);
        this.childConsumers.add(child);
    }

    /**
     * @return this Consumer's parent.
     */
    public Consumer getParent() {
        return parent;
    }

    /**
     * @param parent parant consumer
     */
    public void setParent(Consumer parent) {
        this.parent = parent;
    }
    
    /**
     * @return the owner of this Consumer.
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * Associates an owner to this Consumer.
     * @param owner owner to associate to this Consumer.
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "Consumer [type = " + this.getType() + ", getName() = " +
            getName() + "]";
    }

    
    /**
     * @return all facts about this consumer.
     */
    public ConsumerFacts getFacts() {
        return facts;
    }
    
    /**
     * Returns the value of the fact with the given key.
     * @param factKey specific fact to retrieve.
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        return facts.getFact(factKey);
    }

    
    /**
     * @param factsIn facts about this consumer.
     */
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
     * @param nameIn of field to fetch
     * @return String field value.
     */
    public String getMetadataField(String nameIn) {
        if (this.getFacts().getMetadata() != null) {
            return getFacts().getMetadata().get(nameIn);
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

    public void removeEntitlement(Entitlement entitlement) {
        this.entitlements.remove(entitlement);
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof Consumer)) {
            return false;
        }
        
        Consumer another = (Consumer) anObject;
        
        return uuid.equals(another.getUuid());
    }
    
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @XmlElement(name = "idCert")
    public CertificateDto getIdentity() {
        return identity;
    }

    public void setIdentity(CertificateDto identity) {
        this.identity = identity;
    }
    
}
