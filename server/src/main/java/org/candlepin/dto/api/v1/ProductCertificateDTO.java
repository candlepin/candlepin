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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.candlepin.dto.TimestampedCandlepinDTO;

/**
 * The ProductCertificateDTO is a DTO representing product certificates presented to the API.
 */
@ApiModel(parent = TimestampedCandlepinDTO.class, description = "DTO representing a product certificate")
public class ProductCertificateDTO extends AbstractCertificateDTO<ProductCertificateDTO> {

    private static final long serialVersionUID = 1L;

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
     * Get CertificateSerialDTO - Always returns null
     *
     * @return null
     */
    @Override
    @JsonIgnore
    public CertificateSerialDTO getSerial() {
        return null;
    }

    /**
     *
     * @param serial the serial cert to set.
     * @return a reference to this ProductCertificateDTO object.
     */
    @Override
    @JsonProperty
    public ProductCertificateDTO setSerial(CertificateSerialDTO serial) {
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
        copy.product = product != null ? product.clone() : null;

        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProductCertificateDTO populate(ProductCertificateDTO source) {
        super.populate(source);

        this.setProduct(source.getProduct());

        return this;
    }
}
