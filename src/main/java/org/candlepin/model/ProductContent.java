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

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.Parent;

/**
 * ProductContent
 */
@Embeddable
public class ProductContent extends AbstractHibernateObject {

    @Parent
    @NotNull
    private Product product;

    @ManyToOne
    @JoinColumn(name = "content_id", nullable = false, updatable = false)
    @Index(name = "cp_prodcont_cont_fk_idx")
    @NotNull
    private Content content;

    private Boolean enabled;

    public ProductContent() {

    }

    public ProductContent(Product product, Content content, Boolean enabled) {
        this.setContent(content);
        this.setProduct(product);
        this.setEnabled(enabled);
    }

    public String toString() {
        return "[ProductContent, product = " + product.getName() +
                ", content = " + content.getLabel() +
                ", enabled = " + enabled + "]";
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
    public Boolean getEnabled() {
        return enabled;
    }

    @Override
    public int hashCode() {
        // TODO: Should we include the product here?
        return new HashCodeBuilder(3, 23).append(this.enabled)
            .append(this.content.hashCode()).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ProductContent) {
            ProductContent that = (ProductContent) other;
            return new EqualsBuilder().append(this.enabled, that.enabled)
                .isEquals() && this.content.equals(that.content);
        }
        return false;
    }

}
