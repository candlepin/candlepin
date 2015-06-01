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
package org.candlepin.gutterball.model.snapshot;

import javax.persistence.Entity;
import javax.persistence.Table;



@Entity
@Table(name = "gb_partcompprod_snap")
public class PartiallyCompliantProductReference extends AbstractProductReference {
    /**
     * Creates a new product reference instance with no compliance status association and no product
     * ID.
     */
    public PartiallyCompliantProductReference() {
        super();
    }

    /**
     * Creates a new product reference instance with the specified compliance status and product ID.
     *
     * @param status
     *  The ComplianceStatus to associate with this product reference
     *
     * @param productId
     *  The ID of the product to be referenced by this product reference
     */
    public PartiallyCompliantProductReference(ComplianceStatus status, String productId) {
        super(status, productId);
    }
}
