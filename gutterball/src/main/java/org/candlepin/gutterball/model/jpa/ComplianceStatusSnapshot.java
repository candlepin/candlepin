package org.candlepin.gutterball.model.jpa;

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

@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_compliance_status_snapshot")
public class ComplianceStatusSnapshot {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @ForeignKey(name = "fk_compliance_snapshot")
    @JoinColumn(nullable = false)
    @Index(name = "cp_compliance_snapshot_fk_idx")
    @NotNull
    private ComplianceSnapshot complianceSnapshot;

    @XmlElement
    @Column(nullable = false, unique = false)
    private Date date;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String status;

    public ComplianceStatusSnapshot() {
        // Required by hibernate.
    }

    public ComplianceStatusSnapshot(Date date, String status) {
        this.date = date;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ComplianceSnapshot getComplianceSnapshot() {
        return complianceSnapshot;
    }

    public void setComplianceSnapshot(ComplianceSnapshot complianceSnapshot) {
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
