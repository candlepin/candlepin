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


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Lob;
import javax.persistence.Id;
import javax.persistence.Column;

import org.hibernate.annotations.ForeignKey;


@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_certificate")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
   
    @Lob
    @Column(name = "certificate_blob")
    private String certificate;
    
    
    @ManyToOne
    @ForeignKey(name = "fk_certificate_owner")
    @JoinColumn(nullable = false)
    private Owner owner;
    
    
    public Certificate(String certificateIn, Owner ownerIn) {
        certificate = certificateIn;
        owner = ownerIn;   
    }
    
    public Certificate() {
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
