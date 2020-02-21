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

import org.candlepin.model.dto.ContentData;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.util.SetView;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


/**
 * ProductContent
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Table(name = Content.DB_TABLE)
public class Content extends AbstractHibernateObject implements SharedEntity, Cloneable, ContentInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp2_content";

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

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String name;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    private String vendor;

    @Column(nullable = true)
    @Size(max = 255)
    private String contentUrl;

    @Column(nullable = true)
    @Size(max = 255)
    private String requiredTags;

    // for selecting Y/Z stream
    @Column(nullable = true)
    @Size(max = 255)
    private String releaseVer;

    // attribute?
    @Column(nullable = true)
    @Size(max = 255)
    private String gpgUrl;

    @Column(nullable = true)
    private Long metadataExpire;

    @BatchSize(size = 128)
    @ElementCollection
    @CollectionTable(name = "cp2_content_modified_products", joinColumns = @JoinColumn(name = "content_uuid"))
    @Column(name = "element")
    @Size(max = 255)
    @Immutable
    @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private Set<String> modifiedProductIds;

    @Column(nullable = true)
    @Size(max = 255)
    private String arches;

    @XmlTransient
    @Column(name = "entity_version")
    private Integer entityVersion;

    @XmlTransient
    @Column
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private boolean locked;

    /**
     * Default constructor
     */
    public Content() {
        this.modifiedProductIds = new HashSet<>();
    }

    /**
     * ID-based constructor so API users can specify an ID in place of a full object.
     *
     * @param id
     *  The ID for this content
     */
    public Content(String id) {
        this();

        this.setId(id);
    }

    public Content(String id, String name, String type, String label, String vendor) {
        this(id);

        this.setName(name);
        this.setType(type);
        this.setLabel(label);
        this.setVendor(vendor);
    }

    /**
     * Creates a shallow copy of the specified source content. Attributes and content are not
     * duplicated, but the joining objects are (ContentAttribute, ContentContent, etc.).
     * <p></p>
     * Unlike the merge method, all properties from the source content are copied, including the
     * state of any null collections and any identifier fields.
     *
     * @param source
     *  The Content instance to copy
     */
    protected Content(Content source) {
        this.setUuid(source.getUuid());
        this.setId(source.getId());

        this.setCreated(source.getCreated() != null ? (Date) source.getCreated().clone() : null);

        this.merge(source);
    }

    /**
     * Copies several properties from the given content on to this content instance. Properties that
     * are not copied over include any identifiying fields (UUID, ID), the creation date and locking
     * states. Values on the source content which are null will be ignored.
     *
     * @param source
     *  The source content instance from which to pull content information
     *
     * @return
     *  this content instance
     */
    public Content merge(Content source) {
        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);

        this.setType(source.getType());
        this.setLabel(source.getLabel());
        this.setName(source.getName());
        this.setVendor(source.getVendor());
        this.setContentUrl(source.getContentUrl());
        this.setRequiredTags(source.getRequiredTags());
        this.setReleaseVersion(source.getReleaseVersion());
        this.setGpgUrl(source.getGpgUrl());
        this.setMetadataExpiration(source.getMetadataExpiration());
        this.setArches(source.getArches());
        this.setModifiedProductIds(source.getModifiedProductIds());

        return this;
    }

    @Override
    public Content clone() {
        Content copy;

        try {
            copy = (Content) super.clone();
        }
        catch (CloneNotSupportedException e) {
            // This should never happen.
            throw new RuntimeException("Clone not supported", e);
        }

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.
        copy.modifiedProductIds = new HashSet<>();
        copy.setModifiedProductIds(this.getModifiedProductIds());

        copy.setCreated(this.getCreated() != null ? (Date) this.getCreated().clone() : null);
        copy.setUpdated(this.getUpdated() != null ? (Date) this.getUpdated().clone() : null);

        return copy;
    }

    /**
     * Returns a DTO representing this entity.
     *
     * @return
     *  a DTO representing this entity
     */
    public ContentData toDTO() {
        return new ContentData(this);
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
    @Override
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

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    @Override
    public String getContentUrl() {
        return contentUrl;
    }

    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }

    /**
     * @return Comma separated list of tags this content set requires to be
     *         enabled.
     */
    @Override
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

    /**
     * @return the releaseVer
     */
    @Override
    public String getReleaseVersion() {
        return releaseVer;
    }

    /**
     * @param releaseVer the releaseVer to set
     */
    public void setReleaseVersion(String releaseVer) {
        this.releaseVer = releaseVer;
    }

    @Override
    public String getGpgUrl() {
        return gpgUrl;
    }

    public void setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
    }

    @Override
    public Long getMetadataExpiration() {
        return metadataExpire;
    }

    public void setMetadataExpiration(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
    }

    /**
     * Retrieves the collection of IDs representing products that are modified by this content. If
     * the modified product IDs have not yet been defined, this method returns an empty collection.
     *
     * @return
     *  the modified product IDs of the content
     */
    public Collection<String> getModifiedProductIds() {
        return new SetView(this.modifiedProductIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getRequiredProductIds() {
        return this.getModifiedProductIds();
    }

    /**
     * Adds the specified product ID as a required product ID for this content. If the product ID
     * is already required by this content, it will not be added again.
     *
     * @param productId
     *  The product ID to add as a modified product ID to this content
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the product ID was added successfully; false otherwise
     */
    public boolean addModifiedProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        return this.modifiedProductIds.add(productId);
    }

    /**
     * Removes the specified product ID from the collection of required product IDs. If the product ID
     * is not required by this content, this method does nothing.
     *
     * @param productId
     *  The product ID to remove from the required product IDs of this content
     *
     * @throws IllegalArgumentException
     *  if productId is null
     *
     * @return
     *  true if the product ID was removed successfully; false otherwise
     */
    public boolean removeModifiedProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        return this.modifiedProductIds != null ? this.modifiedProductIds.remove(productId) : false;
    }

    /**
     * Sets the required product IDs for this content to be present to an entitlement. Any
     * previously existing required product IDs will be cleared before assigning the given product
     * IDs.
     *
     * @param requiredProductIds
     *  A collection of product IDs to be required by this content, or null to clear the existing
     *  required product IDs
     *
     * @return
     *  a reference to this entity
     */
    public Content setModifiedProductIds(Collection<String> requiredProductIds) {
        this.modifiedProductIds.clear();

        if (requiredProductIds != null) {
            this.modifiedProductIds.addAll(requiredProductIds);
        }

        return this;
    }

    public void setArches(String arches) {
        this.arches = arches;
    }

    @Override
    public String getArches() {
        return arches;
    }

    @XmlTransient
    @JsonIgnore
    public Content setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    @XmlTransient
    public boolean isLocked() {
        return this.locked;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Content) {
            Content that = (Content) other;

            boolean equals = new EqualsBuilder()
                .append(this.id, that.id)
                .append(this.type, that.type)
                .append(this.label, that.label)
                .append(this.name, that.name)
                .append(this.vendor, that.vendor)
                .append(this.contentUrl, that.contentUrl)
                .append(this.requiredTags, that.requiredTags)
                .append(this.releaseVer, that.releaseVer)
                .append(this.gpgUrl, that.gpgUrl)
                .append(this.metadataExpire, that.metadataExpire)
                .append(this.arches, that.arches)
                .isEquals();

            if (equals) {
                if (!Util.collectionsAreEqual(this.modifiedProductIds, that.modifiedProductIds)) {
                    return false;
                }
            }

            return equals;
        }

        return false;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(this.id);

        return builder.toHashCode();
    }

    /**
     * Calculates and returns a version hash for this entity. This method operates much like the
     * hashCode method, except that it is more accurate and should have fewer collisions.
     *
     * @return
     *  a version hash for this entity
     */
    public int getEntityVersion() {
        return this.getEntityVersion(false);
    }

    public int getEntityVersion(boolean useCache) {
        if (useCache && this.entityVersion != null) {
            return this.entityVersion;
        }

        // This must always be a subset of equals
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(this.id)
            .append(this.type)
            .append(this.label)
            .append(this.name)
            .append(this.vendor)
            .append(this.contentUrl)
            .append(this.requiredTags)
            .append(this.releaseVer)
            .append(this.gpgUrl)
            .append(this.metadataExpire)
            .append(this.arches);

        // Impl note:
        // We need to be certain that the hash code is calculated in a way that's order
        // independent and not subject to Hibernate's poor hashCode implementation on proxy
        // collections. This calculation follows that defined by the Set.hashCode method.
        int accumulator = 0;

        if (!this.modifiedProductIds.isEmpty()) {
            for (String pid : this.modifiedProductIds) {
                accumulator += (pid != null ? pid.hashCode() : 0);
            }

            builder.append(accumulator);
        }

        // Return
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return String.format("Content [uuid: %s, id: %s, name: %s, label: %s]",
                this.uuid, this.id, this.name, this.label);
    }

    @PrePersist
    @PreUpdate
    public void updateEntityVersion() {
        this.entityVersion = this.getEntityVersion();
    }
}
