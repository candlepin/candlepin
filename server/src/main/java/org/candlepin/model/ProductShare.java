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
package org.candlepin.model;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * When a pool is shared to an org, The product may or may not already exist in the recipient
 * org. This class represents the sharing of a product between two organizations.  Note that this object
 * is never returned via the REST API and is intended for internal bookkeeping only, specifically used
 * only during a share bind.
 */
@XmlRootElement
@Entity
@Table(name = "cp2_product_shares")
public class ProductShare extends AbstractHibernateObject implements Owned, Eventful {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column
    @Size(max = 32)
    @NotNull
    private String id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id")
    private Owner recipient;

    @NotNull
    @OneToOne(optional = false)
    @JoinColumn(name = "product_uuid")
    private Product product;

    public ProductShare() {
    }

    public ProductShare(Owner owner, Product product, Owner recipient) {
        this.owner = owner;
        this.product = product;
        this.recipient = recipient;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public Owner getRecipient() {
        return recipient;
    }

    public void setRecipient(Owner recipient) {
        this.recipient = recipient;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
