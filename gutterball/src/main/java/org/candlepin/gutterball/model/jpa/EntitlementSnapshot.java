package org.candlepin.gutterball.model.jpa;

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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "gb_entitlement_snapshot")
public class EntitlementSnapshot {

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

    private int quantity;

    public EntitlementSnapshot() {

    }

    public EntitlementSnapshot(int quantity) {
        this.quantity = quantity;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

}
