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

import com.fasterxml.jackson.annotation.JsonFilter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents a derived product provided by a Pool. These are used when a
 * sub-pool is created as a result of a bind, so the sub-pool can provide
 * different content than the parent.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.PROPERTY)
@Entity
@JsonFilter("ProvidedProductFilter")
@DiscriminatorValue("derived")
public class DerivedProvidedProduct extends ProvidedProduct {
    public DerivedProvidedProduct() {
        super();
    }

    public DerivedProvidedProduct(String productId, String productName) {
        super(productId, productName);
    }

    public DerivedProvidedProduct(String productId, String productName, Pool pool) {
        super(productId, productName, pool);
    }
}
