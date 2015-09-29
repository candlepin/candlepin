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

package org.candlepin.subservice.model;

import org.candlepin.model.AbstractCertificate;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * Represents the "upstream" entitlement certificate that sources a downstream on-site
 * subscription. Can be used to fetch content from the upstream CDN.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cps_cdn_certificates")
public class CdnCertificate extends AbstractCertificate {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    protected String id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "serial_id")
    protected CertificateSerial serial;

    public CdnCertificate() {

    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public CdnCertificate setId(String id) {
        this.id = id;
        return this;
    }

    public CertificateSerial getSerial() {
        return this.serial;
    }

    public CdnCertificate setSerial(CertificateSerial serial) {
        this.serial = serial;
        return this;
    }

}
