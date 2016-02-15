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

import org.candlepin.service.UniqueIdGenerator;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Table(name = "cp2_content")
public class Content extends AbstractHibernateObject {

    public static final  String UEBER_CONTENT_NAME = "ueber_content";

    // Object ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String uuid;

    // Internal RH content ID
    @Column(name = "content_id")
    @Size(max = 32)
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

    @ManyToOne
    @JoinColumn(nullable = false)
    @NotNull
    private Owner owner;

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
    @CollectionTable(name = "cp2_content_modified_products",
                     joinColumns = @JoinColumn(name = "content_uuid"))
    @Column(name = "element")
    @Size(max = 255)
    private Set<String> modifiedProductIds = new HashSet<String>();

    @Column(nullable = true)
    @Size(max = 255)
    private String arches;

    public Content(Owner owner, String name, String id, String label, String type,
        String vendor, String contentUrl, String gpgUrl, String arches) {
        setOwner(owner);
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

    /**
     * ID-based constructor so API users can specify an ID in place of a full object.
     *
     * @param id
     *  The ID for this content
     */
    public Content(String id) {
        this.setId(id);
    }

    public static Content createUeberContent(UniqueIdGenerator idGenerator, Owner o, Product p) {
        return new Content(
            o, UEBER_CONTENT_NAME, idGenerator.generateId(),
            ueberContentLabelForProduct(p), "yum", "Custom",
            "/" + o.getKey(), "", "");
    }

    /**
     * Retrieves this content's object/database UUID. While the content ID may exist multiple times
     * in the database (if in use by multiple owners), this UUID uniquely identifies a
     * content instance.
     *
     * @return
     *  this content's database UUID.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets this content's object/database ID. Note that this ID is used to uniquely identify this
     * particular object and has no baring on the Red Hat content ID.
     *
     * @param uuid
     *  The object ID to assign to this content.
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * Retrieves this content's ID. Assigned by the content provider, and may exist in
     * multiple owners, thus may not be unique in itself.
     *
     * @return
     *  this content's ID.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the content ID for this content. The content ID is the Red Hat content ID and should not
     * be confused with the object ID.
     *
     * @param id
     *  The new content ID for this content.
     */
    public void setId(String id) {
        this.id = id;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Owner getOwner() {
        return this.owner;
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
            return new EqualsBuilder()
                .append(this.contentUrl, that.contentUrl)
                .append(this.gpgUrl, that.gpgUrl)
                .append(this.label, that.label)
                .append(this.metadataExpire, that.metadataExpire)
                .append(this.name, that.name)
                .append(this.releaseVer, that.releaseVer)
                .append(this.requiredTags, that.requiredTags)
                .append(this.type, that.type)
                .append(this.vendor, that.vendor)
                .append(this.arches, that.arches)
                .append(this.modifiedProductIds, that.modifiedProductIds)
                .append(this.owner, that.owner)
                .isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        // This must always be a subset of equals
        return new HashCodeBuilder(37, 7)
            .append(this.contentUrl)
            .append(this.gpgUrl)
            .append(this.label)
            .append(this.metadataExpire)
            .append(this.name)
            .append(this.releaseVer)
            .append(this.requiredTags)
            .append(this.type)
            .append(this.vendor)
            .append(this.arches)
            .append(this.modifiedProductIds)
            .toHashCode();
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
        return p.getUuid() + "_" + UEBER_CONTENT_NAME;
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
        setContentUrl(from.getContentUrl());
        setGpgUrl(from.getGpgUrl());
        setLabel(from.getLabel());
        setMetadataExpire(from.getMetadataExpire());
        setName(from.getName());
        setReleaseVer(from.getReleaseVer());
        setRequiredTags(from.getRequiredTags());
        setType(from.getType());
        setVendor(from.getVendor());
        setArches(from.getArches());
        setModifiedProductIds(defaultIfNull(from.getModifiedProductIds(), new HashSet<String>()));

        return this;
    }

    private <T> T defaultIfNull(T val, T dflt) {
        return val == null ? dflt : val;
    }

    @Override
    public String toString() {
        return "Content [id: " + getId() + ", label: " + getLabel() + "]";
    }

}
