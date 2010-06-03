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
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents certificate used to identify a consumer
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_id_cert")
@SequenceGenerator(name = "seq_id_cert", sequenceName = "seq_id_cert", allocationSize = 1)
public class IdentityCertificate extends AbstractCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_id_cert")
    private Long id;

    @Column(nullable = false)
    private BigInteger serial;

    @OneToOne(mappedBy = "idCert")
    private Consumer consumer;

    public BigInteger getSerial() {
        return serial;
    }

    public void setSerial(BigInteger serialNumber) {
        this.serial = serialNumber;
    }

    @XmlTransient
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void update(IdentityCertificate other) {
        this.setKey(other.getKey());
        this.setCert(other.getCert());
        this.setSerial(other.getSerial());
    }

    @XmlTransient
    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

}
