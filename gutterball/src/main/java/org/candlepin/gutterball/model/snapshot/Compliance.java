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

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A model object representing a snapshot of a Consumer's compliance state at a given
 * point in time.
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_compliance_snap")
@JsonFilter("GBComplianceFilter")
@HateoasInclude
public class Compliance {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @OneToOne(mappedBy = "complianceSnapshot", targetEntity = Consumer.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private Consumer consumer;

    @OneToOne(mappedBy = "complianceSnapshot", targetEntity = ComplianceStatus.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private ComplianceStatus status;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "complianceSnapshot", fetch = FetchType.LAZY)
    @BatchSize(size = 25)
    private Set<Entitlement> entitlements;

    public Compliance() {
        this.entitlements = new HashSet<Entitlement>();
    }

    public Compliance(Date date, Consumer consumerSnapshot,
            ComplianceStatus statusSnapshot) {
        this();
        this.date = date;
        setConsumer(consumerSnapshot);
        setStatus(statusSnapshot);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumerSnapshot) {
        this.consumer = consumerSnapshot;
        this.consumer.setComplianceSnapshot(this);
    }

    public ComplianceStatus getStatus() {
        return status;
    }

    public void setStatus(
            ComplianceStatus complianceStatusSnapshot) {
        this.status = complianceStatusSnapshot;
        this.status.setComplianceSnapshot(this);
    }

    public Set<Entitlement> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(
            Set<Entitlement> entitlementSnapshots) {
        this.entitlements = entitlementSnapshots;

        for (Entitlement snapshot : this.entitlements) {
            snapshot.setComplianceSnapshot(this);
        }
    }

    public void addEntitlementSnapshot(Entitlement snapshot) {
        snapshot.setComplianceSnapshot(this);
        this.entitlements.add(snapshot);
    }

}
