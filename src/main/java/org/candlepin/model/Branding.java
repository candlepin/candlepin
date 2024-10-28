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

import org.candlepin.service.model.BrandingInfo;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Immutable;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * Brand mapping is carried on subscription data and passed to clients through entitlement
 * certificates. It indicates that a particular engineering product ID is being rebranded
 * by the entitlement to the given name. The type is used by clients to determine what
 * action to take with the brand name.
 *
 * NOTE: Presently only type "OS" is supported client side.
 */
@Entity
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Table(name = Branding.DB_TABLE)
public class Branding extends AbstractHibernateObject<Branding> implements BrandingInfo, Cloneable {

    /** Name of the table backing this object in the database */
    public static final String DB_TABLE = "cp_product_branding";

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 37)
    @NotNull
    private String id;

    @Column(name = "product_id", nullable = false)
    @NotNull
    @Size(max = 255)
    private String productId;

    @Column(nullable = false)
    @NotNull
    @Size(max = 255)
    private String name;

    @Column(nullable = false)
    @NotNull
    @Size(max = 32)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_uuid")
    private Product product;

    /**
     * Zero-arg constructor for Hibernate. Do not use.
     */
    Branding() {
        // Intentionally left empty
    }

    public Branding(String productId, String name, String type) {
        this.product = null;

        this.productId = Objects.requireNonNull(productId);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public Branding(Product parent, String productId, String name, String type) {
        this.product = Objects.requireNonNull(parent);

        this.productId = Objects.requireNonNull(productId);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public String getId() {
        return id;
    }

    /**
     * Returns the engineering product ID we are rebranding.
     *
     * @return The engineering product ID we are rebranding, *if* it is installed on the
     * client. Candlepin will always send down the brand mapping for a subscription, the
     * client is responsible for determining if it should be applied or not, and how.
     */
    @Override
    public String getProductId() {
        return productId;
    }

    /**
     * @return The brand name to be applied.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     *
     * @return The type of this branding. (i.e. "OS") Clients use this value to determine
     * what action should be taken with the branding information.
     */
    @Override
    public String getType() {
        return type;
    }

    /**
     * Returns the parent marketing product that this branding belongs to.
     *
     * @return A reference to the marketing product who is the parent of this branding.
     */
    public Product getProduct() {
        return product;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }

        if (!(anObject instanceof Branding)) {
            return false;
        }

        Branding that = (Branding) anObject;

        return new EqualsBuilder()
            .append(this.name, that.name)
            .append(this.productId, that.productId)
            .append(this.type, that.type)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(129, 15)
            .append(this.name)
            .append(this.productId)
            .append(this.type)
            .toHashCode();
    }

    @Override
    public String toString() {
        return String.format("Branding [id: %s, name: %s, productId: %s, type: %s]",
            this.getId(), this.getName(), this.getProductId(), this.getType());
    }
}
