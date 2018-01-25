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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * A DTO representation of the Branding entity used internally by
 * other DTO entities, like PoolDTO and SubscriptionDTO.
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
public class BrandingDTO extends TimestampedCandlepinDTO<BrandingDTO> {

    public static final long serialVersionUID = 1L;

    private String productId;
    private String name;
    private String type;

    /**
     * Initializes a new BrandingDTO instance with null values.
     */
    public BrandingDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new BrandingDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public BrandingDTO(BrandingDTO source) {
        super(source);
    }

    /**
     * Initializes a new BrandingDTO instance based on the given values.
     *
     * @param productId this branding's product id.
     *
     * @param name this branding's name.
     *
     * @param type this branding's type.
     */
    @JsonCreator
    public BrandingDTO(
        @JsonProperty("productId") String productId,
        @JsonProperty("name") String name,
        @JsonProperty("type") String type) {

        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId is null or empty");
        }

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name is null or empty");
        }

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type is null or empty");
        }

        this.productId = productId;
        this.name = name;
        this.type = type;
    }

    /**
     * Returns this branding's product id.
     *
     * @return this branding's product id.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Sets this branding's product id.
     *
     * @param productId the product id to set on this branding DTO.
     *
     * @return a reference to this branding DTO object.
     */
    public BrandingDTO setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    /**
     * Returns this branding's name.
     *
     * @return this branding's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets this branding's name.
     *
     * @param name the name to set on this branding DTO.
     *
     * @return a reference to this branding DTO object.
     */
    public BrandingDTO setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Returns this branding's type.
     *
     * @return this branding's type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets this branding's type.
     *
     * @param type the type to set on this branding DTO.
     *
     * @return a reference to this branding DTO object.
     */
    public BrandingDTO setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("BrandingDTO [productId: %s, name: %s, type: %s]",
                this.getProductId(), this.getName(), this.getType());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof BrandingDTO && super.equals(obj)) {
            BrandingDTO that = (BrandingDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getProductId(), that.getProductId())
                .append(this.getName(), that.getName())
                .append(this.getType(), that.getType());

            return builder.isEquals();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(37, 7)
            .append(super.hashCode())
            .append(this.getProductId())
            .append(this.getName())
            .append(this.getType());

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO clone() {
        // Nothing to copy here. All fields are immutable types.

        return super.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BrandingDTO populate(BrandingDTO source) {
        super.populate(source);

        this.setProductId(source.getProductId());
        this.setName(source.getName());
        this.setType(source.getType());

        return this;
    }
}
