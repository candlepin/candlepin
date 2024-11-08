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
import org.candlepin.model.Pool;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;



@ExtendWith(MockitoExtension.class)
public class PoolMapperTest extends AbstractEntityMapperTest<Pool, SubscriptionInfo> {

    /** Used to ensure generated instances have some differences between them */
    private static int generatedEntityCount = 0;

    @Override
    protected String getEntityId(Pool entity) {
        return entity != null ? entity.getId() : null;
    }

    @Override
    protected String getEntityId(SubscriptionInfo entity) {
        return entity != null ? entity.getId() : null;
    }

    @Override
    protected EntityMapper<Pool, SubscriptionInfo> buildEntityMapper() {
        return new PoolMapper();
    }

    @Override
    protected Pool buildLocalEntity(Owner owner, String entityId) {
        return new Pool()
            .setId(entityId)
            .setOwner(owner)
            .setQuantity(10000L + ++generatedEntityCount);
    }

    @Override
    protected SubscriptionInfo buildImportedEntity(Owner owner, String entityId) {
        return new Pool()
            .setId(entityId)
            .setOwner(owner)
            .setQuantity(10000L + ++generatedEntityCount);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddExistingEntityWithNullOrEmptyEntityId(String entityId) {
        Owner owner = TestUtil.createOwner();
        Pool entity = this.buildLocalEntity(owner, entityId);

        EntityMapper<Pool, SubscriptionInfo> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(entity));

        Map<String, Pool> map = mapper.getExistingEntities();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddExistingEntitiesFailsWithNullOrEmptyEntityIds(String entityId) {
        Owner owner = TestUtil.createOwner();
        Pool entity1 = this.buildLocalEntity(owner, "test_id-1");
        Pool entity2 = this.buildLocalEntity(owner, "test_id-2");
        Pool entity3 = this.buildLocalEntity(owner, "test_id-3");
        Pool badEntity = this.buildLocalEntity(owner, entityId);

        EntityMapper<Pool, SubscriptionInfo> mapper = this.buildEntityMapper();
        Collection<Pool> input = List.of(entity1, badEntity, entity2, badEntity, entity3);

        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntities(input));
    }

}
