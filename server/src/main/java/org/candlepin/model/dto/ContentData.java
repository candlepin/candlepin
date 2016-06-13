/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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
package org.candlepin.model.dto;

import org.candlepin.model.Content;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;



/**
 * DTO representing the content data exposed to the API and adapter layers.
 *
 * <pre>
 * {
 *   "uuid" : "ff808081554a3e4101554a3e83be003d",
 *   "id" : "5001",
 *   "type" : "yum",
 *   "label" : "admin-tagged-content",
 *   "name" : "admin-tagged-content",
 *   "vendor" : "test-vendor",
 *   "contentUrl" : "/admin/foo/path/always",
 *   "requiredTags" : "TAG1,TAG2",
 *   "releaseVer" : null,
 *   "gpgUrl" : "/admin/foo/path/always/gpg",
 *   "metadataExpire" : null,
 *   "modifiedProductIds" : [ ... ],
 *   "arches" : null,
 *   "created" : "2016-06-13T14:50:58+0000",
 *   "updated" : "2016-06-13T14:50:58+0000"
 * }
 * </pre>
 */
@XmlRootElement
public class ContentData extends CandlepinDTO {

    protected String uuid;
    protected String id;
    protected String type;
    protected String label;
    protected String name;
    protected String vendor;
    protected String contentUrl;
    protected String requiredTags;
    protected String releaseVer;
    protected String gpgUrl;
    protected Long metadataExpire;
    protected Set<String> modifiedProductIds;
    protected String arches;

    /**
     * Initializes a new ContentData instance with null values.
     */
    public ContentData() {
        super();
    }

    /**
     * Initializes a new ContentData instance using the data contained by the given DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ContentData(ContentData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
    }

    /**
     * Initializes a new ContentData instance using the data contained by the given entity.
     *
     * @param entity
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if entity is null
     */
    public ContentData(Content entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is null");
        }

        this.populate(entity);
    }

    /**
     * Retrieves the UUID of the content represented by this DTO. If the UUID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the UUID of the content, or null if the UUID has not yet been defined
     */
    public String getUuid() {
        return this.uuid;
    }

    /**
     * Sets the UUID of the content represented by this DTO.
     *
     * @param updated
     *  The UUID of the content represented by this DTO, or null to clear the UUID
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Retrieves the ID of the content represented by this DTO. If the ID has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the ID of the content, or null if the ID has not yet been defined
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the ID of the content represented by this DTO.
     *
     * @param updated
     *  The ID of the content represented by this DTO, or null to clear the ID
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the type of the content represented by this DTO. If the type has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the type of the content, or null if the type has not yet been defined
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the type of the content represented by this DTO.
     *
     * @param updated
     *  The type of the content represented by this DTO, or null to clear the type
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the label of the content represented by this DTO. If the label has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the label of the content, or null if the label has not yet been defined
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the label of the content represented by this DTO.
     *
     * @param updated
     *  The label of the content represented by this DTO, or null to clear the label
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Retrieves the name of the content represented by this DTO. If the name has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the name of the content, or null if the name has not yet been defined
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the content represented by this DTO.
     *
     * @param updated
     *  The name of the content represented by this DTO, or null to clear the name
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the vendor of the content represented by this DTO. If the vendor has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the vendor of the content, or null if the vendor has not yet been defined
     */
    public String getVendor() {
        return this.vendor;
    }

    /**
     * Sets the vendor of the content represented by this DTO.
     *
     * @param updated
     *  The vendor of the content represented by this DTO, or null to clear the vendor
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    /**
     * Retrieves the content URL of the content represented by this DTO. If the content URL has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the content URL of the content, or null if the content URL has not yet been defined
     */
    public String getContentUrl() {
        return this.contentUrl;
    }

    /**
     * Sets the content URL of the content represented by this DTO.
     *
     * @param updated
     *  The content URL of the content represented by this DTO, or null to clear the content URL
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
        return this;
    }

    /**
     * Retrieves the required tags of the content represented by this DTO. If the required tags has
     * not yet been defined, this method returns null.
     *
     * @return
     *  the required tags of the content, or null if the required tags has not yet been defined
     */
    public String getRequiredTags() {
        return this.requiredTags;
    }

    /**
     * Sets the required tags of the content represented by this DTO.
     *
     * @param updated
     *  The required tags of the content represented by this DTO, or null to clear the required
     *  tags
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    /**
     * Retrieves the release version of the content represented by this DTO. If the release version
     * has not yet been defined, this method returns null.
     *
     * @return
     *  the release version of the content, or null if the release version has not yet been defined
     */
    @JsonProperty("releaseVer")
    public String getReleaseVersion() {
        return this.releaseVer;
    }

    /**
     * Sets the release version of the content represented by this DTO.
     *
     * @param updated
     *  The release version of the content represented by this DTO, or null to clear the release
     *  version
     *
     * @return
     *  a reference to this DTO
     */
    @JsonProperty("releaseVer")
    public ContentData setReleaseVersion(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    /**
     * Retrieves the GPG URL of the content represented by this DTO. If the GPG URL has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the GPG URL of the content, or null if the GPG URL has not yet been defined
     */
    public String getGpgUrl() {
        return this.gpgUrl;
    }

    /**
     * Sets the GPG URL of the content represented by this DTO.
     *
     * @param updated
     *  The GPG URL of the content represented by this DTO, or null to clear the GPG URL
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    /**
     * Retrieves the metadata expiration of the content represented by this DTO. If the metadata
     * expiration has not yet been defined, this method returns null.
     *
     * @return
     *  the metadata expiration of the content, or null if the metadata expiration has not yet been
     *  defined
     */
    public Long getMetadataExpire() {
        return this.metadataExpire;
    }

    /**
     * Sets the metadata expiration of the content represented by this DTO.
     *
     * @param updated
     *  The metadata expiration of the content represented by this DTO, or null to clear the
     *  metadata expiration
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setMetadataExpire(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    /**
     * Retrieves the modified product IDs of the content represented by this DTO. If the modified
     * product IDs has not yet been defined, this method returns null.
     *
     * @return
     *  the modified product IDs of the content, or null if the modified product IDs has not yet
     *  been defined
     */
    public Set<String> getModifiedProductIds() {
        return this.modifiedProductIds != null ?
            Collections.unmodifiableSet(this.modifiedProductIds) :
            Collections.<String>emptySet();
    }

    /**
     * Sets the modified product IDs of the content represented by this DTO. If this DTO already has
     * modified product IDs defined, they will be cleared before assigning the given product IDs.
     *
     * @param modifiedProductIds
     *  A collection of product IDs to be modified by the content content, or null to remove changes
     *  to modified product ids
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setModifiedProductIds(Collection<String> modifiedProductIds) {
        if (modifiedProductIds != null) {
            if (this.modifiedProductIds == null) {
                this.modifiedProductIds = new HashSet<String>();
            }
            else {
                this.modifiedProductIds.clear();
            }

            this.modifiedProductIds.addAll(modifiedProductIds);
        }
        else {
            this.modifiedProductIds = null;
        }

        return this;
    }

    /**
     * Adds the specified product ID as a product ID to be modified by the content represented by
     * this DTO. If the product ID is already modified in this DTO, it will not be added again.
     *
     * @param productId
     *  The product ID to add as a modified product ID to this DTO
     *
     * @return
     *  true if the product ID was added successfully; false otherwise
     */
    public boolean addModifiedProductId(String productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId is null");
        }

        if (this.modifiedProductIds == null) {
            this.modifiedProductIds = new HashSet<String>();
        }

        return this.modifiedProductIds.add(productId);
    }

    /**
     * Removes the specified product ID from the collection of product IDs to be modified by the
     * content represented by this DTO. If the product ID is not modified by this DTO, this
     *
     * @param productId
     *  The product ID to remove from the modified product IDs on this DTO
     *
     * @return
     *  true if the product ID was removed successfully; false otherwise
     */
    public boolean removeModifiedProductId(String productId) {
        return this.modifiedProductIds != null ? this.modifiedProductIds.remove(productId) : false;
    }

    /**
     * Retrieves the arches of the content represented by this DTO. If the arches has not yet been
     * defined, this method returns null.
     *
     * @return
     *  the arches of the content, or null if the arches has not yet been defined
     */
    public String getArches() {
        return this.arches;
    }

    /**
     * Sets the arches of the content represented by this DTO.
     *
     * @param updated
     *  The arches of the content represented by this DTO, or null to clear the arches
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setArches(String arches) {
        this.arches = arches;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ContentData)) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        ContentData that = (ContentData) obj;

        EqualsBuilder builder = new EqualsBuilder()
            .append(this.uuid, that.uuid)
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
            .append(this.modifiedProductIds, that.modifiedProductIds)
            .append(this.arches, that.arches);

        return super.equals(obj) && builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(7, 17)
            .append(super.hashCode())
            .append(this.uuid)
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
            .append(this.modifiedProductIds)
            .append(this.arches);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ContentData copy = (ContentData) super.clone();

        copy.uuid = this.uuid;
        copy.id = this.id;
        copy.type = this.type;
        copy.label = this.label;
        copy.name = this.name;
        copy.vendor = this.vendor;
        copy.contentUrl = this.contentUrl;
        copy.requiredTags = this.requiredTags;
        copy.releaseVer = this.releaseVer;
        copy.gpgUrl = this.gpgUrl;
        copy.metadataExpire = this.metadataExpire;

        copy.setModifiedProductIds(this.modifiedProductIds);

        return copy;
    }

    /**
     * Populates this DTO with the data from the given source DTO.
     *
     * @param source
     *  The source DTO from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData populate(ContentData source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.uuid = source.getUuid();
        this.id = source.getId();
        this.type = source.getType();
        this.label = source.getLabel();
        this.name = source.getName();
        this.vendor = source.getVendor();
        this.contentUrl = source.getContentUrl();
        this.requiredTags = source.getRequiredTags();
        this.releaseVer = source.getReleaseVersion();
        this.gpgUrl = source.getGpgUrl();
        this.metadataExpire = source.getMetadataExpire();

        this.setModifiedProductIds(source.getModifiedProductIds());

        return this;
    }

    /**
     * Populates this DTO with data from the given source entity.
     *
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData populate(Content source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        super.populate(source);

        this.uuid = source.getUuid();
        this.id = source.getId();
        this.type = source.getType();
        this.label = source.getLabel();
        this.name = source.getName();
        this.vendor = source.getVendor();
        this.contentUrl = source.getContentUrl();
        this.requiredTags = source.getRequiredTags();
        this.releaseVer = source.getReleaseVer();
        this.gpgUrl = source.getGpgUrl();
        this.metadataExpire = source.getMetadataExpire();

        this.setModifiedProductIds(source.getModifiedProductIds());

        return this;
    }
}
