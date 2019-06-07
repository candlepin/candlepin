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

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents certificate used to entitle a consumer
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = EntitlementCertificate.DB_TABLE)
@JsonFilter("EntitlementCertificateFilter")
public class EntitlementCertificate extends RevocableCertificate<EntitlementCertificate> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_ent_certificate";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Entitlement entitlement;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public Entitlement getEntitlement() {
        return entitlement;
    }

    public void setEntitlement(Entitlement entitlement) {
        this.entitlement = entitlement;
    }

    @Override
    public int hashCode() {
        return this.id == null ? 0 : id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || (getClass() != obj.getClass())) {
            return false;
        }
        EntitlementCertificate other = (EntitlementCertificate) obj;
        if (id == other.id &&
            other.getEntitlement().getId().equals(this.getEntitlement().getId())) {

            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        Entitlement entitlement = this.getEntitlement();
        String entitlementId = entitlement != null ? entitlement.getId() : null;

        CertificateSerial serial = this.getSerial();
        Long serialId = serial != null ? serial.getId() : null;

        return String.format("EntitlementCertificate [id: %s, entitlement: %s, serial: %s]",
            this.getId(), entitlementId, serialId);
    }
}
