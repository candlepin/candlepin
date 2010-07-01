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
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.fedoraproject.candlepin.util.Util;

/**
 * CertificateSerial: A simple database sequence used to ensure certificates receive
 * unique serial numbers.
 */
@Entity
@Table(name = "cp_cert_serial")
@SequenceGenerator(name = "seq_certificate_serial", sequenceName = "seq_certificate_serial",
        allocationSize = 1)
public class CertificateSerial extends AbstractHibernateObject{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator =
        "seq_certificate_serial")
    private Long id;
    
    /**Flag which indicates whether the certificate is revoked */
    private boolean revoked;
    
    /** Set to true if this serial is already a part of the crl*/
    private boolean collected;
    
    /** The expiration. */
    private Date expiration;

    /**
     * Default constructor for serialization - DO NOT REMOVE!
     */
    public CertificateSerial() { }
    
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
     * @param revoked the revoked to set
     */
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
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
        return "CertificateSerial[id=" + id + "]";
    }
    
    public BigInteger getSerial() {
        return Util.toBigInt(this.getId());
    }

}
