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


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.hibernate.annotations.ForeignKey;


/**
 * Certificate
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_certificate")
@SequenceGenerator(name = "seq_certificate", sequenceName = "seq_certificate",
        allocationSize = 1)
public class SubscriptionsCertificate implements Persisted {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_certificate")
    private Long id;
    
   
    @Lob
    @Column(name = "certificate_blob", unique = true)
    private String certificate;
    
    
    @ManyToOne
    @ForeignKey(name = "fk_certificate_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    
    /**
     * represents a certificate.
     * @param certificateIn certificate as a string
     * @param ownerIn owner of the certificate
     */
    public SubscriptionsCertificate(String certificateIn, Owner ownerIn) {
        certificate = certificateIn;
        owner = ownerIn;   
    }
    
    /**
     * default ctor
     */
    public SubscriptionsCertificate() {
    }
    
    /**
     * @return certficate blob
     */
    public String getCertificate() {
        return certificate;
    }
    
    /**
     * 
     * @param certificate the certificate's content.
     */
    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }
    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * @return the owner
     */
    @XmlTransient
    public Owner getOwner() {
        return owner;
    }

    /**
     * @param ownerIn the owner to set
     */
    public void setOwner(Owner ownerIn) {
        this.owner = ownerIn;
    }
}
