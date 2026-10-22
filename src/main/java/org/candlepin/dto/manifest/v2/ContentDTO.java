/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import org.candlepin.dto.TimestampedCandlepinDTO;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Mutable content data for version 2 manifests
 */
public class ContentDTO extends TimestampedCandlepinDTO<ContentDTO> {

    public static final long serialVersionUID = 1L;

    private boolean enabled;
    private String id;
    private String type;
    private String label;
    private String name;
    private String vendor;
    private String contentUrl;
    private String requiredTags;
    private String releaseVer;
    private String gpgUrl;
    private String arches;

    /**
     * Initializes an empty ContentDTO
     */
    public ContentDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public ContentDTO(ContentDTO source) {
        super(source);
    }

    /**
     * Retrieves whether the content is enabled for its product
     *
     * @return true if enabled, otherwise false
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Sets whether the content is enabled for its product
     *
     * @param enabled
     *     whether the content is enabled
     * @return this DTO
     */
    public ContentDTO setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Retrieves the id
     *
     * @return the id, or null if unset
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id
     *
     * @param id
     *     the id, or null to clear it
     * @return this DTO
     */
    public ContentDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the type
     *
     * @return the type, or null if unset
     */
    public String getType() {
        return this.type;
    }

    /**
     * Sets the type
     *
     * @param type
     *     the type, or null to clear it
     * @return this DTO
     */
    public ContentDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Retrieves the label
     *
     * @return the label, or null if unset
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the label
     *
     * @param label
     *     the label, or null to clear it
     * @return this DTO
     */
    public ContentDTO setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Retrieves the name
     *
     * @return the name, or null if unset
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name
     *
     * @param name
     *     the name, or null to clear it
     * @return this DTO
     */
    public ContentDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the vendor
     *
     * @return the vendor, or null if unset
     */
    public String getVendor() {
        return this.vendor;
    }

    /**
     * Sets the vendor
     *
     * @param vendor
     *     the vendor, or null to clear it
     * @return this DTO
     */
    public ContentDTO setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    /**
     * Retrieves the content url
     *
     * @return the content url, or null if unset
     */
    public String getContentUrl() {
        return this.contentUrl;
    }

    /**
     * Sets the content url
     *
     * @param contentUrl
     *     the content url, or null to clear it
     * @return this DTO
     */
    public ContentDTO setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
        return this;
    }

    /**
     * Retrieves the required tags
     *
     * @return the required tags, or null if unset
     */
    public String getRequiredTags() {
        return this.requiredTags;
    }

    /**
     * Sets the required tags
     *
     * @param requiredTags
     *     the required tags, or null to clear it
     * @return this DTO
     */
    public ContentDTO setRequiredTags(String requiredTags) {
        this.requiredTags = requiredTags;
        return this;
    }

    /**
     * Retrieves the release ver
     *
     * @return the release ver, or null if unset
     */
    public String getReleaseVer() {
        return this.releaseVer;
    }

    /**
     * Sets the release ver
     *
     * @param releaseVer
     *     the release ver, or null to clear it
     * @return this DTO
     */
    public ContentDTO setReleaseVer(String releaseVer) {
        this.releaseVer = releaseVer;
        return this;
    }

    /**
     * Retrieves the gpg url
     *
     * @return the gpg url, or null if unset
     */
    public String getGpgUrl() {
        return this.gpgUrl;
    }

    /**
     * Sets the gpg url
     *
     * @param gpgUrl
     *     the gpg url, or null to clear it
     * @return this DTO
     */
    public ContentDTO setGpgUrl(String gpgUrl) {
        this.gpgUrl = gpgUrl;
        return this;
    }

    /**
     * Retrieves the arches
     *
     * @return the arches, or null if unset
     */
    public String getArches() {
        return this.arches;
    }

    /**
     * Sets the arches
     *
     * @param arches
     *     the arches, or null to clear it
     * @return this DTO
     */
    public ContentDTO setArches(String arches) {
        this.arches = arches;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof ContentDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.isEnabled(), that.isEnabled())
            .append(this.getId(), that.getId())
            .append(this.getType(), that.getType())
            .append(this.getLabel(), that.getLabel())
            .append(this.getName(), that.getName())
            .append(this.getVendor(), that.getVendor())
            .append(this.getContentUrl(), that.getContentUrl())
            .append(this.getRequiredTags(), that.getRequiredTags())
            .append(this.getReleaseVer(), that.getReleaseVer())
            .append(this.getGpgUrl(), that.getGpgUrl())
            .append(this.getArches(), that.getArches())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.isEnabled())
            .append(this.getId())
            .append(this.getType())
            .append(this.getLabel())
            .append(this.getName())
            .append(this.getVendor())
            .append(this.getContentUrl())
            .append(this.getRequiredTags())
            .append(this.getReleaseVer())
            .append(this.getGpgUrl())
            .append(this.getArches())
            .toHashCode();
    }

    @Override
    public ContentDTO populate(ContentDTO source) {
        super.populate(source);

        this.setEnabled(source.isEnabled());
        this.setId(source.getId());
        this.setType(source.getType());
        this.setLabel(source.getLabel());
        this.setName(source.getName());
        this.setVendor(source.getVendor());
        this.setContentUrl(source.getContentUrl());
        this.setRequiredTags(source.getRequiredTags());
        this.setReleaseVer(source.getReleaseVer());
        this.setGpgUrl(source.getGpgUrl());
        this.setArches(source.getArches());

        return this;
    }
}
