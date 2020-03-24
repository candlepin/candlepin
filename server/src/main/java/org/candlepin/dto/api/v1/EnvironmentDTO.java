/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.Util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A DTO representation of the Environment entity
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing an environment")
public class EnvironmentDTO extends TimestampedCandlepinDTO<EnvironmentDTO> {
    public static final long serialVersionUID = 1L;

    /**
     * Join object DTO for joining products to content
     */
    public static class EnvironmentContentDTO extends CandlepinDTO<EnvironmentContentDTO> {
        protected final ContentDTO content;
        protected Boolean enabled;

        @JsonCreator
        public EnvironmentContentDTO(
            @JsonProperty("content") ContentDTO content,
            @JsonProperty("enabled") Boolean enabled) {

            if (content == null || (content.getUuid() == null && content.getId() == null)) {
                throw new IllegalArgumentException("content is null or is missing an identifier");
            }

            this.content = content;
            this.setEnabled(enabled);
        }

        public EnvironmentContentDTO(ContentDTO content) {
            this(content, null);
        }

        public ContentDTO getContent() {
            return this.content;
        }

        public EnvironmentContentDTO setEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Boolean isEnabled() {
            return this.enabled;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (obj instanceof EnvironmentContentDTO) {
                EnvironmentContentDTO that = (EnvironmentContentDTO) obj;

                EqualsBuilder builder = new EqualsBuilder()
                    .append(this.getContent().getUuid(), that.getContent().getUuid())
                    .append(this.isEnabled(), that.isEnabled());

                return builder.isEquals();
            }

            return false;
        }

        @Override
        public int hashCode() {
            HashCodeBuilder builder = new HashCodeBuilder(37, 7)
                .append(this.getContent().getUuid())
                .append(this.isEnabled());

            return builder.toHashCode();
        }
    }

    protected String id;
    protected String name;
    protected String description;
    protected NestedOwnerDTO owner;
    protected Map<String, EnvironmentContentDTO> environmentContent;

    /**
     * Initializes a new EnvironmentDTO instance with null values.
     */
    public EnvironmentDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new EnvironmentDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public EnvironmentDTO(EnvironmentDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this EnvironmentDTO object.
     *
     * @return the id field of this EnvironmentDTO object.
     */
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this EnvironmentDTO object.
     *
     * @param id the id to set on this EnvironmentDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EnvironmentDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the id field of this EnvironmentDTO object.
     *
     * @return the name field of this EnvironmentDTO object.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name to set on this EnvironmentDTO object.
     *
     * @param name the name to set on this EnvironmentDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EnvironmentDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Retrieves the description field of this EnvironmentDTO object.
     *
     * @return the description field of this EnvironmentDTO object.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description to set on this EnvironmentDTO object.
     *
     * @param description the description to set on this EnvironmentDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EnvironmentDTO setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Retrieves the owner field of this EnvironmentDTO object.
     *
     * @return the owner field of this EnvironmentDTO object.
     */
    public NestedOwnerDTO getOwner() {
        return owner;
    }

    /**
     * Sets the owner to set on this EnvironmentDTO object.
     *
     * @param owner the owner to set on this EnvironmentDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EnvironmentDTO setOwner(NestedOwnerDTO owner) {
        this.owner = owner;
        return this;
    }

    /**
     * Retrieves the environmentContent field of this EnvironmentDTO object.
     *
     * @return the environmentContent field of this EnvironmentDTO object.
     */
    public Collection<EnvironmentContentDTO> getEnvironmentContent() {
        return this.environmentContent != null ?
            this.environmentContent.values() : null;
    }

    /**
     * Sets the environmentContent to set on this EnvironmentDTO object.
     *
     * @param environmentContent the environementContent to set on this EnvironmentDTO object.
     *
     * @return a reference to this DTO object.
     */
    public EnvironmentDTO setEnvironmentContent(Collection<EnvironmentContentDTO> environmentContent) {
        if (environmentContent != null) {
            if (this.environmentContent == null) {
                this.environmentContent = new HashMap<>();
            }
            else {
                this.environmentContent.clear();
            }

            for (EnvironmentContentDTO dto : environmentContent) {
                if (dto == null || dto.getContent() == null ||
                    StringUtils.isEmpty(dto.getContent().getId())) {
                    throw new IllegalArgumentException("environment content is null or incomplete");
                }

                this.environmentContent.put(dto.getContent().getId(), dto);
            }
        }
        else {
            this.environmentContent = null;
        }
        return this;
    }

    /**
     * Adds the given content to this environment DTO. If a matching content has already been added to
     * this environment, it will be overwritten by the specified content.
     *
     * @param dto
     *  The environment content DTO to add to this environment
     *
     * @throws IllegalArgumentException
     *  if content is null or incomplete
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addEnvironmentContent(EnvironmentContentDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.getContent() == null || dto.getContent().getId() == null) {
            throw new IllegalArgumentException("dto references incomplete content");
        }

        boolean changed = false;
        boolean matched = false;
        String contentId = dto.getContent().getId();

        if (this.environmentContent == null) {
            this.environmentContent = new HashMap<>();
            changed = true;
        }
        else {
            EnvironmentContentDTO existing = this.environmentContent.get(dto.getContent().getId());
            changed = !dto.equals(existing);
        }

        if (changed) {
            this.environmentContent.put(dto.getContent().getId(), dto);
        }

        return changed;
    }

    /**
     * Adds the given content to this DTO. If a matching content has already been added to
     * this environment, it will be overwritten by the specified content.
     *
     * @param dto
     *  The product content DTO to add to this product
     *
     * @throws IllegalArgumentException
     *  if content is null
     *
     * @return
     *  true if adding the content resulted in a change to this product; false otherwise
     */
    public boolean addContent(ContentDTO dto, Boolean enabled) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        return this.addEnvironmentContent(new EnvironmentContentDTO(dto, enabled));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("EnvironmentDTO [id: %s, name: %s]",
            this.getId(), this.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof EnvironmentDTO && super.equals(obj)) {
            EnvironmentDTO that = (EnvironmentDTO) obj;

            String thisOid = this.getOwner() != null ? this.getOwner().getId() : null;
            String thatOid = that.getOwner() != null ? that.getOwner().getId() : null;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getName(), that.getName())
                .append(this.getDescription(), that.getDescription())
                .append(thisOid, thatOid);

            return builder.isEquals() &&
                Util.collectionsAreEqual(this.getEnvironmentContent(), that.getEnvironmentContent());
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        String thisOid = this.getOwner() != null ? this.getOwner().getId() : null;

        // Map.values doesn't properly implement .hashCode, so we need to manually calculate
        // this ourselves using the algorithm defined by list.hashCode()
        int ecHashCode = 0;
        Collection<EnvironmentContentDTO> environmentContent = this.getEnvironmentContent();

        if (environmentContent != null) {
            for (EnvironmentContentDTO dto : environmentContent) {
                ecHashCode = 31 * ecHashCode + (dto != null ? dto.hashCode() : 0);
            }
        }

        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getName())
            .append(this.getDescription())
            .append(thisOid)
            .append(ecHashCode);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO clone() {
        EnvironmentDTO copy = super.clone();

        NestedOwnerDTO owner = this.getOwner();
        copy.setOwner(owner != null ? new NestedOwnerDTO()
            .id(owner.getId())
            .displayName(owner.getDisplayName())
            .href(owner.getHref())
            .key(owner.getKey()) : null);

        copy.setEnvironmentContent(this.getEnvironmentContent());

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EnvironmentDTO populate(EnvironmentDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setName(source.getName());
        this.setDescription(source.getDescription());
        this.setOwner(source.getOwner());
        this.setEnvironmentContent(source.getEnvironmentContent());

        return this;
    }
}
