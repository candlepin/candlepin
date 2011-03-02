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

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinTable;
import javax.persistence.Table;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.CollectionOfElements;

/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_content")
public class Content extends AbstractHibernateObject {

    @Id
    private String id;
    
    @Column(nullable = false)
    private String type;
    
    @Column(nullable = false, unique = true)
    private String label;
    
    // Description?
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String vendor;
    
    @Column(nullable = true)
    private String contentUrl;
    
    // attribute?
    @Column(nullable = true)
    private String gpgUrl;
    
    @Column(nullable = true)
    private Long metadataExpire;

    @CollectionOfElements(targetElement = String.class)
    @JoinTable(name = "cp_content_modified_products")
    private Set<String> modifiedProductIds = new HashSet<String>();

    public Content(String name, String id, String label, String type,
                    String vendor, String contentUrl,
                    String gpgUrl) {
        setName(name);
        setId(id);
        setLabel(label);
        setType(type);
        setVendor(vendor);
        setContentUrl(contentUrl);
        setGpgUrl(gpgUrl);
    }
    
    public Content() {
    }
    
    /* (non-Javadoc)
     * @see org.fedoraproject.candlepin.model.Persisted#getId()
     */
    @Override
    public String getId() {
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
    /**
     * @param modifiedProductIds the modifiedProductIds to set
     */
    public void setModifiedProductIds(Set<String> modifiedProductIds) {
        this.modifiedProductIds = modifiedProductIds;
    }

    /**
     * @return the modifiedProductIds
     */
    public Set<String> getModifiedProductIds() {
        return modifiedProductIds;
    }
    
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Content) {
            Content that = (Content) other;
            return new EqualsBuilder().append(this.contentUrl, that.contentUrl)
                .append(this.gpgUrl, that.gpgUrl)
                .append(this.label, that.label)
                .append(this.type, that.type)
                .append(this.vendor, that.vendor)
                .isEquals();
        }
        return false;
    }
    
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.contentUrl).append(this.gpgUrl)
            .append(this.label).append(this.name)
            .append(this.type).append(this.vendor)
            .toHashCode();
    }

    public Long getMetadataExpire() {
        return metadataExpire;
    }

    public void setMetadataExpire(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
    }

}
