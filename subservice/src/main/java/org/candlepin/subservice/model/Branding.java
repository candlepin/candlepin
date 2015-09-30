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
@Table(name = "cps_branding")
public class Branding extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 37)
    @NotNull
    protected String id;

    @Column(name = "product_id", nullable = false)
    @NotNull
    @Size(max = 255)
    protected String productId; // This should probably be a product reference

    @Column(nullable = false)
    @NotNull
    @Size(max = 255)
    protected String name;

    @Column(nullable = false)
    @NotNull
    @Size(max = 32)
    protected String type;

    public Branding() {

    }

    // TODO:
    // Add convenience constructors



    public String getId() {
        return this.id;
    }

    public Branding setId(String id) {
        this.id = id;
        return this;
    }

    public String getProductId() {
        return this.productId;
    }

    public Branding setProductId(String productId) {
        this.productId = productId;
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Branding setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return this.type;
    }

    public Branding setType(String type) {
        this.type = type;
        return this;
    }

}
