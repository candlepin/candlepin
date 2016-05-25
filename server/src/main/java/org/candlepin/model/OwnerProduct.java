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

import org.candlepin.common.jackson.HateoasInclude;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.resteasy.InfoProperty;

import com.fasterxml.jackson.annotation.JsonFilter;

import org.hibernate.annotations.GenericGenerator;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import io.swagger.annotations.ApiModelProperty;



/**
 * Represents the join table between Product and Owner.
 *
 * This class uses composite primary key from the two
 * entities. This strategy has been chosen so that
 * the current Candlepin schema doesn't change. However,
 * should we encounter any problems with this design,
 * there is nothing that stops us from using standard
 * uuid for the link.
 */
@XmlRootElement
@Entity
@Table(name = "cp2_owner_products")
@IdClass(OwnerProductKey.class)
public class OwnerProduct implements Persisted, Serializable {
    private static final long serialVersionUID = -7059065874812188165L;

    /**
     * This class already maps the foreign keys.
     * Because of that we need to disallow
     * Hibernate to update database based on
     * the owner and product fields.
     */
    @ManyToOne
    @JoinColumn(updatable=false, insertable=false)
    private Owner owner;

    @ManyToOne
    @JoinColumn(updatable=false, insertable=false)
    private Product product;

    @Id
    @Column(name="owner_id")
    private String ownerId;

    @Id
    @Column(name="product_uuid")
    private String productUuid;

    public OwnerProduct() {
        // Intentionally left empty
    }

    public OwnerProduct(String ownerId, String productUuid) {
        this.ownerId = ownerId;
        this.productUuid = productUuid;
    }

    @Override
    public Serializable getId() {
        return new OwnerProductKey(ownerId, productUuid);
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        if (owner.getId() == null) {
            throw new IllegalStateException(
                "Owner must be persisted before it can be linked to a product"
            );
        }

        this.owner = owner;
        ownerId = owner.getId();
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        if (product.getUuid() == null) {
            throw new IllegalStateException(
                "Product must be persisted before it can be linked to an owner"
            );
        }

        this.product = product;
        productUuid = product.getUuid();
    }

}
