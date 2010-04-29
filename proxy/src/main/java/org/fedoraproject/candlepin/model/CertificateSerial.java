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

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * CertificateSerial: A simple database sequence used to ensure certificates receive
 * unique serial numbers.
 * 
 * Also used for serialization over the REST API.
 *
 * WARNING: A little clunky here, the id field is used for the sequence in the db,
 * the serial field is used for REST serialization... Probably should be made into
 * two objects.
 */
@XmlRootElement(name = "serials")
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_serial_generator")
@SequenceGenerator(name = "seq_certificate_serial", sequenceName = "seq_certificate_serial",
        allocationSize = 1)
public class CertificateSerial implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator =
        "seq_certificate_serial")
    private Long id;

    private BigInteger serial;
    
    public CertificateSerial() {
    }

    public CertificateSerial(Long id) {
        this.id = id;
    }

    public CertificateSerial(BigInteger serial) {
        this.serial = serial;
    }

    public BigInteger getSerial() {
        return serial;
    }

    @XmlTransient
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSerial(BigInteger serial) {
        this.serial = serial;
    }

}
