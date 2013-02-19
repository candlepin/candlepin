/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.candlepin.jackson.HateoasArrayExclude;
import org.candlepin.jackson.HateoasInclude;
import org.candlepin.util.Util;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonFilter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.MapKeyManyToMany;


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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFilter("ApiHateoas")
public class Consumer extends AbstractHibernateObject implements Linkable, Owned {

    public static final String UEBER_CERT_CONSUMER = "ueber_cert_consumer";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    private String id;

    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(nullable = false)
    private String name;

    // Represents the username used to register this consumer
    @Column
    private String username;

    @Column(length = 32)
    private String entitlementStatus;

    @Column(length = 32, nullable = false)
    private String serviceLevel;

    // for selecting Y/Z strean
    @Column(length = 255, nullable =  true)
    private String releaseVer;

    /*
     * Because this object is used both as a Hibernate object, as well as a DTO to be
     * serialized and sent to callers, we do some magic with these two cert related
     * fields. The idCert is a database certificated that carries bytes, the identity
     * field is a DTO for transmission to the client carrying PEM in plain text, and is
     * not stored in the database.
     */
    @OneToOne
    @JoinColumn(name = "consumer_idcert_id")
    private IdentityCertificate idCert;

    @ManyToOne
    @JoinColumn(nullable = false)
    @ForeignKey(name = "fk_consumer_consumer_type")
    private ConsumerType type;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_owner")
    @JoinColumn(nullable = false)
    @Index(name = "cp_consumer_owner_fk_idx")
    private Owner owner;

    @ManyToOne
    @ForeignKey(name = "fk_consumer_env")
    @JoinColumn(nullable = true)
    @Index(name = "cp_consumer_env_fk_idx")
    private Environment environment;

    @Formula("(select sum(ent.quantity) from cp_entitlement ent " +
        "where ent.consumer_id = id)")
    private Long entitlementCount;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "consumer", fetch = FetchType.LAZY)
    private Set<Entitlement> entitlements;

    @MapKeyManyToMany(targetEntity = String.class)
    @CollectionOfElements(targetElement = String.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> facts;

    @OneToOne(cascade = CascadeType.ALL)
    private KeyPair keyPair;

    private Date lastCheckin;

    @OneToMany(mappedBy = "consumer", targetEntity = ConsumerInstalledProduct.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.MERGE,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ConsumerInstalledProduct> installedProducts;

    @Transient
    private boolean canActivate;

    @OneToMany(mappedBy = "consumer", targetEntity = GuestId.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.MERGE,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private List<GuestId> guestIds;

    // An instruction for the client to initiate an autoheal request.
    // WARNING: can't initialize to a default value here, we need to be able to see
    // if it was specified on an incoming update, so it must be null if no value came in.
    private Boolean autoheal;

    public Consumer(String name, String userName, Owner owner, ConsumerType type) {
        this();

        this.name = name;
        this.username = userName;
        this.owner = owner;
        this.type = type;
        this.facts = new HashMap<String, String>();
        this.installedProducts = new HashSet<ConsumerInstalledProduct>();
        this.guestIds = new ArrayList<GuestId>();
        this.autoheal = true;
        this.serviceLevel = "";
    }

    public Consumer() {
        // This constructor is for creating a new Consumer in the DB, so we'll
        // generate a UUID at this point.
        this.ensureUUID();
        this.entitlements = new HashSet<Entitlement>();
    }

    /**
     * @return the Consumer's uuid
     */
    @HateoasInclude
    public String getUuid() {
        return uuid;
    }

    public void ensureUUID() {
        if (uuid == null  || uuid.length() == 0) {
            this.uuid = Util.generateUUID();
        }
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
    @Override
    @HateoasInclude
    public String getId() {
        return id;
    }

    /**
     * @param id the db id.
     */
    public void setId(String id) {
        this.id = id;
    }

    @HateoasArrayExclude
    public IdentityCertificate getIdCert() {
        return idCert;
    }

    public void setIdCert(IdentityCertificate idCert) {
        this.idCert = idCert;
    }

    /**
     * @return the name of this consumer.
     */
    @HateoasInclude
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
     * @return the userName
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param userName the userName to set
     */
    public void setUsername(String userName) {
        this.username = userName;
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
     * @return the owner of this Consumer.
     */
    @Override
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
        return "Consumer [id = " + getId() + ", type = " + getType() + ", getName() = " +
            getName() + "]";
    }


    /**
     * @return all facts about this consumer.
     */
    @HateoasArrayExclude
    public Map<String, String> getFacts() {
        return facts;
    }

    public boolean hasFact(String fact) {
        return facts.containsKey(fact);
    }

    /**
     * Returns the value of the fact with the given key.
     * @param factKey specific fact to retrieve.
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        if (facts != null) {
            return facts.get(factKey);
        }
        return null;
    }

    /**
     * @param factsIn facts about this consumer.
     */
    public void setFacts(Map<String, String> factsIn) {
        facts = factsIn;
    }

    /**
     * Returns if the <code>other</code> consumer's facts are
     * the same as the facts of this consumer.
     *
     * @param other the Consumer whose facts to compare
     * @return <code>true</code> if the facts are the same, <code>false</code> otherwise
     */
    public boolean factsAreEqual(Consumer other) {
        Map<String, String> myFacts = getFacts();
        Map<String, String> otherFacts = other.getFacts();

        if (myFacts == null && otherFacts == null) {
            return true;
        }

        if (myFacts == null || otherFacts == null) {
            return false;
        }

        if (myFacts.size() != otherFacts.size()) {
            return false;
        }

        for (Entry<String, String> entry : myFacts.entrySet()) {
            String myVal = entry.getValue();
            String otherVal = otherFacts.get(entry.getKey());

            if (myVal == null) {
                if (otherVal != null) {
                    return false;
                }
            }
            else if (!myVal.equals(otherVal)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Set a fact
     * @param name to set
     * @param value to set
     */
    public void setFact(String name, String value) {
        if (facts == null) {
            facts = new HashMap<String, String>();
        }
        this.facts.put(name, value);
    }

    public long getEntitlementCount() {
        if (entitlementCount == null) {
            return 0;
        }
        return entitlementCount.longValue();
    }
    /**
     * @return Returns the entitlements.
     */
    @XmlTransient
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
        entitlementIn.setConsumer(this);
        this.entitlements.add(entitlementIn);
    }

    public void removeEntitlement(Entitlement entitlement) {
        this.entitlements.remove(entitlement);
    }

    @XmlTransient
    public KeyPair getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
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

    @Override
    @HateoasInclude
    public String getHref() {
        return "/consumers/" + getUuid();
    }

    @Override
    public void setHref(String href) {
        /*
         * No-op, here to aid with updating objects which have nested objects that were
         * originally sent down to the client in HATEOAS form.
         */
    }

    public Date getLastCheckin() {
        return lastCheckin;
    }

    public void setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
    }

    public boolean isCanActivate() {
        return canActivate;
    }

    public void setCanActivate(boolean canActivate) {
        this.canActivate = canActivate;
    }

    public Set<ConsumerInstalledProduct> getInstalledProducts() {
        return installedProducts;
    }

    public void setInstalledProducts(Set<ConsumerInstalledProduct> installedProducts) {
        this.installedProducts = installedProducts;
    }

    public void addInstalledProduct(ConsumerInstalledProduct installed) {
        if (installedProducts == null) {
            installedProducts = new HashSet<ConsumerInstalledProduct>();
        }
        installed.setConsumer(this);
        installedProducts.add(installed);
    }

    public Boolean isAutoheal() {
        return autoheal;
    }

    public void setAutoheal(Boolean autoheal) {
        this.autoheal = autoheal;
    }

    /**
     * @param guests the GuestIds to set
     */
    public void setGuestIds(List<GuestId> guests) {
        this.guestIds = guests;
    }

    /**
     * @return the guestIds
     */
    public List<GuestId> getGuestIds() {
        return guestIds;
    }

    public void addGuestId(GuestId guestId) {
        if (guestIds == null) {
            guestIds = new ArrayList<GuestId>();
        }
        guestId.setConsumer(this);
        guestIds.add(guestId);
    }

    public String getEntitlementStatus() {
        return entitlementStatus;
    }

    public void setEntitlementStatus(String status) {
        this.entitlementStatus = status;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String level) {
        this.serviceLevel = level;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * @param releaseVer the releaseVer to set
     */
    public void setReleaseVer(Release releaseVer) {
        if (releaseVer == null) {
            releaseVer = new Release();
        }
        this.releaseVer = releaseVer.getReleaseVer();
    }

    /**
     * @return the releaseVer
     */
    public Release getReleaseVer() {
        return new Release(releaseVer);
    }

    @Transient
    public boolean isManifest() {
        return getType().isManifest();
    }
}
