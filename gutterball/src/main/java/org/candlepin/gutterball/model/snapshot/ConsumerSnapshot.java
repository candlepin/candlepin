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

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
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
@Table(name = "gb_consumer_snapshot")
public class ConsumerSnapshot {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    // Ignore the id when building from JSON so that the CP id
    // is not set from the CP record.
    @JsonIgnore
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String uuid;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @ForeignKey(name = "fk_compliance_snapshot")
    @JoinColumn(nullable = false)
    @Index(name = "cp_compliance_snapshot_fk_idx")
    @NotNull
    private ComplianceSnapshot complianceSnapshot;

    @OneToOne(mappedBy = "consumerSnapshot", targetEntity = OwnerSnapshot.class)
    @Cascade({org.hibernate.annotations.CascadeType.ALL,
        org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @NotNull
    private OwnerSnapshot owner;

    public ConsumerSnapshot() {
    }

    public ConsumerSnapshot(String uuid, OwnerSnapshot ownerSnapshot) {
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
    public ComplianceSnapshot getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(ComplianceSnapshot complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public OwnerSnapshot getOwner() {
        return owner;
    }

    public void setOwner(OwnerSnapshot ownerSnapshot) {
        this.owner = ownerSnapshot;
        this.owner.setConsumerSnapshot(this);
    }

}
