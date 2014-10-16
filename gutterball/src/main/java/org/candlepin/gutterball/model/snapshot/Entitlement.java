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

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import java.util.Date;

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
 * A model representing a snapshot of an Entitlement at a given point in time.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_entitlement_snap")
public class Entitlement {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compliance_snap_id", nullable = false)
    @NotNull
    private Compliance complianceSnapshot;

    private int quantity;

//    @OneToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "source_ent_id", nullable = true)
//    private Entitlement sourceEntitlement;

    @Column(nullable = false)
    @NotNull
    private Date startDate;

    @Column(nullable = false)
    @NotNull
    private Date endDate;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String productId;

    @Column
    @Size(max = 255)
    private String derivedProductId;

    public Entitlement() {

    }

    public Entitlement(int quantity, Date startDate, Date endDate) {
        this.quantity = quantity;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Compliance getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(Compliance complianceSnapshot) {
        this.complianceSnapshot = complianceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

//    public Entitlement getSourceEntitlement() {
//        return sourceEntitlement;
//    }
//
//    public void setSourceEntitlement(Entitlement sourceEntitlement) {
//        this.sourceEntitlement = sourceEntitlement;
//    }



}
