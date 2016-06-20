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
package org.candlepin.model;

import org.candlepin.model.dto.ProductContentData;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import org.hibernate.annotations.Parent;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;



/**
 * ProductContent
 */
@Embeddable
public class ProductContent extends AbstractHibernateObject {

    @Parent
    @NotNull
    private Product product;

    @ManyToOne
    @JoinColumn(name = "content_uuid", nullable = false)
    @NotNull
    private Content content;

    private Boolean enabled;

    public ProductContent() {
        // Intentionally left empty
    }

    public ProductContent(Product product, Content content, Boolean enabled) {
        this.setContent(content);
        this.setProduct(product);
        this.setEnabled(enabled);
    }

    @Override
    @XmlTransient
    public Serializable getId() {
        return null; // return new ProductContentKey(this.productUuid, this.contentUuid);
    }

    /**
     * @param content the content to set
     */
    public void setContent(Content content) {
        this.content = content;
    }

    /**
     * @return the content
     */
    public Content getContent() {
        return content;
    }

    /**
     * @param product the product to set
     */
    public void setProduct(Product product) {
        this.product = product;
    }

    /**
     * @return the product
     */
    @XmlTransient
    public Product getProduct() {
        return product;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the enabled
     */
    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof ProductContent) {
            ProductContent that = (ProductContent) obj;

            String thisProductUuid = this.product != null ? this.product.getUuid() : null;
            String thisContentUuid = this.content != null ? this.content.getUuid() : null;
            String thatProductUuid = that.product != null ? that.product.getUuid() : null;
            String thatContentUuid = that.content != null ? that.content.getUuid() : null;

            return new EqualsBuilder()
                .append(thisProductUuid, thatProductUuid)
                .append(thisContentUuid, thatContentUuid)
                .append(this.enabled, that.enabled)
                .isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(3, 23)
            .append(this.product != null ? this.product.getUuid() : null)
            .append(this.content != null ? this.content.getUuid() : null)
            .append(this.enabled)
            .toHashCode();
    }

    /**
     * Determines whether or not this entity would be changed if the given DTO were applied to this
     * object.
     *
     * @param dto
     *  The product content DTO to check for changes
     *
     * @throws IllegalArgumentException
     *  if dto is null
     *
     * @return
     *  true if this product content would be changed by the given DTO; false otherwise
     */
    public boolean isChangedBy(ProductContentData dto) {
        if (dto == null) {
            throw new IllegalArgumentException("dto is null");
        }

        if (dto.isEnabled() != null && dto.isEnabled().equals(this.enabled)) {
            return true;
        }

        if (dto.getContent() != null) {
            if (this.content == null || this.content.isChangedBy(dto.getContent())) {
                return true;
            }
        }

        // Impl note:
        // Product content DTOs do not contain product information

        return false;
    }

    /**
     * Returns a DTO representing this entity.
     *
     * @return
     *  a DTO representing this entity
     */
    public ProductContentData toDTO() {
        return new ProductContentData(this);
    }

    public String toString() {
        return String.format(
            "ProductContent [product = %s, content = %s, enabled = %s]",
            this.getProduct(), this.getContent(), this.isEnabled()
        );
    }
}
