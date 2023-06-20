/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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



/**
 * Represents the "upstream" entitlement certificate that sources a downstream on-site
 * subscription. Can be used to fetch content from the upstream CDN.
 */
@Entity
@Table(name = SubscriptionsCertificate.DB_TABLE)
public class SubscriptionsCertificate extends AbstractCertificate<SubscriptionsCertificate>
    implements Certificate<SubscriptionsCertificate> {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_certificate";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "serial_id")
    private CertificateSerial serial;

    public CertificateSerial getSerial() {
        return serial;
    }

    public void setSerial(CertificateSerial serialNumber) {
        this.serial = serialNumber;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    public String toString() {
        String serial = null;

        if (this.serial != null) {
            serial = String.format("Serial [id=%s]", this.serial.getId());
        }

        return String.format("Cert [id=%s, serial=%s]", this.id, serial);
    }
}
