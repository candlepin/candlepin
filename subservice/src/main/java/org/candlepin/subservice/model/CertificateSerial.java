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

import org.candlepin.model.AbstractHibernateObject;

import org.hibernate.annotations.Formula;
import org.hibernate.annotations.GenericGenerator;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



/**
 * CertificateSerial: A simple database sequence used to ensure certificates receive
 * unique serial numbers.
 */
@Entity
@Table(name = "cps_certificate_serials")
public class CertificateSerial extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "serial-number")
    @GenericGenerator(name = "serial-number",
        strategy = "org.candlepin.subservice.util.SerialNumberGenerator")
    @NotNull
    protected Long id;

    /*
     * A CertificateSerial is considered revoked when no certificates reference it
     * TODO: put different kinds of serials into different tables.  More specifically,
     * those that we own (can revoke) vs those that we don't
     */
    @Formula("(CASE (" +
        "(SELECT count(cdncert.id) FROM cps_cdn_certificates cdncert where cdncert.serial_id = id) + " +
        "(SELECT count(subcert.id) FROM cps_subscription_certificates subcert where subcert.serial_id = id) + " +
        ") WHEN 0 THEN 1 ELSE 0 END)")
    protected boolean revoked;

    // Set to true if this serial is already a part of the CRL
    @NotNull
    protected boolean collected;

    // The expiration.
    protected Date expiration;

    public CertificateSerial() {

    }

    // TODO:
    // Add convenience constructors



    public Long getId() {
        return this.id;
    }

    public CertificateSerial setId(Long id) {
        this.id = id;
        return this;
    }

    public boolean getRevoked() {
        return this.revoked;
    }

    public boolean getCollected() {
        return this.collected;
    }

    public CertificateSerial setCollected(boolean collected) {
        this.collected = collected;
        return this;
    }

    public Date getExpiration() {
        return this.expiration;
    }

    public CertificateSerial setExpiration(Date expiration) {
        this.expiration = expiration;
        return this;
    }

}
