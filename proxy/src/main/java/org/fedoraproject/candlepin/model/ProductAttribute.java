/**
 * Copyright (c) 2009 Red Hat, Inc.
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

package org.fedoraproject.candlepin.model;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * See Attributes class for documentation.
 */
@Entity
@Table(name = "cp_product_attribute")
@SequenceGenerator(name = "seq_product_attribute", sequenceName = "seq_product_attribute",
        allocationSize = 1)
@Embeddable
public class ProductAttribute extends Attribute {

    public ProductAttribute() {

    }

    public ProductAttribute(String name, String quantity) {
        this.name = name;
        this.value = quantity;
    }
}
