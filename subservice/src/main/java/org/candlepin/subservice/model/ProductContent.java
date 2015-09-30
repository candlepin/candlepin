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
package org.candlepin.subservice.model;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;



/**
 * ProductContent
 */
@Entity
@Table(name = "cps_product_content")
public class ProductContent {

    @NotNull
    protected Date created;

    @NotNull
    protected Date updated;

    @NotNull
    protected Product product;

    @ManyToOne
    @JoinColumn(name = "content_uuid", nullable = false, updatable = false)
    @NotNull
    protected Content content;

    protected boolean enabled;

    /**
     * Temporary object used to prevent infinite recursion during conversion to the CP model
     */
    private org.candlepin.model.ProductContent cpmodel;

    public ProductContent() {

    }

    // TODO:
    // Add convenience constructors



    public Product getProduct() {
        return this.product;
    }

    public ProductContent setProduct(Product product) {
        this.product = product;
        return this;
    }

    public Content getContent() {
        return this.content;
    }

    public ProductContent setContent(Content content) {
        this.content = content;
        return this;
    }

    public boolean getEnabled() {
        return this.enabled;
    }

    public ProductContent setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @PrePersist
    protected void onCreate() {
        Date now = new Date();

        setCreated(now);
        setUpdated(now);
    }

    @PreUpdate
    protected void onUpdate() {
        setUpdated(new Date());
    }

    @XmlElement
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlElement
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public synchronized org.candlepin.model.ProductContent toCandlepinModel() {
        try {
            if (this.cpmodel == null) {
                org.candlepin.model.ProductContent output = new org.candlepin.model.ProductContent();
                this.cpmodel = output;

                Product product = this.getProduct();
                output.setProduct(product != null ? product.toCandlepinModel() : null);

                Content content = this.getContent();
                output.setContent(content != null ? content.toCandlepinModel() : null);

                output.setEnabled(this.getEnabled());
                output.setCreated(this.getCreated());
                output.setUpdated(this.getUpdated());

                return output;
            }
            else {
                return this.cpmodel;
            }
        }
        finally {
            this.cpmodel = null;
        }
    }
}
