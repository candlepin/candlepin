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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Brand mapping is carried on subscription data and passed to clients through entitlement
 * certificates. It indicates that a particular engineering product ID is being rebranded
 * by the entitlement to the given name. The type is used by clients to determine what
 * action to take with the brand name.
 *
 * NOTE: Presently only type "OS" is supported client side.
 *
 * See sub-classes for actual implementations tying this to a subscription or pool.
 */
@Entity
@Table(name = "cp_org_branding")
public class OrgBranding extends AbstractHibernateObject {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(length = 37)
    @NotNull
    private String id;

    @Column(nullable = false)
    @NotNull
    private OrgProduct product;

    @Column(nullable = false)
    @NotNull
    @Size(max = 255)
    private String name;

    @Column(nullable = false)
    @NotNull
    @Size(max = 32)
    private String type;

    public OrgBranding() {
    }

    public OrgBranding(OrgProduct product, String type, String name) {
        this.product = product;
        this.type = type;
        this.name = name;
    }

    @XmlTransient
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     *
     * @return The engineering product we are rebranding, *if* it is installed on the
     * client. Candlepin will always send down the brand mapping for a subscription, the
     * client is responsible for determining if it should be applied or not, and how.
     */
    public String getProduct() {
        return product;
    }

    public void setProduct(OrgProduct product) {
        this.product = product;
    }

    /**
     * @return The brand name to be applied.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return The type of this branding. (i.e. "OS") Clients use this value to determine
     * what action should be taken with the branding information.
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (!(anObject instanceof OrgBranding)) {
            return false;
        }

        OrgBranding that = (OrgBranding) anObject;

        return new EqualsBuilder().append(this.name, that.name)
            .append(this.product, that.product)
            .append(this.type, that.type).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(129, 15).append(this.name)
            .append(this.product).append(this.type).toHashCode();
    }
}
