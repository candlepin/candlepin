/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.service.model.ProductContentInfo;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;



/**
 * ProductContent
 */
@Entity
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Table(name = ProductContent.DB_TABLE)
public class ProductContent extends AbstractHibernateObject<ProductContent> implements ProductContentInfo {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_product_contents";

    /** The default state of the enabled flag */
    public static final Boolean DEFAULT_ENABLED_STATE = Boolean.FALSE;

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    private Product product;

    @ManyToOne
    @JoinColumn(name = "content_uuid", nullable = false)
    @NotNull
    private Content content;

    private boolean enabled;

    /**
     * Zero-arg constructor for Hibernate. Do not use.
     */
    ProductContent() {
        // Intentionally left empty
    }

    /**
     * Creates a new ProductContent instance representing a join between the product and content.
     *
     * @param product
     *  the parent product to which the content is to be attached
     *
     * @param content
     *  the content to join to the product
     *
     * @param enabled
     *  whether or not the content should be enabled or disabled
     */
    public ProductContent(Product product, Content content, boolean enabled) {
        this.product = Objects.requireNonNull(product);
        this.content = Objects.requireNonNull(content);
        this.enabled = enabled;
    }

    /**
     * Creates a new ProductContent instance representing a content and its enabled state. Instances
     * created in this way cannot be persisted in the database, and must be recreated with a proper
     * product reference. Such instances are intended to be used to capture the content for
     * attaching several contents to a given product via .setProductContent.
     *
     * @param content
     *  the content to eventually attach to a product
     *
     * @param enabled
     *  whether or not the content should be enabled or disabled
     */
    public ProductContent(Content content, boolean enabled) {
        this.product = null;
        this.content = Objects.requireNonNull(content);
        this.enabled = enabled;
    }

    @Override
    public Serializable getId() {
        return this.id;
    }

    /**
     * @return the content
     */
    @Override
    public Content getContent() {
        return this.content;
    }

    public String getContentId() {
        return this.content.getId();
    }

    /**
     * @return the product
     */
    public Product getProduct() {
        return this.product;
    }

    public String getProductId() {
        return this.product != null ? this.product.getId() : null;
    }

    /**
     * @return the enabled
     */
    @Override
    public Boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        return obj instanceof ProductContent that ?
            this.getContentId().equals(that.getContentId()) :
            false;
    }

    @Override
    public int hashCode() {
        return this.getContentId()
            .hashCode();
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
        return String.format("ProductContent [id: %s, product = %s, content = %s, enabled = %s]",
            this.getId(), this.getProduct(), this.getContent(), this.isEnabled());
    }
}
