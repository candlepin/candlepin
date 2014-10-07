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

import org.hibernate.annotations.ForeignKey;
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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Model object that represents a consumer's status at a given point in time.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_compliance_status_snap")
public class ComplianceStatus {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    @JsonIgnore
    private String id;

    @XmlTransient
    @OneToOne(fetch = FetchType.LAZY)
    @ForeignKey(name = "fk_status_compliance")
    @JoinColumn(nullable = false)
    @Index(name = "ix_compliance_snap_fk")
    @NotNull
    private Compliance complianceSnapshot;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String status;

    public ComplianceStatus() {
        // Required by hibernate.
    }

    public ComplianceStatus(Date date, String status) {
        this.date = date;
        this.status = status;
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

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
