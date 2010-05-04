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

import java.math.BigInteger;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ForeignKey;
import org.hibernate.annotations.ParamDef;

/**
 * Represents certificate used to entitle a consumer
 */
@XmlRootElement(name = "cert")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_ent_certificate")
@SequenceGenerator(name = "seq_ent_cert", 
                   sequenceName = "seq_ent_cert", allocationSize = 1)
@FilterDef(
    name = "EntitlementCertificate_CONSUMER_FILTER", 
    parameters = @ParamDef(name = "consumer_id", type = "long")
)
@Filter(name = "EntitlementCertificate_CONSUMER_FILTER", 
    condition = "id in (select c.id from cp_ent_certificate c " +
        "inner join cp_entitlement e on c.entitlement_id = e.id " +
        "inner join cp_consumer_entitlements con_en on e.id = con_en.entitlement_id " + 
            "and con_en.consumer_id = :consumer_id)")

public class EntitlementCertificate implements Persisted {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, 
                    generator = "seq_ent_cert")
    private Long id;

    @Column(nullable = false)
    private byte[] key;

    @Column(nullable = false)
    private byte[] cert;

    @Column(nullable = false)
    private BigInteger serial;

    @ManyToOne
    @ForeignKey(name = "fk_cert_entitlement")
    @JoinColumn(nullable = false)
    private Entitlement entitlement;

    public BigInteger getSerial() {
        return serial;
    }

    public void setSerial(BigInteger serialNumber) {
        this.serial = serialNumber;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    @XmlTransient
    public byte[] getKey() {
        return key;
    }
    
    @XmlElement(name = "key")
    public String getKeyAsString() {
        return new String(key);
    }
    
    public void setKeyAsString(String key) {
        this.key = key.getBytes();        
    }    

    public void setCert(byte[] cert) {
        this.cert = cert;
    }

    @XmlTransient
    public byte[] getCert() {
        return cert;
    }
    
    @XmlElement(name = "cert")
    public String getCertAsString() {
        return new String(cert);
    }
    
    public void setCertAsString(String cert) {
        this.cert = cert.getBytes();        
    }

    @XmlTransient
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Entitlement getEntitlement() {
        return entitlement;
    }

    public void setEntitlement(Entitlement entitlement) {
        this.entitlement = entitlement;
    }
}
