/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.controller.refresher.nodes;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;



/**
 * Test suite for the ProductNode class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductNodeTest extends AbstractNodeTest<Product, ProductInfo> {

    @Override
    protected EntityNode<Product, ProductInfo> buildEntityNode(Owner owner, String entityId) {
        return new ProductNode(owner, entityId);
    }

    @Override
    protected Product buildLocalEntity(Owner owner, String entityId) {
        return new Product()
            .setId(entityId);
    }

    @Override
    protected ProductInfo buildImportedEntity(Owner owner, String entityId) {
        return new Product()
            .setId(entityId);
    }

    @Test
    public void testGetEntityClass() {
        Owner owner = TestUtil.createOwner();
        ProductNode node = new ProductNode(owner, "test_id");

        assertEquals(Product.class, node.getEntityClass());
    }

}
