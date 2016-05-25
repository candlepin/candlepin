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
import org.candlepin.util.Util;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
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
@Table(name = "cp2_content")
public class Content extends AbstractHibernateObject implements SharedEntity, Cloneable {

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

    @OneToMany
    @JoinTable(
        name = "cp2_owner_content",
        joinColumns = {@JoinColumn(name = "content_uuid", insertable = true, updatable = true)},
        inverseJoinColumns = {@JoinColumn(name = "owner_id")})
    @LazyCollection(LazyCollectionOption.FALSE)
    @XmlTransient
    private Set<Owner> owners;

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
    @Column(nullable =  true)
    @Size(max = 255)
    private String releaseVer;

    // attribute?
    @Column(nullable = true)
    @Size(max = 255)
    private String gpgUrl;

    @Column(nullable = true)
    private Long metadataExpire;

    @ElementCollection
    @CollectionTable(name = "cp2_content_modified_products", joinColumns = @JoinColumn(name = "content_uuid"))
    @Column(name = "element")
    @Size(max = 255)
    private Set<String> modifiedProductIds = new HashSet<String>();

    @Column(nullable = true)
    @Size(max = 255)
    private String arches;

    @XmlTransient
    @Column(name = "entity_version")
    private Integer entityVersion;

    @XmlTransient
    @Column
    @Type(type = "org.hibernate.type.NumericBooleanType")
    private Boolean locked;


    public Content(Owner owner, String name, String id, String label, String type,
        String vendor, String contentUrl, String gpgUrl, String arches) {
        addOwner(owner);
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

    /**
     * Creates a shallow copy of the specified source content. Owners, attributes and content are
     * not duplicated, but the joining objects are (ContentAttribute, ContentContent, etc.).
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
        this.setUpdated(source.getUpdated() != null ? (Date) source.getUpdated().clone() : null);

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection. The exception here is the modifiedProductIds, which uses the collection
        // directly, so we'll need to make a defensive copy.

        this.setType(source.getType());
        this.setLabel(source.getLabel());
        this.setOwners(source.getOwners());
        this.setName(source.getName());
        this.setVendor(source.getVendor());
        this.setContentUrl(source.getContentUrl());
        this.setRequiredTags(source.getRequiredTags());
        this.setReleaseVer(source.getReleaseVer());
        this.setGpgUrl(source.getGpgUrl());
        this.setMetadataExpire(source.getMetadataExpire());
        this.setArches(source.getArches());

        this.setModifiedProductIds(source.getModifiedProductIds() != null ?
            new HashSet<String>(source.getModifiedProductIds()) : null);
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

        if (source.getType() != null) {
            this.setType(source.getType());
        }

        if (source.getLabel() != null) {
            this.setLabel(source.getLabel());
        }

        if (source.getName() != null) {
            this.setName(source.getName());
        }

        if (source.getVendor() != null) {
            this.setVendor(source.getVendor());
        }

        if (source.getContentUrl() != null) {
            this.setContentUrl(source.getContentUrl());
        }

        if (source.getRequiredTags() != null) {
            this.setRequiredTags(source.getRequiredTags());
        }

        if (source.getReleaseVer() != null) {
            this.setReleaseVer(source.getReleaseVer());
        }

        if (source.getGpgUrl() != null) {
            this.setGpgUrl(source.getGpgUrl());
        }

        if (source.getMetadataExpire() != null) {
            this.setMetadataExpire(source.getMetadataExpire());
        }

        if (source.getArches() != null) {
            this.setArches(source.getArches());
        }

        if (source.getModifiedProductIds() != null) {
            // We need to make a copy of the modified products collection to ensure we don't link
            // the two instances
            this.setModifiedProductIds(new HashSet<String>(source.getModifiedProductIds()));
        }

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

        // Clear any existing collections to ensure we don't end up with shared collections
        copy.owners = null;
        copy.modifiedProductIds = null;

        // Impl note:
        // In most cases, our collection setters copy the contents of the input collections to their
        // own internal collections, so we don't need to worry about our two instances sharing a
        // collection.

        if (this.getOwners() != null) {
            copy.setOwners(this.getOwners());
        }

        if (this.getModifiedProductIds() != null) {
            copy.setModifiedProductIds(new HashSet<String>(this.getModifiedProductIds()));
        }

        copy.setCreated(this.getCreated() != null ? (Date) this.getCreated().clone() : null);
        copy.setUpdated(this.getUpdated() != null ? (Date) this.getUpdated().clone() : null);

        return copy;
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

    /**
     * Retrieves the owners with which this content is associated. If this content is not associated
     * with any owners, this method returns an empty set.
     * <p></p>
     * Note that changes made to the set returned by this method will be reflected by this object
     * and its backing data store.
     *
     * @return
     *  The set of owners with which this content is associated
     */
    @XmlTransient
    public Collection<Owner> getOwners() {
        return this.owners;
    }

    /**
     * Associates this content with the specified owner. If the given owner is already associated
     * with this content, the request is silently ignored.
     *
     * @param owner
     *  An owner to be associated with this content
     *
     * @return
     *  True if this content was successfully associated with the given owner; false otherwise
     */
    public boolean addOwner(Owner owner) {
        if (owner != null) {
            if (this.owners == null) {
                this.owners = new HashSet<Owner>();
            }

            return this.owners.add(owner);
        }

        return false;
    }

    /**
     * Disassociates this content with the specified owner. If the given owner is not associated
     * with this content, the request is silently ignored.
     *
     * @param owner
     *  The owner to disassociate from this content
     *
     * @return
     *  True if the content was disassociated successfully; false otherwise
     */
    public boolean removeOwner(Owner owner) {
        return (this.owners != null && owner != null) ? this.owners.remove(owner) : false;
    }

    /**
     * Sets the owners with which this content is associated.
     *
     * @param owners
     *  A collection of owners to be associated with this content
     *
     * @return
     *  A reference to this content
     */
    public Content setOwners(Collection<Owner> owners) {
        if (this.owners == null) {
            this.owners = new HashSet<Owner>();
        }

        this.owners.clear();

        if (owners != null) {
            this.owners.addAll(owners);
        }

        return this;
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
                .append(this.locked, that.locked)
                .isEquals();

            if (equals) {
                if (!Util.collectionsAreEqual(this.modifiedProductIds, that.modifiedProductIds)) {
                    if (!(this.modifiedProductIds == null && that.modifiedProductIds.size() == 0) &&
                        !(that.modifiedProductIds == null && this.modifiedProductIds.size() == 0)) {

                        return false;
                    }
                }
            }

            return equals;
        }

        return false;
    }

    @Override
    public int hashCode() {
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
            .append(this.arches)
            .append(this.locked);

        // Impl note:
        // Because we handle the collections specially in .equals, we have to do the same special
        // treatment here to ensure our output doesn't give us wonky results when compared to the
        // output of .equals

        if (this.modifiedProductIds != null && this.modifiedProductIds.size() > 0) {
            builder.append(this.modifiedProductIds);
        }

        return builder.toHashCode();
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

    @Override
    public String toString() {
        return "Content [id: " + getId() + ", label: " + getLabel() + "]";
    }

    @XmlTransient
    @JsonIgnore
    public Content setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    @XmlTransient
    public boolean isLocked() {
        return this.locked != null && this.locked;
    }

    @PrePersist
    @PreUpdate
    public void updateEntityVersion() {
        this.entityVersion = this.hashCode();
    }
}
