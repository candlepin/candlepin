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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
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
@Table(name = "cp2_owner_product_shares")
public class OwnerProductShare extends AbstractHibernateObject implements Owned, Eventful {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column
    @Size(max = 32)
    @NotNull
    private String id;

    @NotNull
    @Column(name = "product_id")
    private String productId;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "sharing_owner_product_uuid")
    private Product product;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "sharing_owner_id")
    private Owner sharingOwner;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_owner_id")
    private Owner recipientOwner;

    @NotNull
    @Column(name = "share_date")
    private Date shareDate;

    @Column(name = "active")
    private boolean active;

    public OwnerProductShare() {
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
        return sharingOwner;
    }

    public String getProductId() {
        return this.productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Product getProduct() {
        return this.product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Owner getSharingOwner() {
        return this.sharingOwner;
    }

    public void setSharingOwner(Owner owner) {
        this.sharingOwner = owner;
    }

    public Owner getRecipientOwner() {
        return this.recipientOwner;
    }

    public void setRecipientOwner(Owner recipient) {
        this.recipientOwner = recipient;
    }

    public Date getShareDate() {
        return this.shareDate;
    }

    public void setShareDate(Date shareDate) {
        this.shareDate = shareDate;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
