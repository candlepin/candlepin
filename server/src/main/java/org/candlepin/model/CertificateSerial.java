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

import org.candlepin.util.Util;

import org.hibernate.annotations.GenericGenerator;

import java.math.BigInteger;
import java.util.Date;

import javax.persistence.Column;
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
@Table(name = CertificateSerial.DB_TABLE)
public class CertificateSerial extends AbstractHibernateObject {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_cert_serial";

    @Id
    @GeneratedValue(generator = "serial-number")
    @GenericGenerator(name = "serial-number",
        strategy = "org.candlepin.util.SerialNumberGenerator")
    @NotNull
    private Long id;

    /*
     * A CertificateSerial is considered revoked when no certificates reference it
     * TODO: put different kinds of serials into different tables.  More specifically,
     * those that we own (can revoke) vs those that we don't.
     */
    @NotNull
    @Column(nullable = false)
    private boolean revoked;

    // Set to true if this serial is already a part of the CRL
    @NotNull
    @Column(nullable = false)
    private boolean collected;

    // The expiration.
    private Date expiration;

    /**
     * Default constructor for serialization - DO NOT REMOVE!
     */
    public CertificateSerial() {
        super();
    }

    public CertificateSerial(Date expiration) {
        this.expiration = expiration;
    }

    public CertificateSerial(Long id) {
        this.id = id;
    }

    public CertificateSerial(Long id, Date expiration) {
        this(id);
        this.expiration = expiration;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the revoked
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * @param isRevoked whether or not this serial is revoked.
     */
    public void setRevoked(boolean isRevoked) {
        this.revoked = isRevoked;
    }

    /**
     * @return the collected
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * @param collected the collected to set
     */
    public void setCollected(boolean collected) {
        this.collected = collected;
    }

    /**
     * @return the expiration
     */
    public Date getExpiration() {
        return expiration;
    }

    /**
     * @param expiration the expiration to set
     */
    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public String toString() {
        return "CertificateSerial[id=" + id + ", revoked=" + revoked +
            " ,collected=" + collected + ", expDt=" + expiration + "] ";
    }

    public BigInteger getSerial() {
        return Util.toBigInt(this.getId());
    }

    public void setSerial(Long serial) {
        this.id = serial;
    }

}
