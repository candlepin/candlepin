/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.util.Util;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.util.Collection;
import java.util.Collections;
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
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * ProductContent
 */
@Entity
@Table(name = Content.DB_TABLE)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Content extends AbstractHibernateObject implements SharedEntity, Cloneable, ContentInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_contents";

    // Object ID
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String uuid;

    // TODO: FIXME: This field uses the NullAsEmptyStringType to silently convert any null types we
    // provide at runtime to empty strings in the DB. Not because we *want* empty strings, but
    // because nulls aren't distinct in constraints. PostgreSQL can work around it using partial
    // indexes, but MariaDB has much more trouble with it and requires extra work (generated
    // columns). If we drop support for MariaDB, we can also drop the conversion and go to straight
    // nulls + partial indexes to retain uniqueness on (namespace, id). If we do so,
    // any namespace-aware queries will need to be updated to change the global namespace lookup
    // from "namespace = ''" to "namespace IS NULL".
    @Column(name = "namespace")
    @Type(type = "org.candlepin.hibernate.NullAsEmptyStringType")
    private String namespace;

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

    @Column(name = "content_url", nullable = true)
    @Size(max = 255)
    private String contentUrl;

    @Column(name = "required_tags", nullable = true)
    @Size(max = 255)
    private String requiredTags;

    // for selecting Y/Z stream
    @Column(name = "release_ver", nullable = true)
    @Size(max = 255)
    private String releaseVer;

    // attribute?
    @Column(name = "gpg_url", nullable = true)
    @Size(max = 255)
    private String gpgUrl;

    @Column(name = "metadata_expire", nullable = true)
    private Long metadataExpire;

    // Impl note:
    // As of 2022-11-14, the FK for this table has a delete cascade for automated cleanup. It should
    // be removed if the ridiculous limitations placed on @elementcollections are ever sorted at the
    // JPA spec level.
    @BatchSize(size = 128)
    @ElementCollection
    @CollectionTable(name = "cp_content_required_products", joinColumns = @JoinColumn(name = "content_uuid"))
    @Column(name = "product_id")
    @Size(max = 255)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<String> modifiedProductIds;

    @Column(nullable = true)
    @Size(max = 255)
    private String arches;

    /**
     * Zero-arg constructor for Hibernate. Do not use.
     */
    Content() {
        this.modifiedProductIds = new HashSet<>();
    }

    /**
     * Creates a new Content instance with the given content ID. Note that the content ID specified here
     * is the upstream content ID, not the Candlepin-internal content UUID.
     *
     * @param id
     *  The upstream content ID for the new content instance; cannot be null or empty
     */
    public Content(String id) {
        this();

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("content ID is null or empty");
        }

        this.id = id;
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
     *
     * @return
     *  a reference to this content instance
     */
    public Content setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Fetches the namespace that contains this entity. If this entity exists in the global
     * namespace, this method returns null.
     *
     * @return
     *  the namespace this entity exists in, or null if the entity is part of the global namespace
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Sets the namespace for this entity to the provided owner. If the owner is null, or lacks an
     * org key, this entity will be assigned to the global namespace.
     *
     * @param owner
     *  the organization to use as this entity's namespace
     *
     * @return
     *  a reference to this content instance
     */
    public Content setNamespace(Owner owner) {
        this.namespace = owner != null ? owner.getKey() : null;
        return this;
    }

    /**
     * Sets the namespace for this entity. If the provided namespace is null or empty, this entity
     * will be assigned to the global namespace.
     *
     * @param namespace
     *  the namespace to assign to this entity
     *
     * @return
     *  a reference to this content instance
     */
    public Content setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
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

    @Override
    public String getType() {
        return type;
    }

    public Content setType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public String getLabel() {
        return label;
    }

    public Content setLabel(String label) {
        this.label = label;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    public Content setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getVendor() {
        return vendor;
    }

    public Content setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    @Override
    public String getContentUrl() {
        return this.contentUrl != null && !this.contentUrl.isEmpty() ? this.contentUrl : null;
    }

    public Content setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
        return this;
    }

    /**
     * @return
     *  a comma-separated list of tags this content set requires to be enabled
     */
    @Override
    public String getRequiredTags() {
        return this.requiredTags != null && !this.requiredTags.isEmpty() ? this.requiredTags : null;
    }

    /**
     * @param requiredTags
     *  a comma-separated list of tags this content set requires
     *
     * @return
     *  a reference to this content instance
     */
    public Content setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    /**
     * @return the releaseVer
     */
    @Override
    public String getReleaseVersion() {
        return this.releaseVer != null && !this.releaseVer.isEmpty() ? this.releaseVer : null;
    }

    /**
     * @param releaseVer the releaseVer to set
     *
     * @return
     *  a reference to this content instance
     */
    public Content setReleaseVersion(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    @Override
    public String getGpgUrl() {
        return this.gpgUrl != null && !this.gpgUrl.isEmpty() ? this.gpgUrl : null;
    }

    public Content setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    @Override
    public Long getMetadataExpiration() {
        return metadataExpire;
    }

    public Content setMetadataExpiration(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    /**
     * Retrieves the collection of IDs representing products that are required by this content. If
     * the required product IDs have not yet been defined, this method returns an empty collection.
     *
     * @return
     *  the required product IDs of this content
     */
    public Set<String> getModifiedProductIds() {
        return this.modifiedProductIds != null ?
            Collections.unmodifiableSet(this.modifiedProductIds) :
            Set.of();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getRequiredProductIds() {
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
        if (this.modifiedProductIds == null) {
            this.modifiedProductIds = new HashSet<>();
        }
        this.modifiedProductIds.clear();

        if (requiredProductIds != null) {
            this.modifiedProductIds.addAll(requiredProductIds);
        }

        return this;
    }

    public Content setArches(String arches) {
        this.arches = arches;
        return this;
    }

    @Override
    public String getArches() {
        return this.arches != null && !this.arches.isEmpty() ? this.arches : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Content)) {
            return false;
        }

        Content that = (Content) obj;

        // - If the objects have the same non-null UUID, they are equal
        // - If the objects have different entity versions, they cannot be equal
        // - If the objects have the same entity versions, run through the checks below to
        //   avoid collisions
        if (this.getUuid() != null && this.getUuid().equals(that.getUuid())) {
            return true;
        }

        boolean equals = new EqualsBuilder()
            .append(this.getId(), that.getId())
            .append(this.getType(), that.getType())
            .append(this.getLabel(), that.getLabel())
            .append(this.getName(), that.getName())
            .append(this.getVendor(), that.getVendor())
            .append(this.getMetadataExpiration(), that.getMetadataExpiration())

            // These fields require special consideration, as nulls and empty strings are
            // considered identical for CP's purposes. The accessors should fix this for us,
            // but if equality checks start failing in the future, this is something to check.
            .append(this.getContentUrl(), that.getContentUrl())
            .append(this.getRequiredTags(), that.getRequiredTags())
            .append(this.getReleaseVersion(), that.getReleaseVersion())
            .append(this.getGpgUrl(), that.getGpgUrl())
            .append(this.getArches(), that.getArches())
            .isEquals();

        equals = equals && Util.collectionsAreEqual(this.getModifiedProductIds(),
            that.getModifiedProductIds());

        return equals;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 17)
            .append(this.getId())
            .toHashCode();
    }

    @Override
    public String toString() {
        return String.format("Content [uuid: %s, id: %s, name: %s, label: %s]",
            this.getUuid(), this.getId(), this.getName(), this.getLabel());
    }

}
