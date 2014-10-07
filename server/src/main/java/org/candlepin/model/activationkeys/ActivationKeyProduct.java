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
package org.candlepin.model.activationkeys;

import org.candlepin.model.AbstractHibernateObject;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * SubscriptionToken
 */
@XmlRootElement
@Entity
@XmlAccessorType(XmlAccessType.PROPERTY)
@Table(name = "cp_activationkey_product",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"key_id", "product_id"})}
)
public class ActivationKeyProduct extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 32)
    @NotNull
    private String id;

    @ManyToOne
    @JoinColumn(nullable = false)
    @Index(name = "cp_activation_key_prod_k_fk_idx")
    @NotNull
    private ActivationKey key;

    @Column(name = "product_id", nullable = false)
    @Size(max = 255)
    @NotNull
    private String productId;

    public ActivationKeyProduct() {
    }

    public ActivationKeyProduct(ActivationKey key, String productId) {
        this.key = key;
        this.productId = productId;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the key
     */
    @XmlTransient
    public ActivationKey getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKeyId(ActivationKey key) {
        this.key = key;
    }

    /**
     * @return the Product
     */
    public String getProductId() {
        return productId;
    }

    /**
     * @param productId the Product Id to set
     */
    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Override
    public String toString() {
        return "Activation key: " + this.getKey().getName() + ", Product ID: " +
                this.getProductId();
    }
}
