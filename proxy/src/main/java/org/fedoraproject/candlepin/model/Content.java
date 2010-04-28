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
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_content")
@SequenceGenerator(name = "seq_content", sequenceName = "seq_content", allocationSize = 1)
public class Content implements Persisted {

    // Product ID is stored as a string. Could be a product OID or label.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_content")
    private String id;
    
    @Column(nullable = false)
    private String type;
    
    @Column(nullable = false)
    private String label;
    
    // Description?
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private Long hash;
    
    @Column(nullable = false)
    private String vendor;
    
    @Column(nullable = true)
    private String contentUrl;
    
    
    // attribute?
    @Column(nullable = true)
    private String gpgUrl;
    
    
    // probably a join here, seems external to content set defination
    @Column(nullable = true)
    private String enabled;

 //   @ManyToOne
//    @ForeignKey(name = "fk_product_content")
 //   @JoinColumn
 //   private Content Content;
    
    
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
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
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
