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

import org.candlepin.auth.AuthenticationMethod;
import org.candlepin.exceptions.DuplicateEntryException;
import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.util.Util;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * A Consumer is the entity that uses a given Entitlement. It can be a user,
 * system, or anything else we want to track as using the Entitlement.
 *
 * Every Consumer has an Owner which may or may not own the Entitlement. The
 * Consumer's attributes or metadata is stored in a ConsumerInfo object which
 * boils down to a series of name/value pairs.
 */
@Entity
@Table(name = Consumer.DB_TABLE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Consumer extends AbstractHibernateObject<Consumer> implements Linkable, Owned, Named,
    ConsumerProperty, Eventful, ConsumerInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_consumer";

    // This is based on the limitation of the name in the generated identity certificate
    // BZ 1451107
    public static final int MAX_LENGTH_OF_CONSUMER_NAME = 250;

    /**
     * Commonly used/recognized consumer facts
     */
    public static final class Facts {
        /**
         * The 'uname.machine' fact provides the system architecture information for a particular consumer.
         * This architecture information can then be used to determine what content the system can use.
         */
        public static final String ARCHITECTURE = "uname.machine";

        /** Used by the rules.js */
        public static final String BAND_STORAGE_USAGE = "band.storage.usage";

        /** The number of cores per socket on a given consumer; also part of the cloud profile and rules.js */
        public static final String CPU_CORES_PER_SOCKET = "cpu.core(s)_per_socket";

        /** The number of sockets on a given consumer; also part of the cloud profile and rules.js */
        public static final String CPU_SOCKETS = "cpu.cpu_socket(s)";

        /** */
        public static final String DISTRIBUTOR_VERSION = "distributor_version";

        /** The consumer's system/hardware UUID; also part of the cloud profile */
        public static final String DMI_SYSTEM_UUID = "dmi.system.uuid";

        /** The amount of memory on a given consumer; also part of the cloud profile and rules.js */
        public static final String MEMORY_MEMTOTAL = "memory.memtotal";

        /**
         * Unordered, comma-delimited architectures supported by the consumer; used for cert generation and
         * content filtering
         */
        public static final String SUPPORTED_ARCHITECTURES = "supported_architectures";

        /** The version of the Candlepin certificate system to use for this consumer */
        public static final String SYSTEM_CERTIFICATE_VERSION = "system.certificate_version";

        /** Whether or not the consumer is a virtual/guest system; also part of the cloud profile */
        public static final String VIRT_IS_GUEST = "virt.is_guest";

        /** The consumer's guest UUID; used to match them to a hypervisor on virt-who checkin */
        public static final String VIRT_UUID = "virt.uuid";

        // Cloud profile facts
        // These facts aren't used by Candlepin directly (other than to determine whether or not to
        // update the "last cloud profile update" timestamp), but are critical to the function of
        // some client applications, and must be maintained and passed through properly.
        public static final String DMI_BIOS_VENDOR = "dmi.bios.vendor";
        public static final String DMI_BIOS_VERSION = "dmi.bios.version";
        public static final String DMI_CHASSIS_ASSET_TAG = "dmi.chassis.asset_tag";
        public static final String DMI_SYSTEM_MANUFACTURER = "dmi.system.manufacturer";
        public static final String OCM_UNITS = "ocm.units";
    }


    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @Column(nullable = false)
    @Size(max = MAX_LENGTH_OF_CONSUMER_NAME)
    @NotNull
    private String name;

    // Represents the username used to register this consumer
    @Column
    @Size(max = 255)
    private String username;

    @Column(length = 32)
    @Size(max = 32)
    private String entitlementStatus;

    /**
     * Represents a 256 bit hash digest of the last calculated ComplianceStatus that was
     * generated by ComplianceRules.
     */
    @Column
    @Size(max = 64)
    private String complianceStatusHash;

    @Column(length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String serviceLevel;

    @Column(name = "sp_role", length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String role;

    @Column(name = "sp_usage", length = 255, nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String usage;

    @ElementCollection
    @CollectionTable(name = "cp_sp_add_on", joinColumns = @JoinColumn(name = "consumer_id"))
    @Column(name = "add_on")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<String> addOns;

    @Column(name = "sp_status", length = 32)
    @Size(max = 32)
    private String systemPurposeStatus;

    /**
     * Represents a 256 bit hash digest of the last calculated system purpose status.
     */
    @Column(name = "sp_status_hash")
    @Size(max = 64)
    private String systemPurposeStatusHash;

    // for selecting Y/Z stream
    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    private String releaseVer;

    /*
     * Because this object is used both as a Hibernate object, as well as a DTO to be
     * serialized and sent to callers, we do some magic with these two cert related
     * fields. The idCert is a database certificated that carries bytes, the identity
     * field is a DTO for transmission to the client carrying PEM in plain text, and is
     * not stored in the database.
     */
    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "consumer_idcert_id")
    private IdentityCertificate idCert;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cont_acc_cert_id")
    private SCACertificate contentAccessCert;

    // Reference to the ConsumerType by ID
    @Column(name = "type_id")
    @NotNull
    private String typeId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private Owner owner;

    @Column(name = "entitlement_count")
    @NotNull
    private Long entitlementCount;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "consumer", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Entitlement> entitlements;

    // TODO: FIXME:
    // Hibernate has a known issue with map-backed element collections that contain entries with
    // null values. Upon persist, and entries with null values will be silently discarded rather
    // than persisted, even if the configuration explicitly allows such a thing.
    @ElementCollection
    @CollectionTable(name = "cp_consumer_facts", joinColumns = @JoinColumn(name = "cp_consumer_id"))
    @MapKeyColumn(name = "mapkey")
    @Column(name = "element")
    //FIXME A cascade shouldn't be necessary here as ElementCollections cascade by default
    //See http://stackoverflow.com/a/7696147
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Map<String, String> facts;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "keypair_id")
    private KeyPairData keyPairData;

    private Date lastCheckin;

    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    private Set<ConsumerInstalledProduct> installedProducts;

    @Transient
    private boolean canActivate;

    @BatchSize(size = 32)
    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<GuestId> guestIds;

    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<ConsumerCapability> capabilities;

    @OneToOne(mappedBy = "consumer",
        orphanRemoval = true, cascade = { CascadeType.ALL })
    private HypervisorId hypervisorId;

    @Valid  // Enable validation.  See http://stackoverflow.com/a/13992948
    @ElementCollection
    @CollectionTable(name = "cp_consumer_content_tags", joinColumns = @JoinColumn(name = "consumer_id"))
    @Column(name = "content_tag")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<String> contentTags;

    // An instruction for the client to initiate an autoheal request.
    // WARNING: can't initialize to a default value here, we need to be able to see
    // if it was specified on an incoming update, so it must be null if no value came in.
    private Boolean autoheal;

    // This is normally used from the owner setting. If this consumer is manifest then it
    // can have a different setting as long as it exists in the owner's mode list.
    @Column(name = "content_access_mode")
    private String contentAccessMode;

    /**
     * Length of field is required by hypersonic in the unit tests only
     *
     * 4194304 bytes = 4 MB
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "annotations", length = 4194304)
    private String annotations;

    @Column(name = "rh_cloud_profile_modified")
    private Date rhCloudProfileModified;

    @Basic(fetch = FetchType.LAZY)
    @OneToMany(mappedBy = "consumer", orphanRemoval = true, cascade = { CascadeType.ALL })
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<ConsumerActivationKey> activationKeys;

    @Column(name = "sp_service_type")
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String serviceType;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 1000)
    @CollectionTable(name = "cp_consumer_environments", joinColumns = @JoinColumn(name = "cp_consumer_id"))
    @MapKeyColumn(name = "priority")
    @Column(name = "environment_id")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @Fetch(FetchMode.SELECT)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Map<String, String> environmentIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "reg_auth_method")
    private AuthenticationMethod registrationAuthenticationMethod;

    @OneToOne(mappedBy = "consumer",
        orphanRemoval = true, cascade = { CascadeType.ALL })
    private ConsumerCloudData consumerCloudData;

    public Consumer() {
        this.addOns = new HashSet<>();
        this.entitlements = new HashSet<>();
        this.facts = new HashMap<>();
        this.installedProducts = new HashSet<>();
        this.guestIds = new ArrayList<>();
        this.capabilities = new HashSet<>();
        this.contentTags = new HashSet<>();
        this.activationKeys = new HashSet<>();
        this.environmentIds = new LinkedHashMap<>();

        this.autoheal = true;
        this.serviceLevel = "";
        this.entitlementCount = 0L;
    }

    /**
     * Required by ConsumerProperty
     */
    @Override
    public Consumer getConsumer() {
        return this;
    }

    /**
     * @return the Consumer's UUID
     */
    public String getUuid() {
        return uuid;
    }

    @PrePersist
    public void ensureUUID() {
        if (uuid == null  || uuid.length() == 0) {
            this.uuid = Util.generateUUID();
        }
    }

    /**
     * @param uuid the UUID of this consumer.
     * @return this consumer.
     */
    public Consumer setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * @param id the db id.
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer setId(String id) {
        this.id = id;
        return this;
    }

    public IdentityCertificate getIdCert() {
        return idCert;
    }

    public Consumer setIdCert(IdentityCertificate idCert) {
        this.idCert = idCert;
        return this;
    }

    public SCACertificate getContentAccessCert() {
        return contentAccessCert;
    }

    public Consumer setContentAccessCert(SCACertificate contentAccessCert) {
        this.contentAccessCert = contentAccessCert;
        return this;
    }

    /**
     * @return the name of this consumer.
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name of this consumer.
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @return the userName
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param userName the userName to set
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setUsername(String userName) {
        this.username = userName;
        return this;
    }

    /**
     * @return this consumers type.
     */
    public String getTypeId() {
        return this.typeId;
    }

    /**
     * Sets the ID of the consumer type to of this consumer.
     *
     * @param typeId
     *  The ID of the consumer type to use for this consumer
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setTypeId(String typeId) {
        this.typeId = typeId;
        this.updateRHCloudProfileModified();

        return this;
    }

    /**
     * Sets the consumer type of this consumer.
     *
     * @param type
     *  The ConsumerType instance to use as the type for this consumer
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setType(ConsumerType type) {
        if (type == null || type.getId() == null) {
            throw new IllegalArgumentException("type is null or has not been persisted");
        }

        return this.setTypeId(type.getId());
    }

    /**
     * @return the owner Id of this Consumer.
     */
    @Override
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOwnerKey() {
        Owner owner = this.getOwner();
        return owner == null ? null : owner.getKey();
    }

    /**
     * Fetches the owner of this consumer, if the owner ID is set. This may perform a lazy lookup of the
     * owner, and should generally be avoided if the owner ID is sufficient.
     *
     * @return
     *  The owner of this consumer, if the owner ID is populated; null otherwise.
     */
    @Override
    public Owner getOwner() {
        return this.owner;
    }

    public Consumer setOwner(Owner owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner is null or lacks an ID");
        }

        this.owner = owner;
        this.ownerId = owner.getId();

        return this;
    }

    /**
     * Checks if the incoming facts contain changes or new entries to one or more "cloud profile"
     * facts. Note that the removal of a fact will not be detected by this method.
     *
     * @param incomingFacts
     *  a map containing the "incoming" facts to add or update for this consumer
     *
     * @return
     *  true if the incoming facts contain one or more new or updated cloud profile facts; false
     *  otherwise
     */
    public boolean checkForCloudProfileFacts(Map<String, String> incomingFacts) {
        // TODO: FIXME: This does not catch the case where cloud attributes are cleared after being
        // set on the consumer.

        if (incomingFacts == null) {
            return false;
        }

        Map<String, String> existingFacts = this.getFacts();
        if (existingFacts != null && existingFacts.equals(incomingFacts)) {
            return false;
        }

        for (CloudProfileFacts profileFact : CloudProfileFacts.values()) {
            if (incomingFacts.containsKey(profileFact.getFact())) {

                if (existingFacts == null || !existingFacts.containsKey(profileFact.getFact()) ||
                    !existingFacts.get(profileFact.getFact())
                    .equals(incomingFacts.get(profileFact.getFact()))) {

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @return all facts about this consumer.
     */
    public Map<String, String> getFacts() {
        return this.facts != null ? Collections.unmodifiableMap(this.facts) : Map.of();
    }

    /**
     * Returns the value of the fact with the given key.
     * @param factKey specific fact to retrieve.
     * @return the value of the fact with the given key.
     */
    public String getFact(String factKey) {
        Map<String, String> facts = this.getFacts();
        return facts != null ? facts.get(factKey) : null;
    }

    public boolean hasFact(String fact) {
        Map<String, String> facts = this.getFacts();
        return facts != null && facts.containsKey(fact);
    }

    /**
     * @param facts facts about this consumer.
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setFacts(Map<String, String> facts) {
        if (this.checkForCloudProfileFacts(facts)) {
            this.updateRHCloudProfileModified();
        }

        if (this.facts == null) {
            this.facts = new HashMap<>();
        }
        this.facts.clear();

        if (facts != null) {
            this.facts.putAll(facts);
        }

        return this;
    }

    /**
     * Returns if the <code>otherFacts</code> are
     * the same as the facts of this consumer model entity.
     *
     * @param otherFacts the facts to compare
     * @return <code>true</code> if the facts are the same, <code>false</code> otherwise
     */
    public boolean factsAreEqual(Map<String, String> otherFacts) {
        if (this.getFacts() == null && otherFacts == null) {
            return true;
        }

        if (this.getFacts() == null || otherFacts == null) {
            return false;
        }

        if (this.getFacts().size() != otherFacts.size()) {
            return false;
        }

        for (Entry<String, String> entry : this.getFacts().entrySet()) {
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
     * Sets the specified fact for this consumer. If the fact is a cloud profile fact, the cloud
     * profile last-modified time for this consumer will be updated.
     * <p></p>
     * <strong>Warning:</strong> setting a fact's value to null or empty is permitted, and
     * <strong>will not</strong> result in the removal of the fact. The null/empty value will be
     * stored as-is, with any empty-value normalization the database layer may perform on it.
     *
     * @param name
     *  the name of the fact to set; cannot be null or empty
     *
     * @param value
     *  the value for the fact; may be null or empty
     *
     * @throws IllegalArgumentException
     *  if the fact name is null or empty
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer setFact(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is null or empty");
        }

        if (this.facts == null) {
            this.facts = new HashMap<>();
        }

        if (this.checkForCloudProfileFacts(Collections.singletonMap(name, value))) {
            this.updateRHCloudProfileModified();
        }

        this.facts.put(name, value);
        return this;
    }

    /**
     * Removes a fact from this consumer, if it exists. If the fact exists and is a cloud profile
     * fact, the cloud profile last-modified time for this consumer will be updated.
     *
     * @param name
     *  the name of the fact to remove; cannot be null or empty
     *
     * @throws IllegalArgumentException
     *  if the fact name is null or empty
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer removeFact(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is null or empty");
        }

        if (this.facts != null && this.facts.containsKey(name)) {
            this.facts.remove(name);

            if (this.checkForCloudProfileFacts(Collections.singletonMap(name, null))) {
                this.updateRHCloudProfileModified();
            }
        }

        return this;
    }

    public long getEntitlementCount() {
        return this.entitlementCount != null ? entitlementCount.longValue() : 0;
    }

    public void setEntitlementCount(long count) {
        this.entitlementCount = count;
    }

    /**
     * @return Returns the entitlements.
     */
    public Set<Entitlement> getEntitlements() {
        return this.entitlements != null ? Collections.unmodifiableSet(this.entitlements) : Set.of();
    }

    /**
     * @param entitlements The entitlements to set.
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer setEntitlements(Collection<Entitlement> entitlements) {
        if (this.entitlements == null) {
            this.entitlements = new HashSet<>();
        }
        this.entitlements.clear();

        if (entitlements != null) {
            entitlements.stream()
                .filter(Objects::nonNull)
                .peek(entitlement -> entitlement.setConsumer(this))
                .forEach(this.entitlements::add);
        }

        return this;
    }

    /**
     * Add an Entitlement to this Consumer
     * @param entitlement to add to this consumer
     *
     * @return
     *  true if the entitlement was added successfully; false otherwise
     */
    public boolean addEntitlement(Entitlement entitlement) {
        if (this.entitlements == null) {
            this.entitlements = new HashSet<>();
        }

        if (entitlement != null) {
            entitlement.setConsumer(this);
            return this.entitlements.add(entitlement);
        }

        return false;
    }

    public boolean removeEntitlement(Entitlement entitlement) {
        return this.entitlements != null && this.entitlements.remove(entitlement);
    }

    /*
     * Only for internal use as a pojo for resource update.
     */
    public Consumer setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
        return this;
    }

    public KeyPairData getKeyPairData() {
        return keyPairData;
    }

    public Date getLastCheckin() {
        return this.lastCheckin;
    }

    public Consumer setKeyPairData(KeyPairData keyPairData) {
        this.keyPairData = keyPairData;
        return this;
    }

    @Override
    public String getHref() {
        return "/consumers/" + this.getUuid();
    }

    public boolean isCanActivate() {
        return this.canActivate;
    }

    public Consumer setCanActivate(boolean canActivate) {
        this.canActivate = canActivate;
        return this;
    }

    public Set<ConsumerInstalledProduct> getInstalledProducts() {
        return this.installedProducts != null ?
            Collections.unmodifiableSet(this.installedProducts) :
            Set.of();
    }

    public Consumer setInstalledProducts(Collection<ConsumerInstalledProduct> installedProducts) {
        if (this.installedProducts == null) {
            this.installedProducts = new HashSet<>();
        }
        this.installedProducts.clear();

        if (installedProducts != null) {
            installedProducts.stream()
                .filter(Objects::nonNull)
                .peek(cip -> cip.setConsumer(this))
                .forEach(this.installedProducts::add);
        }

        this.updateRHCloudProfileModified();
        return this;
    }

    public boolean addInstalledProduct(ConsumerInstalledProduct installed) {
        if (this.installedProducts == null) {
            this.installedProducts = new HashSet<>();
        }

        boolean result = false;

        if (installed != null) {
            installed.setConsumer(this);
            result = this.installedProducts.add(installed);
        }

        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    /**
     * Removes an installed product from this consumer, if present. If this removal operation
     * results in a change to the installed products, the cloud profile last-modified time for this
     * consumer will be updated.
     *
     * @param installed
     *  the installed product to remove from this consumer
     *
     * @return
     *  true if the given installed product was found and removed; false otherwise
     */
    public boolean removeInstalledProduct(ConsumerInstalledProduct installed) {
        boolean result = this.installedProducts != null && this.installedProducts.remove(installed);
        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    public Boolean isAutoheal() {
        return autoheal;
    }

    public Consumer setAutoheal(Boolean autoheal) {
        this.autoheal = autoheal;
        return this;
    }

    /**
     * @return the guestIds
     */
    public List<GuestId> getGuestIds() {
        return this.guestIds != null ? Collections.unmodifiableList(this.guestIds) : List.of();
    }

    /**
     * @param guests the GuestIds to set
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer setGuestIds(Collection<GuestId> guests) {
        if (guestIds == null) {
            guestIds = new ArrayList<>();
        }
        this.guestIds.clear();

        if (guests != null) {
            guests.stream()
                .filter(Objects::nonNull)
                .peek(gid -> gid.setConsumer(this))
                .forEach(this.guestIds::add);
        }

        this.updateRHCloudProfileModified();
        return this;
    }

    public boolean addGuestId(GuestId guestId) {
        if (guestIds == null) {
            guestIds = new ArrayList<>();
        }

        boolean result = false;

        if (guestId != null) {
            guestId.setConsumer(this);
            result = this.guestIds.add(guestId);
        }

        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    /**
     * Removes a guest ID from this consumer, if present. If this removal operation results in a
     * change to the guest IDs, the cloud profile last-modified time for this consumer will be
     * updated as well.
     *
     * @param guestId
     *  the guest ID to remove from this consumer
     *
     * @return
     *  true if the given guest ID was found and removed; false otherwise
     */
    public boolean removeGuestId(GuestId guestId) {
        boolean result = this.guestIds != null && this.guestIds.remove(guestId);
        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    public String getEntitlementStatus() {
        return entitlementStatus;
    }

    public Consumer setEntitlementStatus(String status) {
        this.entitlementStatus = status;
        return this;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public Consumer setServiceLevel(String level) {
        this.serviceLevel = level;
        this.updateRHCloudProfileModified();
        return this;
    }

    public String getRole() {
        return role;
    }

    public Consumer setRole(String role) {
        this.role = role;
        this.updateRHCloudProfileModified();
        return this;
    }

    public String getUsage() {
        return usage;
    }

    public Consumer setUsage(String usage) {
        this.usage = usage;
        this.updateRHCloudProfileModified();
        return this;
    }

    public String getSystemPurposeStatus() {
        return this.systemPurposeStatus;
    }

    public Consumer setSystemPurposeStatus(String systemPurposeStatus) {
        this.systemPurposeStatus = systemPurposeStatus;
        return this;
    }

    public String getSystemPurposeStatusHash() {
        return systemPurposeStatusHash;
    }

    public Consumer setSystemPurposeStatusHash(String systemPurposeStatusHash) {
        this.systemPurposeStatusHash = systemPurposeStatusHash;
        return this;
    }

    /**
     * @param releaseVer the releaseVer to set
     *
     * @return
     *  a reference to this
     */
    public Consumer setReleaseVer(Release releaseVer) {
        if (releaseVer == null) {
            releaseVer = new Release();
        }

        this.releaseVer = releaseVer.getReleaseVer();
        return this;
    }

    /**
     * @return the releaseVer
     */
    public Release getReleaseVer() {
        return new Release(releaseVer);
    }

    /**
     * @return the capabilities
     */
    public Set<ConsumerCapability> getCapabilities() {
        return this.capabilities != null ? Collections.unmodifiableSet(this.capabilities) : Set.of();
    }

    /**
     * @param capabilities the capabilities to set
     *
     * @return
     *  a reference to this Consumer instance
     */
    public Consumer setCapabilities(Collection<ConsumerCapability> capabilities) {
        if (this.capabilities == null) {
            this.capabilities = new HashSet<>();
        }
        this.capabilities.clear();

        if (capabilities != null) {
            capabilities.stream()
                .filter(Objects::nonNull)
                .peek(cc -> cc.setConsumer(this))
                .forEach(this.capabilities::add);
        }

        return this;
    }

    /**
     * @return the hypervisorId
     */
    public HypervisorId getHypervisorId() {
        return hypervisorId;
    }

    /**
     * @param hypervisorId the hypervisorId to set
     *
     * @return
     *  a reference to this consumer instance
     */
    public Consumer setHypervisorId(HypervisorId hypervisorId) {
        if (hypervisorId != null) {
            hypervisorId.setConsumer(this);
        }

        this.hypervisorId = hypervisorId;
        this.updateRHCloudProfileModified();

        return this;
    }

    public String getComplianceStatusHash() {
        return complianceStatusHash;
    }

    public Consumer setComplianceStatusHash(String complianceStatusHash) {
        this.complianceStatusHash = complianceStatusHash;
        return this;
    }

    public Set<String> getContentTags() {
        return this.contentTags != null ? Collections.unmodifiableSet(this.contentTags) : Set.of();
    }

    public Consumer setContentTags(Collection<String> contentTags) {
        if (this.contentTags == null) {
            this.contentTags = new HashSet<>();
        }
        this.contentTags.clear();

        if (contentTags != null) {
            this.contentTags.addAll(contentTags);
        }

        return this;
    }

    public String getAnnotations() {
        return this.annotations;
    }

    public Consumer setAnnotations(String annotations) {
        this.annotations = annotations;
        return this;
    }

    public boolean isGuest() {
        return "true".equalsIgnoreCase(this.getFact(Facts.VIRT_IS_GUEST));
    }

    public String getContentAccessMode() {
        return this.contentAccessMode;
    }

    public Consumer setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    public Set<String> getAddOns() {
        return this.addOns != null ? Collections.unmodifiableSet(this.addOns) : Set.of();
    }

    public Consumer setAddOns(Collection<String> addOns) {
        if (this.addOns == null) {
            this.addOns = new HashSet<>();
        }
        this.addOns.clear();

        if (addOns != null) {
            this.addOns.addAll(addOns);
        }

        this.updateRHCloudProfileModified();
        return this;
    }

    public boolean addAddOn(String addon) {
        if (this.addOns == null) {
            this.addOns = new HashSet<>();
        }

        boolean result = addon != null && this.addOns.add(addon);
        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    public boolean removeAddOn(String addon) {
        boolean result = this.addOns != null && this.addOns.remove(addon);
        if (result) {
            this.updateRHCloudProfileModified();
        }

        return result;
    }

    public Date getRHCloudProfileModified() {
        return this.rhCloudProfileModified;
    }

    public Consumer setRHCloudProfileModified(Date rhCloudProfileModified) {
        this.rhCloudProfileModified = rhCloudProfileModified;
        return this;
    }

    public void updateRHCloudProfileModified() {
        this.rhCloudProfileModified = new Date();
    }

    public Set<ConsumerActivationKey> getActivationKeys() {
        return this.activationKeys != null ? Collections.unmodifiableSet(this.activationKeys) : Set.of();
    }

    public Consumer setActivationKeys(Collection<ConsumerActivationKey> activationKeys) {
        if (this.activationKeys == null) {
            this.activationKeys = new HashSet<>();
        }
        this.activationKeys.clear();

        if (activationKeys != null) {
            activationKeys.stream()
                .filter(Objects::nonNull)
                .peek(cak -> cak.setConsumer(this))
                .forEach(this.activationKeys::add);
        }

        return this;
    }

    public String getServiceType() {
        return serviceType;
    }

    public Consumer setServiceType(String serviceType) {
        this.serviceType = serviceType;
        return this;
    }

    public List<String> getEnvironmentIds() {
        if (this.environmentIds == null) {
            return List.of();
        }

        return this.environmentIds.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey())))
            .map(Map.Entry::getValue)
            .collect(Collectors.toUnmodifiableList());
    }

    public Consumer setEnvironmentIds(Collection<String> environmentIds) {
        // Impl note:
        // We always create a new map instance instead of clearing the existing one in this case, as
        // it changes the order in which Hibernate will delete the existing entries in the DB. For
        // some reason, using Map.clear in this case doesn't trigger a full removal of the existing
        // rows before adding the new entries on persist, leading to a constraint violation (on
        // cp_consumer_environments_pkey) whenever environments are partially updated or only
        // priorities are changed.
        // Updating existing entries is also not an option here, since Hibernate will only update
        // the value for a given key, rather than updating both the key and value as a logical pair.
        // This may break if Hibernate's implementation changes in the future, or our schema
        // changes. Revisit as necessary.
        this.environmentIds = new LinkedHashMap<>();

        if (environmentIds != null) {
            HashSet<String> deDuplicatedIds = new HashSet<>(environmentIds);
            if (environmentIds.size() != deDuplicatedIds.size()) {
                throw new DuplicateEntryException("One or more environments were specified more than once.");
            }

            for (String envId : environmentIds) {
                this.environmentIds.put(String.valueOf(this.environmentIds.size()), envId);
            }
        }
        return this;
    }

    public Consumer setRegistrationAuthenticationMethod(String method) {
        this.registrationAuthenticationMethod = AuthenticationMethod.get(method);
        return this;
    }

    public String getRegistrationAuthenticationMethod() {
        if (this.registrationAuthenticationMethod == null) {
            return null;
        }
        return this.registrationAuthenticationMethod.getDescription();
    }

    public Consumer setConsumerCloudData(ConsumerCloudData consumerCloudData) {
        this.consumerCloudData = consumerCloudData;
        if (this.consumerCloudData != null) {
            this.consumerCloudData.setConsumer(this);
        }

        return this;
    }

    public ConsumerCloudData getConsumerCloudData() {
        return this.consumerCloudData;
    }

    public boolean checkForCloudIdentifierFacts(Map<String, String> incomingFacts) {
        if (incomingFacts == null) {
            return false;
        }

        Map<String, String> existingFacts = this.getFacts();
        if (existingFacts != null && existingFacts.equals(incomingFacts)) {
            return false;
        }

        return Arrays.stream(CloudIdentifierFacts.values())
            .filter(profileFact -> incomingFacts.containsKey(profileFact.getValue()))
            .anyMatch(profileFact -> {
                String key = profileFact.getValue();
                String incomingValue = incomingFacts.get(key);
                String existingValue = existingFacts == null ? null : existingFacts.get(key);
                return !Objects.equals(incomingValue, existingValue);
            });
    }

    /**
     * It adds the consumer's environment.
     * It basically adds one after another with
     * the lowering priority.
     *
     * @param environment incoming environment
     * @return a consumer
     */
    public Consumer addEnvironment(Environment environment) {
        if (environment != null) {
            if (this.environmentIds == null) {
                this.environmentIds = new LinkedHashMap<>();
            }

            if (this.environmentIds.containsValue(environment.getId())) {
                throw new DuplicateEntryException("Environment " + environment.getId() +
                    " specified more than once.");
            }

            this.environmentIds.put(String.valueOf(this.environmentIds.size()), environment.getId());
        }

        return this;
    }

    @Override
    public String toString() {
        return String.format("Consumer [id: %s, uuid: %s, name: %s]",
            this.getId(), this.getUuid(), this.getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Consumer)) {
            return false;
        }

        return Objects.equals(this.getUuid(), ((Consumer) obj).getUuid());
    }

    @Override
    public int hashCode() {
        String uuid = this.getUuid();
        return uuid != null ? uuid.hashCode() : 0;
    }

}
