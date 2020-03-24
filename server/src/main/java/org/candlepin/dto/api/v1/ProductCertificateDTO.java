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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.TimestampedCandlepinDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * The ProductCertificateDTO is a DTO representing product certificates presented to the API.
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a product certificate")
public class ProductCertificateDTO extends TimestampedCandlepinDTO<ProductCertificateDTO> {

    private static final long serialVersionUID = 1L;

    protected String id;
    protected String key;
    protected String cert;
    private ProductDTO product;

    /**
     * Initializes a new ProductCertificateDTO instance with null values.
     */
    public ProductCertificateDTO() {
        // Intentionally left empty
    }

    /**
     * Initializes a new ProductCertificateDTO instance which is a shallow copy of the provided
     * source entity.
     *
     * @param source
     *  The source entity to copy
     */
    public ProductCertificateDTO(ProductCertificateDTO source) {
        super(source);
    }

    /**
     * Retrieves the id field of this ProductCertificateDTO object.
     *
     * @return the id field of this ProductCertificateDTO object.
     */
    @JsonIgnore
    public String getId() {
        return this.id;
    }

    /**
     * Sets the id to set on this ProductCertificateDTO object.
     *
     * @param id the id to set on this ProductCertificateDTO object.
     *
     * @return a reference to this ProductCertificateDTO object.
     */
    @JsonIgnore
    public ProductCertificateDTO setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Returns the product of this product certificate.
     *
     * @return the owner of this entitlement.
     */
    @JsonIgnore
    public ProductDTO getProduct() {
        return this.product;
    }

    /**
     * Sets the product of this certificate.
     *
     * @param product the product to set.
     *
     * @return a reference to this ProductCertificateDTO object.
     */
    @JsonProperty
    public ProductCertificateDTO setProduct(ProductDTO product) {
        this.product = product;
        return this;
    }

    /**
     * Returns the product of this product certificate.
     *
     * @return the key of this certificate.
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Sets the key of this certificate.
     *
     * @param key the key to set.
     *
     * @return a reference to this ProductCertificateDTO object.
     */
    public ProductCertificateDTO setKey(String key) {
        this.key = key;
        return this;
    }

    public String getCert() {
        return this.cert;
    }

    /**
     * Sets the cert of this certificate.
     *
     * @param cert the cert to set.
     *
     * @return a reference to this ProductCertificateDTO object.
     */
    public ProductCertificateDTO setCert(String cert) {
        this.cert = cert;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        ProductDTO product = this.getProduct();

        return String.format("ProductCertificateDTO [id: %s, key: %s, product id: %s]",
                this.getId(), this.getKey(), product != null ? product.getId() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ProductCertificateDTO && super.equals(obj)) {
            ProductCertificateDTO that = (ProductCertificateDTO) obj;

            EqualsBuilder builder = new EqualsBuilder()
                .append(this.getId(), that.getId())
                .append(this.getKey(), that.getKey())
                .append(this.getCert(), that.getCert())
                .append(this.getProduct() != null ? this.getProduct().getId() : null,
                that.getProduct() != null ? that.getProduct().getId() : null);

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
            .append(this.getId())
            .append(this.getKey())
            .append(this.getCert())
            .append(this.getProduct() != null ? this.getProduct().getId() : null);

        return builder.toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO clone() {
        ProductCertificateDTO copy = super.clone();

        ProductDTO product = this.getProduct();

        if (product == null) {
            copy.product = null;
        }
        else {
            copy.product = new ProductDTO()
                .id(product.getId())
                .attributes(product.getAttributes())
                .productContent(product.getProductContent())
                .dependentProductIds(product.getDependentProductIds())
                .branding(product.getBranding())
                .created(product.getCreated())
                .updated(product.getUpdated());
        }

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO populate(ProductCertificateDTO source) {
        super.populate(source);

        this.setId(source.getId());
        this.setKey(source.getKey());
        this.setCert(source.getCert());
        this.setProduct(source.getProduct());

        return this;
    }
}
