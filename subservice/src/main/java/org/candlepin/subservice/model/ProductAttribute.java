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

import org.candlepin.model.AbstractHibernateObject;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;



/**
 * See Attributes interface for documentation.f
 */
@Entity
@Table(name = "cps_product_attributes")
public class ProductAttribute extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @NotNull
    protected String id;

    @Column(nullable = false)
    @Size(max = 255)
    @NotNull
    protected String name;

    @Column
    @Size(max = 255)
    protected String value;

    @ManyToOne
    @JoinColumn(name = "product_uuid", nullable = false)
    @NotNull
    protected Product product;

    protected ProductAttribute() {

    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public ProductAttribute setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public ProductAttribute setName(String name) {
        this.name = name;
        return this;
    }

    public String getValue() {
        return this.value;
    }

    public ProductAttribute setValue(String value) {
        this.value = value;
        return this;
    }

    public Product getProduct() {
        return this.product;
    }

    public ProductAttribute setProduct(Product product) {
        this.product = product;
        return this;
    }

}
