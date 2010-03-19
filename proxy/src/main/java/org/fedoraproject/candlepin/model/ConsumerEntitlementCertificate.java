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
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;

/**
 * Represents certificate used to entitle a consumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_consumer_ent_certificate")
@SequenceGenerator(name = "seq_consumer_ent_cert", sequenceName = "seq_consumer_ent_cert", allocationSize = 1)
public class ConsumerEntitlementCertificate implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_consumer_ent_cert")
    private Long id;

    @Column(nullable = false)
    private byte[] key;

    @Column(nullable = false)
    private String pem;

    @Column(nullable = false)
    private BigInteger serialNumber;

    @SuppressWarnings("unused")
    @ManyToOne
    @ForeignKey(name = "fk_cert_entitlement")
    @JoinColumn(nullable = false)
    private Entitlement entitlement;

    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(BigInteger serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getKey() {
        return new String(key);
    }

    public void setKey(String key) {
        this.key = key.getBytes();
    }

    public String getPem() {
        return pem;
    }

    public void setPem(String pem) {
        this.pem = pem;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void update(ConsumerIdentityCertificate other) {
        this.setKey(other.getKey());
        this.setPem(other.getPem());
        this.setSerialNumber(other.getSerialNumber());
    }

}
