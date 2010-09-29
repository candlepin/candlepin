/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.fedoraproject.candlepin.auth.interceptor.AccessControlValidator;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.Filters;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.ParamDef;

/**
 * Represents certificate used to entitle a consumer
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_ent_certificate")

@FilterDefs({
    @FilterDef(
        name = "EntitlementCertificate_CONSUMER_FILTER", 
        parameters = @ParamDef(name = "consumer_id", type = "string")
    ),
    @FilterDef(
        name = "EntitlementCertificate_OWNER_FILTER", 
        parameters = @ParamDef(name = "owner_id", type = "string")
    )
})
@Filters({
    @Filter(name = "EntitlementCertificate_CONSUMER_FILTER", 
        condition = "id in (select c.id from cp_ent_certificate c " +
            "inner join cp_entitlement e on c.entitlement_id = e.id " +
            "inner join cp_consumer_entitlements con_en on e.id = con_en.entitlement_id " + 
                "and con_en.consumer_id = :consumer_id)"),
    @Filter(name = "Consumer_CONSUMER_FILTER", 
        condition = "id in (select c.id from cp_ent_certificate c " +
            "inner join cp_entitlement e on c.entitlement_id = e.id " +
                "and c.owner_id = :owner_id)")
})
public class EntitlementCertificate extends AbstractCertificate
    implements AccessControlEnforced {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    private String id;

    @OneToOne
    private CertificateSerial serial;

    @ManyToOne
    @ForeignKey(name = "fk_cert_entitlement")
    @JoinColumn(nullable = false)
    private Entitlement entitlement;

    public CertificateSerial getSerial() {
        return serial;
    }

    public void setSerial(CertificateSerial serialNumber) {
        this.serial = serialNumber;
    }

    @XmlTransient
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
    public boolean shouldGrantAccessTo(Owner owner) {
        return AccessControlValidator.shouldGrantAccess(this, owner);
    }

    @Override
    public boolean shouldGrantAccessTo(Consumer consumer) {
        return AccessControlValidator.shouldGrantAccess(this, consumer);
    }

    @Override
    public int hashCode() {
        return this.id == null ? super.hashCode() : 31 * id.hashCode();
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
        if (id == other.id) {
            if (other.getEntitlement().getId() == this.getEntitlement().getId()) {
                return true;
            }
        }
        return false;
    }

}
