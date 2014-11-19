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

import static org.junit.Assert.assertEquals;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Test;

import javax.inject.Inject;

public class AttributeTest extends DatabaseTestFixture {
    @Inject private ProductCurator productCurator;
    @Inject private ProductAttributeCurator attributeCurator;

    @Test
    public void testLookup() {
        Product p = TestUtil.createProduct();
        productCurator.create(p);
        ProductAttribute newAttr = new ProductAttribute("OwesUsMoney_100", "100");
        newAttr.setProduct(p);
        attributeCurator.create(newAttr);

        ProductAttribute foundAttr = attributeCurator.find(newAttr.getId());
        assertEquals(newAttr.getName(), foundAttr.getName());
        assertEquals(newAttr.getValue(), foundAttr.getValue());
    }

}
