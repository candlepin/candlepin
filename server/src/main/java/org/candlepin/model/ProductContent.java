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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;



/**
 * ProductContent
 */
@Entity
@Table(name = "cp2_product_content")
public class ProductContent extends AbstractHibernateObject {

    @Id
    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false, updatable = false)
    @NotNull
    private Product product;

    @Id
    @ManyToOne
    @JoinColumn(name = "content_uuid", nullable = false, updatable = false)
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

    public String toString() {
        return String.format(
            "ProductContent [product = %s, content = %s, enabled = %s]",
            this.getProduct(), this.getContent(), this.isEnabled()
        );
    }

    @XmlTransient
    public Serializable getId() {
        // TODO: just here to appease AbstractHibernateObject
        return null;
    }

    public void setId(String s) {
        // TODO: just here to appease jackson
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
    public int hashCode() {
        return new HashCodeBuilder(3, 23)
            // .append(this.product != null ? this.product.getUuid() : null)
            // .append(this.content != null ? this.content.getUuid() : null)
            .append(this.enabled)
            .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof ProductContent) {
            ProductContent that = (ProductContent) other;

            String thisProductUuid = this.product != null ? this.product.getUuid() : null;
            String thisContentUuid = this.content != null ? this.content.getUuid() : null;
            String thatProductUuid = that.product != null ? that.product.getUuid() : null;
            String thatContentUuid = that.content != null ? that.content.getUuid() : null;

            return new EqualsBuilder()
                // .append(thisProductUuid, thatProductUuid)
                // .append(thisContentUuid, thatContentUuid)
                .append(this.enabled, that.enabled)
                .isEquals();
        }

        return false;
    }

}
