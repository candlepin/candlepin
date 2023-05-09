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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.service.model.ProductInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.stream.Stream;



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
    protected ProductMapper buildEntityMapper() {
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

    @Test
    public void testLocalEntitiesMatchMatchesEntitiesWithSameIDAndUUID() {
        Product current = new Product()
            .setId("product1")
            .setUuid("product_uuid")
            .setName("product name");

        Product inbound = new Product()
            .setId(current.getId())
            .setUuid(current.getUuid())
            .setName(current.getName());

        ProductMapper mapper = this.buildEntityMapper();

        // uuids equal => should match
        assertEquals(current, inbound);
        assertTrue(mapper.entitiesMatch(current, inbound));

        // uuids equal + objects differ => still match
        inbound.setName(current.getName() + " modified");
        assertNotEquals(current.getName(), inbound.getName());
        assertTrue(mapper.entitiesMatch(current, inbound));
    }

    @Test
    public void testLocalEntitiesMatchRejectsEntitiesWithSameIDAndDifferentUUIDs() {
        Product current = new Product()
            .setId("product1")
            .setUuid("product_uuid")
            .setName("product name");

        Product inbound = new Product()
            .setId(current.getId())
            .setUuid(current.getUuid() + " modified")
            .setName(current.getName());

        ProductMapper mapper = this.buildEntityMapper();

        // uuids differ => no match, even when the objects are otherwise equal
        assertEquals(current, inbound);
        assertFalse(mapper.entitiesMatch(current, inbound));

        // uuids differ + data differs => no match
        inbound.setName(current.getName() + " modified");
        assertNotEquals(current, inbound);
        assertFalse(mapper.entitiesMatch(current, inbound));
    }

    private static Stream<Arguments> mixedNullUUIDProvider() {
        return Stream.of(
            Arguments.of("product_uuid", null),
            Arguments.of(null, "product_uuid"),
            Arguments.of(null, null));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}, {1}")
    @MethodSource("mixedNullUUIDProvider")
    public void testLocalEntitiesMatchDefaultsToEqualityChecksWithoutUUID(
        String currentUuid, String inboundUuid) {

        Product current = new Product()
            .setId("product1")
            .setUuid(currentUuid)
            .setName("product name");

        Product inbound = new Product()
            .setId(current.getId())
            .setUuid(inboundUuid)
            .setName(current.getName());

        ProductMapper mapper = this.buildEntityMapper();

        // no uuid + entities equal => match
        assertEquals(current, inbound);
        assertTrue(mapper.entitiesMatch(current, inbound));

        // no uuid + entities differ => no match
        inbound.setName(current.getName() + " modified");
        assertNotEquals(current, inbound);
        assertFalse(mapper.entitiesMatch(current, inbound));
    }

    @Test
    public void testLocalEntitiesMatchErrorsOnNullCurrentEntity() {
        Product entity = this.buildLocalEntity(new Owner(), "product_id");
        ProductMapper mapper = this.buildEntityMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.entitiesMatch(null, entity));
    }

    @Test
    public void testLocalEntitiesMatchErrorsOnNullInboundEntity() {
        Product entity = this.buildLocalEntity(new Owner(), "product_id");
        ProductMapper mapper = this.buildEntityMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.entitiesMatch(entity, null));
    }

    @Test
    public void testUpstreamEntitiesMatchMatchesOnEquality() {
        // Impl note: Product is a ProductInfo
        Product current = new Product()
            .setId("product1")
            .setName("product name");

        Product inbound = new Product()
            .setId(current.getId())
            .setName(current.getName());

        ProductMapper mapper = this.buildEntityMapper();

        // no uuid + entities equal => match
        assertEquals(current, inbound);
        assertTrue(mapper.entitiesMatch((ProductInfo) current, (ProductInfo) inbound));

        // no uuid + entities differ => no match
        inbound.setName(current.getName() + " modified");
        assertNotEquals(current, inbound);
        assertFalse(mapper.entitiesMatch((ProductInfo) current, (ProductInfo) inbound));
    }

    @Test
    public void testUpstreamEntitiesMatchErrorsOnNullCurrentEntity() {
        ProductInfo entity = this.buildImportedEntity(new Owner(), "product_id");
        ProductMapper mapper = this.buildEntityMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.entitiesMatch(null, entity));
    }

    @Test
    public void testUpstreamEntitiesMatchErrorsOnNullInboundEntity() {
        ProductInfo entity = this.buildImportedEntity(new Owner(), "product_id");
        ProductMapper mapper = this.buildEntityMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.entitiesMatch(entity, null));
    }

    /**
     * This oddly named test captures the case where we have two product instances that are equal
     * but have different UUIDs, usually stemming from a case where the entity versions were either
     * manually cleared or cleared during a previous mapping resolution run that didn't fully clean
     * up the mapping issues.
     *
     * In such a case where two local product instances only differ by UUID, we expect the mapper to
     * use the most recently mapped entity, but flag the mapping as dirty.
     */
    @Test
    public void testAddExistingEntityCorrectlyFlagsMappingErrorsOnEqualProduct() {
        String id = "product_id";

        Product current = new Product()
            .setId(id)
            .setUuid("product_uuid-1")
            .setName("product name");

        Product inbound = new Product()
            .setId(id)
            .setUuid("product_uuid-2")
            .setName(current.getName());

        ProductMapper mapper = this.buildEntityMapper();

        // Adding the first entity should be normal
        mapper.addExistingEntity(current);
        assertFalse(mapper.isDirty(id));

        // Re-adding itself should also be fine
        mapper.addExistingEntity(current);
        assertFalse(mapper.isDirty(id));

        // Adding the new inbound entity which is equal but has a different UUID should flip the
        // dirty flag
        assertEquals(current, inbound);
        mapper.addExistingEntity(inbound);
        assertTrue(mapper.isDirty(id));
    }
}
