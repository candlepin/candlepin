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

package org.candlepin.gutterball.model.snapshot;

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.gutterball.jackson.EnvironmentNameConverter;
import org.candlepin.gutterball.jackson.HypervisorIdToStringConverter;
import org.candlepin.gutterball.jackson.ReleaseVersionToStringConverter;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * A model object representing a snapshot of a consumer at a given point in time.
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_consumer_snap")
@JsonFilter("GBConsumerFilter")
@HateoasInclude
public class Consumer {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    // Ignore the id when building from JSON so that the CP id
    // is not set from the CP record.
    @JsonIgnore
    private String id;

    @Size(max = 255)
    private String uuid;

    @Size(max = 255)
    private String name;

    // Represents the username used to register this consumer
    @Column
    @Size(max = 255)
    private String username;

    @Column(length = 32)
    @Size(max = 32)
    private String entitlementStatus;

    @Column(length = 255, nullable = true)
    @Size(max = 255)
    private String serviceLevel;

    // for selecting Y/Z stream
    @Column(length = 255, nullable =  true)
    @Size(max = 255)
    @JsonDeserialize(converter = ReleaseVersionToStringConverter.class)
    private String releaseVer;

    @OneToOne(cascade = CascadeType.ALL)
    private ConsumerType type;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_snap_id", nullable = false)
    @NotNull
    private Compliance complianceSnapshot;

    @OneToOne(mappedBy = "consumerSnapshot", targetEntity = Owner.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private Owner owner;

    private Long entitlementCount;

    private Date lastCheckin;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @CollectionTable(name = "gb_consumer_facts_snap",
                     joinColumns = @JoinColumn(name = "consumer_snap_id"))
    @MapKeyColumn(name = "mapkey")
    @Column(name = "element")
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    private Map<String, String> facts;

    @OneToMany(mappedBy = "consumer", targetEntity = ConsumerInstalledProduct.class, fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private Set<ConsumerInstalledProduct> installedProducts;

    @OneToMany(mappedBy = "consumer", targetEntity = GuestId.class, fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    private List<GuestId> guestIds;

    @Size(max = 255)
    @JsonProperty("environment")
    @JsonDeserialize(converter = EnvironmentNameConverter.class)
    private String environmentName;

    @Size(max = 255)
    @JsonDeserialize(converter = HypervisorIdToStringConverter.class)
    private String hypervisorId;

    public Consumer() {
        this.facts = new HashMap<String, String>();
        this.installedProducts = new HashSet<ConsumerInstalledProduct>();
        this.guestIds = new LinkedList<GuestId>();
    }

    public Consumer(String uuid, Owner ownerSnapshot) {
        this.uuid = uuid;
        setOwner(ownerSnapshot);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @XmlTransient
    public Compliance getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(Compliance complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner ownerSnapshot) {
        this.owner = ownerSnapshot;
        this.owner.setConsumerSnapshot(this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEntitlementStatus() {
        return entitlementStatus;
    }

    public void setEntitlementStatus(String entitlementStatus) {
        this.entitlementStatus = entitlementStatus;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public String getReleaseVer() {
        return releaseVer;
    }

    public void setReleaseVer(String releaseVer) {
        this.releaseVer = releaseVer;
    }

    public ConsumerType getType() {
        return type;
    }

    public void setType(ConsumerType type) {
        this.type = type;
    }

    public Long getEntitlementCount() {
        return entitlementCount;
    }

    public void setEntitlementCount(Long entitlementCount) {
        this.entitlementCount = entitlementCount;
    }

    public Date getLastCheckin() {
        return lastCheckin;
    }

    public void setLastCheckin(Date lastCheckin) {
        this.lastCheckin = lastCheckin;
    }

    public Map<String, String> getFacts() {
        return facts;
    }

    public void setFacts(Map<String, String> facts) {
        this.facts = facts;
    }

    public Set<ConsumerInstalledProduct> getInstalledProducts() {
        return installedProducts;
    }

    public void setInstalledProducts(Set<ConsumerInstalledProduct> installed) {
        if (installed == null) {
            installed = new HashSet<ConsumerInstalledProduct>();
        }

        this.installedProducts = installed;
        for (ConsumerInstalledProduct p : this.installedProducts) {
            p.setConsumer(this);
        }
    }

    public List<GuestId> getGuestIds() {
        return guestIds;
    }

    public void setGuestIds(List<GuestId> ids) {
        if (ids == null) {
            ids = new LinkedList<GuestId>();
        }

        this.guestIds = ids;
        for (GuestId gid : this.guestIds) {
            gid.setConsumer(this);
        }
    }

    public String getEnvironment() {
        return environmentName;
    }

    public void setEnvironment(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getHypervisorId() {
        return hypervisorId;
    }

    public void setHypervisorId(String hypervisorId) {
        this.hypervisorId = hypervisorId;
    }

}
