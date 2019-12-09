/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.manifest.v1;

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.service.model.OwnerInfo;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;



/**
 * A DTO representation of the Owner entity as used by the manifest import/export mechanism.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@JsonFilter("OwnerFilter")
public class OwnerDTO extends TimestampedCandlepinDTO<OwnerDTO> implements OwnerInfo {
    public static final long serialVersionUID = 1L;

    protected String id;
    protected String key;
    protected String displayName;
    protected OwnerDTO parentOwner;
    protected String contentPrefix;
    protected String defaultServiceLevel;
    protected UpstreamConsumerDTO upstreamConsumer;
    protected String logLevel;
    protected Boolean autobindDisabled;
    protected Boolean autobindHypervisorDisabled;
    protected String contentAccessMode;
    protected String contentAccessModeList;
    protected Date lastRefreshed;

    /**
     * Initializes a new OwnerDTO instance with null values.
     */
    public OwnerDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new OwnerDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public OwnerDTO(OwnerDTO source) {
        super(source);
    }

    @HateoasInclude
    @JsonProperty
    public String getId() {
        return this.id;
    }

    @JsonIgnore
    public OwnerDTO setId(String id) {
        this.id = id;
        return this;
    }

    @HateoasInclude
    public String getKey() {
        return key;
    }

    public OwnerDTO setKey(String key) {
        this.key = key;
        return this;
    }

    @HateoasInclude
    public String getDisplayName() {
        return displayName;
    }

    public OwnerDTO setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @JsonProperty
    public OwnerDTO getParentOwner() {
        return this.parentOwner;
    }

    @JsonIgnore
    public OwnerDTO setParentOwner(OwnerDTO parentOwner) {
        this.parentOwner = parentOwner;
        return this;
    }

    public String getContentPrefix() {
        return contentPrefix;
    }

    public OwnerDTO setContentPrefix(String contentPrefix) {
        this.contentPrefix = contentPrefix;
        return this;
    }

    public String getDefaultServiceLevel() {
        return defaultServiceLevel;
    }

    public OwnerDTO setDefaultServiceLevel(String defaultServiceLevel) {
        this.defaultServiceLevel = defaultServiceLevel;
        return this;
    }

    @JsonProperty
    public UpstreamConsumerDTO getUpstreamConsumer() {
        return upstreamConsumer;
    }

    @JsonIgnore
    public OwnerDTO setUpstreamConsumer(UpstreamConsumerDTO upstreamConsumer) {
        this.upstreamConsumer = upstreamConsumer;
        return this;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public OwnerDTO setLogLevel(String logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    @JsonProperty
    public Boolean isAutobindDisabled() {
        return autobindDisabled;
    }

    @JsonIgnore
    public OwnerDTO setAutobindDisabled(Boolean autobindDisabled) {
        this.autobindDisabled = autobindDisabled;
        return this;
    }

    @JsonProperty
    public Boolean isAutobindHypervisorDisabled() {
        return autobindHypervisorDisabled;
    }

    @JsonIgnore
    public OwnerDTO setAutobindHypervisorDisabled(Boolean autobindHypervisorDisabled) {
        this.autobindHypervisorDisabled = autobindHypervisorDisabled;
        return this;
    }

    public String getContentAccessMode() {
        return contentAccessMode;
    }

    public OwnerDTO setContentAccessMode(String contentAccessMode) {
        this.contentAccessMode = contentAccessMode;
        return this;
    }

    public String getContentAccessModeList() {
        return contentAccessModeList;
    }

    public OwnerDTO setContentAccessModeList(String contentAccessModeList) {
        this.contentAccessModeList = contentAccessModeList;
        return this;
    }

    /**
     * Fetches the date this owner was last refreshed, or null if the owner has not been refreshed
     * or this field has not been set.
     *
     * @return
     *  the date this owner was last refreshed, or null if the owner has not been refreshed or this
     *  field is not defined
     */
    @JsonProperty
    public Date getLastRefreshed() {
        return lastRefreshed;
    }

    /**
     * Sets or clears the date this owner was last refreshed.
     *
     * @param lastRefreshed
     *  The date this owner was last refreshed, or null to clear the last refresh value
     *
     * @return
     *  A reference to this DTO
     */
    @JsonIgnore
    public OwnerDTO setLastRefreshed(Date lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("OwnerDTO [id: %s, key: %s]", this.getId(), this.getKey());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof OwnerDTO && super.equals(obj)) {
            OwnerDTO that = (OwnerDTO) obj;

            // Pull the parent owner IDs, as we're not interested in verifying that the parent owners
            // themselves are equal; just so long as they point to the same parent owner.
            String lpoid = this.getParentOwner() != null ? this.getParentOwner().getId() : null;
            String rpoid = that.getParentOwner() != null ? that.getParentOwner().getId() : null;

            // Same with the upstream consumer
            String lucid = this.getUpstreamConsumer() != null ? this.getUpstreamConsumer().getId() : null;
            String rucid = that.getUpstreamConsumer() != null ? that.getUpstreamConsumer().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getKey(), that.getKey())
                .append(this.getDisplayName(), that.getDisplayName())
                .append(lpoid, rpoid)
                .append(this.getContentPrefix(), that.getContentPrefix())
                .append(this.getDefaultServiceLevel(), that.getDefaultServiceLevel())
                .append(lucid, rucid)
                .append(this.getLogLevel(), that.getLogLevel())
                .append(this.isAutobindDisabled(), that.isAutobindDisabled())
                .append(this.isAutobindHypervisorDisabled(), that.isAutobindHypervisorDisabled())
                .append(this.getContentAccessMode(), that.getContentAccessMode())
                .append(this.getContentAccessModeList(), that.getContentAccessModeList())
                .append(this.getLastRefreshed(), that.getLastRefreshed());

            return builder.isEquals();
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        // Like with the equals method, we are not interested in hashing nested objects; we're only
        // concerned with the reference to such an object.

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getKey())
            .append(this.getDisplayName())
            .append(this.getParentOwner() != null ? this.getParentOwner().getId() : null)
            .append(this.getContentPrefix())
            .append(this.getDefaultServiceLevel())
            .append(this.getUpstreamConsumer() != null ? this.getUpstreamConsumer().getId() : null)
            .append(this.getLogLevel())
            .append(this.isAutobindDisabled())
            .append(this.isAutobindHypervisorDisabled())
            .append(this.getContentAccessMode())
            .append(this.getContentAccessModeList())
            .append(this.getLastRefreshed());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO clone() {
        OwnerDTO copy = super.clone();

        OwnerDTO parent = this.getParentOwner();
        copy.setParentOwner(parent != null ? parent.clone() : null);

        UpstreamConsumerDTO consumer = this.getUpstreamConsumer();
        copy.setUpstreamConsumer(consumer != null ? consumer.clone() : null);

        Date lastRefreshed = this.getLastRefreshed();
        copy.setLastRefreshed(lastRefreshed != null ? (Date) lastRefreshed.clone() : null);

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OwnerDTO populate(OwnerDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setKey(source.getKey());
        this.setDisplayName(source.getDisplayName());
        this.setParentOwner(source.getParentOwner());
        this.setContentPrefix(source.getContentPrefix());
        this.setDefaultServiceLevel(source.getDefaultServiceLevel());
        this.setUpstreamConsumer(source.getUpstreamConsumer());
        this.setLogLevel(source.getLogLevel());
        this.setAutobindDisabled(source.isAutobindDisabled());
        this.setAutobindHypervisorDisabled(source.isAutobindHypervisorDisabled());
        this.setContentAccessMode(source.getContentAccessMode());
        this.setContentAccessModeList(source.getContentAccessModeList());
        this.setLastRefreshed(source.getLastRefreshed());

        return this;
    }
}
