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
import org.candlepin.service.model.ContentInfo;
import org.candlepin.util.SetView;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * DTO representing the content data exposed to the API and adapter layers.
 *
 * <pre>
 * {
 *   "uuid" : "ff808081554a3e4101554a3e83be003d",
 *   "id" : "5001",
 *   "type" : "yum",
 *   "label" : "content_label",
 *   "name" : "content_name",
 *   "vendor" : "example-vendor",
 *   "contentUrl" : "/admin/foo/example/path",
 *   "requiredTags" : "TAG1,TAG2",
 *   "releaseVer" : "1.2.3",
 *   "gpgUrl" : "/admin/foo/example/gpg/path",
 *   "metadataExpire" : 1467124079,
 *   "modifiedProductIds" : [ ... ],
 *   "arches" : "x86_64",
 *   "created" : "2016-06-13T14:50:58+0000",
 *   "updated" : "2016-06-13T14:50:58+0000"
 * }
 * </pre>
 */
@XmlRootElement
public class ContentData extends CandlepinDTO implements ContentInfo {
    public static final long serialVersionUID = 1L;

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

    protected Boolean locked;

    /**
     * Initializes a new ContentData instance with null values.
     */
    public ContentData() {
        super();
    }

    /**
     * Initializes a new ContentData instance with the specified values.
     * <p/></p>
     * <strong>Note</strong>: This constructor passes the provided values to their respective
     * mutator methods, and does not capture any exceptions they may throw due to malformed
     * values.
     *
     * @param id
     *  The ID of the content to be represented by this DTO; cannot be null
     *
     * @param name
     *  The name of the content to be represented by this DTO
     *
     * @param type
     *  The type of the content to be represented by this DTO
     *
     * @param label
     *  The label of the content to be represented by this DTO
     *
     * @param vendor
     *  The vendor of the content to be represented by this DTO
     */
    public ContentData(String id, String name, String type, String label, String vendor) {
        super();

        this.setId(id);
        this.setName(name);
        this.setType(type);
        this.setLabel(label);
        this.setVendor(vendor);
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
     * @param source
     *  The source entity from which to copy data
     *
     * @throws IllegalArgumentException
     *  if source is null
     */
    public ContentData(Content source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        this.populate(source);
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
     * @param uuid
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
     * @param id
     *  The ID of the content represented by this DTO
     *
     * @throws IllegalArgumentException
     *  if id is null or empty
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setId(String id) {
        if (id == null || id.length() == 0) {
            throw new IllegalArgumentException("id is null or empty");
        }

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
     * @param type
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
     * @param label
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
     * @param name
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
     * @param vendor
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
     * @param contentUrl
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
     * @param requiredTags
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
     * @param releaseVer
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
     * @param gpgUrl
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
    public Long getMetadataExpiration() {
        return this.metadataExpire;
    }

    /**
     * Sets the metadata expiration of the content represented by this DTO.
     *
     * @param metadataExpire
     *  The metadata expiration of the content represented by this DTO, or null to clear the
     *  metadata expiration
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setMetadataExpiration(Long metadataExpire) {
        this.metadataExpire = metadataExpire;
        return this;
    }

    /**
     * Retrieves the collection of IDs representing products that are modified by this content. If
     * the modified product IDs have not yet been defined, this method returns null.
     *
     * @return
     *  the modified product IDs of the content, or null if the modified product IDs have not yet
     *  been defined
     */
    public Collection<String> getModifiedProductIds() {
        return this.modifiedProductIds != null ? new SetView(this.modifiedProductIds) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getRequiredProductIds() {
        return this.getModifiedProductIds();
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
            this.modifiedProductIds = new HashSet<>();
        }

        return this.modifiedProductIds.add(productId);
    }

    /**
     * Removes the specified product ID from the collection of product IDs to be modified by the
     * content represented by this DTO. If the product ID is not modified by this DTO, this method
     * does nothing
     *
     * @param productId
     *  The product ID to remove from the modified product IDs on this DTO
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
     * Sets the modified product IDs for the content represented by this DTO. Any previously
     * existing modified product IDs will be cleared before assigning the given product IDs.
     *
     * @param modifiedProductIds
     *  A collection of product IDs to be modified by the content, or null to clear the
     *  existing modified product IDs
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setModifiedProductIds(Collection<String> modifiedProductIds) {
        if (modifiedProductIds != null) {
            if (this.modifiedProductIds == null) {
                this.modifiedProductIds = new HashSet<>();
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
     * New name for the old setModifiedProductIds method.
     *
     * @param requiredProductIds
     *  A collection of product IDs to be required by the content, or null to clear the
     *  existing required product IDs
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setRequiredProductIds(Collection<String> requiredProductIds) {
        return this.setModifiedProductIds(requiredProductIds);
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
     * @param arches
     *  The arches of the content represented by this DTO, or null to clear the arches
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setArches(String arches) {
        this.arches = arches;
        return this;
    }

    /**
     * Retrieves the lock state of the content represented by this DTO. If the lock state has not
     * yet been defined, this method returns null.
     *
     * @return
     *  the lock state of the content, or null if the lock state has not yet been defined
     */
    @XmlTransient
    public Boolean isLocked() {
        return this.locked;
    }

    /**
     * Sets the lock state of the content represented by this DTO.
     *
     * @param locked
     *  The lock state of the content represented by this DTO, or null to clear the state
     *
     * @return
     *  a reference to this DTO
     */
    public ContentData setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }

    @Override
    public String toString() {
        return String.format("ContentData [id: %s, name: %s, label: %s]", this.id, this.name, this.label);
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
            .append(this.arches, that.arches)
            .append(this.locked, that.locked);

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
            .append(this.arches)
            .append(this.locked);

        return builder.toHashCode();
    }

    @Override
    public Object clone() {
        ContentData copy = (ContentData) super.clone();

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
        this.metadataExpire = source.getMetadataExpiration();
        this.arches = source.getArches();
        this.locked = source.isLocked();

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
        this.releaseVer = source.getReleaseVersion();
        this.gpgUrl = source.getGpgUrl();
        this.metadataExpire = source.getMetadataExpiration();
        this.arches = source.getArches();
        this.locked = source.isLocked();

        this.setModifiedProductIds(source.getModifiedProductIds());

        return this;
    }
}
