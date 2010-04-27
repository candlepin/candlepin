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
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * CertificateSerial: A simple database sequence used to ensure certificates receive
 * unique serial numbers.
 * 
 * Also used for serialization over the REST API.
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
    private Long serial;
    
    public CertificateSerial() {
    }

    public CertificateSerial(Long serial) {
        this.serial = serial;
    }

    public Long getSerial() {
        return serial;
    }

    // Keep the Persisted interface happy:
    public Long getId() {
        return serial;
    }

    public void setSerial(Long id) {
        this.serial = id;
    }

}
