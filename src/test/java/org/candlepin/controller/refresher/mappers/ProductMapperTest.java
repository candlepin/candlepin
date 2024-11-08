/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.controller.refresher.mappers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.List;
import java.util.Map;



/**
 * Test suite for the ProductMapper class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProductMapperTest extends AbstractEntityMapperTest<Product, ProductInfo> {

    /** Used to ensure generated instances have some differences between them */
    private static int generatedEntityCount = 0;

    @Override
    protected String getEntityId(Product entity) {
        return entity != null ? entity.getId() : null;
    }

    @Override
    protected String getEntityId(ProductInfo entity) {
        return entity != null ? entity.getId() : null;
    }

    @Override
    protected EntityMapper<Product, ProductInfo> buildEntityMapper() {
        return new ProductMapper();
    }

    @Override
    protected Product buildLocalEntity(Owner owner, String entityId) {
        return new Product()
            .setId(entityId)
            .setName(String.format("%s-%d", entityId, ++generatedEntityCount));
    }

    @Override
    protected ProductInfo buildImportedEntity(Owner owner, String entityId) {
        return new Product()
            .setId(entityId)
            .setName(String.format("%s-%d", entityId, ++generatedEntityCount));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddExistingEntityWithNullOrEmptyEntityId(String entityId) {
        Owner owner = TestUtil.createOwner();
        Product entity = this.buildLocalEntity(owner, entityId);

        EntityMapper<Product, ProductInfo> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(entity));

        Map<String, Product> map = mapper.getExistingEntities();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddExistingEntitiesFailsWithNullOrEmptyEntityIds(String entityId) {
        Owner owner = TestUtil.createOwner();
        Product entity1 = this.buildLocalEntity(owner, "test_id-1");
        Product entity2 = this.buildLocalEntity(owner, "test_id-2");
        Product entity3 = this.buildLocalEntity(owner, "test_id-3");
        Product badEntity = this.buildLocalEntity(owner, entityId);

        EntityMapper<Product, ProductInfo> mapper = this.buildEntityMapper();
        Collection<Product> input = List.of(entity1, badEntity, entity2, badEntity, entity3);

        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntities(input));
    }

}
