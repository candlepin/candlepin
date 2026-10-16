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
 * Mutable branding data for version 2 manifests
 */
public class BrandingDTO extends TimestampedCandlepinDTO<BrandingDTO> {

    public static final long serialVersionUID = 1L;

    private String id;
    private String productId;
    private String name;
    private String type;

    /**
     * Initializes an empty BrandingDTO
     */
    public BrandingDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a shallow copy of the source DTO
     *
     * @param source
     *     the DTO to copy
     */
    public BrandingDTO(BrandingDTO source) {
        super(source);
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
    public BrandingDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Retrieves the product id
     *
     * @return the product id, or null if unset
     */
    public String getProductId() {
        return this.productId;
    }

    /**
     * Sets the product id
     *
     * @param productId
     *     the product id, or null to clear it
     * @return this DTO
     */
    public BrandingDTO setProductId(String productId) {
        this.productId = productId;
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
    public BrandingDTO setName(String name) {
        this.name = name;
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
    public BrandingDTO setType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof BrandingDTO that) || !super.equals(obj)) {
            return false;
        }

        return new EqualsBuilder()
            .append(this.getId(), that.getId())
            .append(this.getProductId(), that.getProductId())
            .append(this.getName(), that.getName())
            .append(this.getType(), that.getType())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getId())
            .append(this.getProductId())
            .append(this.getName())
            .append(this.getType())
            .toHashCode();
    }

    @Override
    public BrandingDTO populate(BrandingDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setProductId(source.getProductId());
        this.setName(source.getName());
        this.setType(source.getType());

        return this;
    }
}
