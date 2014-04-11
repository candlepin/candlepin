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

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.service.UniqueIdGenerator;
import org.hibernate.annotations.Type;

/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp_content")
public class Content extends AbstractHibernateObject {

    public static final  String UEBER_CONTENT_NAME = "ueber_content";

    @Id
    @Size(max = 255)
    @NotNull
    private String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String type;

    @Column(nullable = false, unique = true)
    @Size(max = 255)
    @NotNull
    private String label;

    // Description?

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String vendor;

    @Column(nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String contentUrl;

    @Column(nullable = true)
    @Size(max = 255)
    private String requiredTags;

    // for selecting Y/Z stream
    @Column(nullable =  true)
    @Size(max = 255)
    private String releaseVer;

    // attribute?
    @Column(nullable = true)
    @Type(type = "org.candlepin.hibernate.EmptyStringUserType")
    @Size(max = 255)
    private String gpgUrl;

    @Column(nullable = true)
    private Long metadataExpire;

    @ElementCollection
    @CollectionTable(name = "cp_content_modified_products",
                     joinColumns = @JoinColumn(name = "cp_content_id"))
    @Column(name = "element")
    @Size(max = 255)
    private Set<String> modifiedProductIds = new HashSet<String>();

    @Column(nullable = true)
    @Size(max = 255)
    private String arches;

    public Content(String name, String id, String label, String type,
        String vendor, String contentUrl, String gpgUrl, String arches) {
        setName(name);
        setId(id);
        setLabel(label);
        setType(type);
        setVendor(vendor);
        setContentUrl(contentUrl);
        setGpgUrl(gpgUrl);
        setArches(arches);
    }

    public Content() {
    }

    public static Content createUeberContent(
        UniqueIdGenerator idGenerator, Owner o, Product p) {

        return new Content(
            UEBER_CONTENT_NAME, idGenerator.generateId(),
            ueberContentLabelForProduct(p), "yum", "Custom",
            "/" + o.getKey(), "", "");
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.model.Persisted#getId()
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
                .append(this.name, that.name)
                .append(this.type, that.type)
                .append(this.vendor, that.vendor).isEquals();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7).append(this.contentUrl)
            .append(this.gpgUrl).append(this.label).append(this.name)
            .append(this.type).append(this.vendor).toHashCode();
    }

    public Long getMetadataExpire() {
        return metadataExpire;
    }

    public void setMetadataExpire(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
    }

    /**
     * @return Comma separated list of tags this content set requires to be
     *         enabled.
     */
    public String getRequiredTags() {
        return requiredTags;
    }

    /**
     * @param requiredTags Comma separated list of tags this content set
     *        requires.
     */
    public void setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
    }

    public static String ueberContentLabelForProduct(Product p) {
        return p.getId() + "_" + UEBER_CONTENT_NAME;
    }

    /**
     * @param releaseVer the releaseVer to set
     */
    public void setReleaseVer(String releaseVer) {
        this.releaseVer = releaseVer;
    }

    /**
     * @return the releaseVer
     */
    public String getReleaseVer() {
        return releaseVer;
    }

    public void setArches(String arches) {
        this.arches = arches;
    }

    public String getArches() {
        return arches;
    }

    /**
     * @param from Content object to copy properties from.
     * @return current Content object with updated properties
     */
    public Content copyProperties(Content from) {
        setType(from.getType());
        setLabel(from.getLabel());
        setName(from.getName());
        setVendor(from.getVendor());
        setContentUrl(from.getContentUrl());
        setRequiredTags(from.getRequiredTags());
        setReleaseVer(from.getReleaseVer());
        setGpgUrl(from.getGpgUrl());
        setMetadataExpire(from.getMetadataExpire());
        setModifiedProductIds(defaultIfNull(from.getModifiedProductIds(),
            new HashSet<String>()));
        setArches(from.getArches());

        return this;
    }

    private <T> T defaultIfNull(T val, T dflt) {
        return val == null ? dflt : val;
    }

}
