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

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlRootElement;

import org.hibernate.annotations.ForeignKey;

/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_content")
public class Content implements Persisted {

    
    /*  Example oid of this model
    1.3.6.1.4.1.2312.9.2.<content_hash>.2 (File repo type))
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.1 (Name) : Red Hat Enterprise Linux (core server)
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.2 (Label) : rhel-server
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.3 (Physical Entitlements): 1
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.4 (Flex Guest Entitlements): 0
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.5 (Vendor ID): %Red_Hat_Id% or %Red_Hat_Label%
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.6 (Download URL): content/rhel-server-isos/$releasever/$basearch
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.7 (GPG Key URL): gpg/rhel-server-isos/$releasever/$basearch
    1.3.6.1.4.1.2312.9.2.<content_hash>.2.8 (Enabled): 1
    */
    // Product ID is stored as a string. Could be a product OID or label.
    @Id
    private String id;
    
    @Column(nullable = false)
    private String label;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private Long hash;
    
    @Column(nullable = false)
    private String vendor;
    
    @Column(nullable = true)
    private String contentUrl;
    
    @Column(nullable = true)
    private String gpgUrl;
    
    @Column(nullable = true)
    private String enabled;

    @ManyToOne
//    @ForeignKey(name = "fk_product_content")
    @JoinColumn
    private Content Content;
    
    
    public Content(String name, String vendor, String contentUrl,
        String gpgUrl, String enabled) {
        setName(name);
        setVendor(vendor);
        setContentUrl(contentUrl);
        setGpgUrl(gpgUrl);
        setEnabled(enabled);
    }
    
    public Content() {
    }
    
    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.model.Persisted#getId()
     */
    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return id;
    }
    
    /**
     * @param id product id
     */
    public void setId(String id) {
        this.id = id;
    }
    
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getVendor() {
        return vendor;
    }
    public void setVendor(String vendor) {
        this.vendor = vendor;
    }
    public String getContentUrl() {
        return contentUrl;
    }
    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }
    public String getGpgUrl() {
        return gpgUrl;
    }
    public void setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
    }
    public String getEnabled() {
        return enabled;
    }
    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    /**
     * @param hash the hash to set
     */
    public void setHash(Long hash) {
        this.hash = hash;
    }

    /**
     * @return the hash
     */
    public Long getHash() {
        return hash;
    }

}
