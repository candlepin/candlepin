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

import org.candlepin.service.model.CertificateSerialInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
public class CertificateSerial extends AbstractHibernateObject<CertificateSerial>
    implements CertificateSerialInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_cert_serial";

    @Id
    @NotNull
    @GeneratedValue(generator = "CertificateSerialIdGenerator")
    @GenericGenerator(name = "CertificateSerialIdGenerator",
        strategy = "org.candlepin.model.CertificateSerialIdGenerator")
    private Long id;

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
     * {@inheritDoc}
     */
    @Override
    public Boolean isRevoked() {
        return revoked;
    }

    /**
     * @param isRevoked whether or not this serial is revoked.
     */
    public void setRevoked(Boolean isRevoked) {
        this.revoked = isRevoked != null ? isRevoked : false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean isCollected() {
        return collected;
    }

    /**
     * @param collected the collected to set
     */
    public void setCollected(Boolean collected) {
        this.collected = collected != null ? collected : false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
        Date expiration = this.getExpiration();
        String date = expiration != null ? String.format("%1$tF %1$tT%1$tz", expiration) : null;

        return String.format(
            "CertificateSerial [id: %s, serial: %s, expiration: %s, collected: %b, revoked: %b]",
            this.getId(), this.getSerial(), date, this.isCollected(), this.isRevoked());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BigInteger getSerial() {
        return this.getId() != null ? BigInteger.valueOf(this.getId()) : null;
    }

    @JsonProperty
    public void setSerial(Long serial) {
        this.id = serial;
    }

    @JsonIgnore
    public void setSerial(BigInteger serial) {
        this.setId(serial != null ? serial.longValueExact() : null);
    }

}
