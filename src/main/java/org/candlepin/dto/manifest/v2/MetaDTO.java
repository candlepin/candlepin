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

import org.candlepin.dto.CandlepinDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Date;

/**
 * Mutable meta data for version 2 manifests
 */
public class MetaDTO extends CandlepinDTO<MetaDTO> {

    public static final long serialVersionUID = 1L;

    private static final String MANIFEST_VERSION = "2.0.0";
    private String version;
    private Date created;
    private String principalName;
    private String webAppPrefix;
    private String cdnLabel;

    /**
     * Initializes an empty MetaDTO
     */
    public MetaDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public MetaDTO(MetaDTO source) {
        super(source);
    }

    /**
     * Retrieves the manifest format version
     *
     * @return the fixed version {@code 2.0.0}
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getManifestVersion() {
        return MANIFEST_VERSION;
    }

    /**
     * Retrieves the Candlepin version that produced the manifest
     *
     * @return the Candlepin version, or null if unset
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * Sets the Candlepin version that produced the manifest
     *
     * @param version
     *     the Candlepin version, or null to clear it
     * @return this DTO
     */
    public MetaDTO setVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Retrieves the created
     *
     * @return the created, or null if unset
     */
    public Date getCreated() {
        return this.created;
    }

    /**
     * Sets the created
     *
     * @param created
     *     the created, or null to clear it
     * @return this DTO
     */
    public MetaDTO setCreated(Date created) {
        this.created = created;
        return this;
    }

    /**
     * Retrieves the principal name
     *
     * @return the principal name, or null if unset
     */
    public String getPrincipalName() {
        return this.principalName;
    }

    /**
     * Sets the principal name
     *
     * @param principalName
     *     the principal name, or null to clear it
     * @return this DTO
     */
    public MetaDTO setPrincipalName(String principalName) {
        this.principalName = principalName;
        return this;
    }

    /**
     * Retrieves the web app prefix
     *
     * @return the web app prefix, or null if unset
     */
    public String getWebAppPrefix() {
        return this.webAppPrefix;
    }

    /**
     * Sets the web app prefix
     *
     * @param webAppPrefix
     *     the web app prefix, or null to clear it
     * @return this DTO
     */
    public MetaDTO setWebAppPrefix(String webAppPrefix) {
        this.webAppPrefix = webAppPrefix;
        return this;
    }

    /**
     * Retrieves the cdn label
     *
     * @return the cdn label, or null if unset
     */
    public String getCdnLabel() {
        return this.cdnLabel;
    }

    /**
     * Sets the cdn label
     *
     * @param cdnLabel
     *     the cdn label, or null to clear it
     * @return this DTO
     */
    public MetaDTO setCdnLabel(String cdnLabel) {
        this.cdnLabel = cdnLabel;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof MetaDTO that)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getVersion(), that.getVersion())
            .append(this.getCreated(), that.getCreated())
            .append(this.getPrincipalName(), that.getPrincipalName())
            .append(this.getWebAppPrefix(), that.getWebAppPrefix())
            .append(this.getCdnLabel(), that.getCdnLabel())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(this.getVersion())
            .append(this.getCreated())
            .append(this.getPrincipalName())
            .append(this.getWebAppPrefix())
            .append(this.getCdnLabel())
            .toHashCode();
    }

    @Override
    public MetaDTO populate(MetaDTO source) {
        super.populate(source);

        this.setVersion(source.getVersion());
        this.setCreated(source.getCreated());
        this.setPrincipalName(source.getPrincipalName());
        this.setWebAppPrefix(source.getWebAppPrefix());
        this.setCdnLabel(source.getCdnLabel());

        return this;
    }

    @Override
    public MetaDTO clone() {
        MetaDTO copy = super.clone();
        copy.setCreated(this.created != null ? (Date) this.created.clone() : null);
        return copy;
    }
}
