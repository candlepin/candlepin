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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * General tests that apply to all entity mappers. Test suites for specific entity mapper
 * implementations should extend this class and override the necessary methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractEntityMapperTest<E extends AbstractHibernateObject,
    I extends ServiceAdapterModel> {

    /**
     * Builds an EntityMapper instance
     *
     * @return
     *  the newly created EntityMapper instance
     */
    protected abstract EntityMapper<E, I> buildEntityMapper();

    /**
     * Fetches the ID for the specified entity. If the given entity is null, this method should
     * return null.
     *
     * @param entity
     *  the entity for which to fetch the ID
     *
     * @return
     *  the ID of the given entity, or null if the entity is null or lacks an ID
     */
    protected abstract String getEntityId(E entity);

    /**
     * Fetches the ID for the specified entity. If the given entity is null, this method should
     * return null.
     *
     * @param entity
     *  the entity for which to fetch the ID
     *
     * @return
     *  the ID of the given entity, or null if the entity is null or lacks an ID
     */
    protected abstract String getEntityId(I entity);

    /**
     * Builds a new "local" entity to be used for testing. Each entity should be somewhat randomized
     * or unique such that two invocations with the same entity ID are likely to produce entities
     * that are not equal.
     *
     * @param owner
     *  the owner for the new local entity
     *
     * @param entityId
     *  the ID for the new entity
     *
     * @return
     *  a new local entity instance
     */
    protected abstract E buildLocalEntity(Owner owner, String entityId);

    /**
     * Builds a new "imported" entity to be used for testing. Each entity should be somewhat
     * randomized or unique such that two invocations with the same entity ID are likely to produce
     * entities that are not equal.
     *
     * @param owner
     *  the owner for the new imported entity
     *
     * @param entityId
     *  the ID for the new entity
     *
     * @return
     *  a new imported entity instance
     */
    protected abstract I buildImportedEntity(Owner owner, String entityId);

    @Test
    public void testAddExistingEntity() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertNull(mapper.getExistingEntity(id));

        E entity = this.buildLocalEntity(owner, id);

        // The first addition should be successful and add the entity; repeated additions should be
        // ignored.
        for (int i = 0; i < 5; ++i) {
            EntityMapper<E, I> result = mapper.addExistingEntity(entity);
            assertSame(mapper, result);

            Map<String, E> map = mapper.getExistingEntities();
            assertNotNull(map);
            assertEquals(1, map.size());
            assertThat(map, hasEntry(id, entity));
        }
    }

    @Test
    public void testAddExistingEntityIgnoresNullEntities() {
        EntityMapper<E, I> mapper = this.buildEntityMapper();

        EntityMapper<E, I> result = mapper.addExistingEntity(null);
        assertSame(mapper, result);

        Map<String, E> map = mapper.getExistingEntities();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testAddExistingEntities() {
        Owner owner = TestUtil.createOwner();
        E entity1 = this.buildLocalEntity(owner, "test_id-1");
        E entity2 = this.buildLocalEntity(owner, "test_id-2");
        E entity3a = this.buildLocalEntity(owner, "test_id-3");
        E entity3b = this.buildLocalEntity(owner, "test_id-3");
        E entity4 = this.buildLocalEntity(owner, "test_id-4");
        E entity5 = this.buildLocalEntity(owner, "test_id-5");

        assertNotEquals(entity3a, entity3b);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        Map<String, E> map1 = mapper.getExistingEntities();
        assertNotNull(map1);
        assertTrue(map1.isEmpty());

        // Add the first chunk, which should work as one expects
        Collection<E> chunk1 = List.of(entity1, entity2, entity3a);

        EntityMapper<E, I> result1 = mapper.addExistingEntities(chunk1);
        assertSame(mapper, result1);

        Map<String, E> map2 = mapper.getExistingEntities();
        assertNotNull(map2);
        assertEquals(3, map2.size());

        chunk1.forEach(entity -> assertThat(map2, hasEntry(this.getEntityId(entity), entity)));

        // Add the second chunk, and verify that duplicates overwrite on the key
        Collection<E> chunk2 = List.of(entity3b, entity4, entity5);

        EntityMapper<E, I> result2 = mapper.addExistingEntities(chunk2);
        assertSame(mapper, result2);

        Map<String, E> map3 = mapper.getExistingEntities();
        assertNotNull(map3);
        assertEquals(5, map3.size());

        chunk2.forEach(entity -> assertThat(map3, hasEntry(this.getEntityId(entity), entity)));

        // Verify only the second instance of entity3 is mapped
        assertThat(map3, hasEntry(this.getEntityId(entity3a), entity3b));
    }

    @Test
    public void testAddExistingEntitiesIgnoresNullValues() {
        Owner owner = TestUtil.createOwner();
        E entity1 = this.buildLocalEntity(owner, "test_id-1");
        E entity2 = this.buildLocalEntity(owner, "test_id-2");
        E entity3 = this.buildLocalEntity(owner, "test_id-3");

        Collection<E> input = Arrays.asList(entity1, null, entity2, null, entity3);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        EntityMapper<E, I> result = mapper.addExistingEntities(input);
        assertSame(mapper, result);

        Map<String, E> map = mapper.getExistingEntities();
        assertNotNull(map);
        assertEquals(3, map.size());

        assertThat(map, hasEntry(this.getEntityId(entity1), entity1));
        assertThat(map, hasEntry(this.getEntityId(entity2), entity2));
        assertThat(map, hasEntry(this.getEntityId(entity3), entity3));
    }

    @Test
    public void testGetExistingEntity() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        E entity = this.buildLocalEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addExistingEntity(entity);

        E output = mapper.getExistingEntity(entityId);
        assertSame(entity, output);
    }

    @Test
    public void testGetExistingEntityWithWrongId() {
        Owner owner = TestUtil.createOwner();
        E entity = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addExistingEntity(entity);

        E output = mapper.getExistingEntity("wrong_id");
        assertNull(output);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetExistingEntityWithNullOrEmptyId(String input) {
        Owner owner = TestUtil.createOwner();
        E entity = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addExistingEntity(entity);

        E output = mapper.getExistingEntity(input);
        assertNull(output);
    }

    @Test
    public void testGetExistingEntities() {
        Owner owner = TestUtil.createOwner();
        E entity1 = this.buildLocalEntity(owner, "test_id-1");
        E entity2 = this.buildLocalEntity(owner, "test_id-2");
        E entity3 = this.buildLocalEntity(owner, "test_id-3");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify the initial state is an empty map
        Map<String, E> initial = mapper.getExistingEntities();
        assertNotNull(initial);
        assertEquals(0, initial.size());

        // Add entities and verify the output state matches
        List<E> mapped = new ArrayList<>();
        for (E entity : List.of(entity1, entity2, entity3)) {
            mapper.addExistingEntity(entity);
            mapped.add(entity);

            Map<String, E> output = mapper.getExistingEntities();
            assertNotNull(output);
            assertEquals(mapped.size(), output.size());
            mapped.forEach(expected -> assertThat(output, hasEntry(this.getEntityId(expected), expected)));
        }
    }

    @Test
    public void testAddImportedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertNull(mapper.getImportedEntity(id));

        I entity = this.buildImportedEntity(owner, id);

        // The first addition should be successful and add the entity; repeated additions should be
        // ignored.
        for (int i = 0; i < 5; ++i) {
            EntityMapper<E, I> result = mapper.addImportedEntity(entity);
            assertSame(mapper, result);

            Map<String, I> map = mapper.getImportedEntities();
            assertNotNull(map);
            assertEquals(1, map.size());
            assertThat(map, hasEntry(id, entity));
        }
    }

    @Test
    public void testAddImportedEntityIgnoresNullEntities() {
        EntityMapper<E, I> mapper = this.buildEntityMapper();

        EntityMapper<E, I> result = mapper.addImportedEntity(null);
        assertSame(mapper, result);

        Map<String, I> map = mapper.getImportedEntities();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddImportedEntityWithNullOrEmptyEntityId(String entityId) {
        Owner owner = TestUtil.createOwner();
        I entity = this.buildImportedEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(entity));

        Map<String, I> map = mapper.getImportedEntities();
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    public void testAddImportedEntities() {
        Owner owner = TestUtil.createOwner();
        I entity1 = this.buildImportedEntity(owner, "test_id-1");
        I entity2 = this.buildImportedEntity(owner, "test_id-2");
        I entity3a = this.buildImportedEntity(owner, "test_id-3");
        I entity3b = this.buildImportedEntity(owner, "test_id-3");
        I entity4 = this.buildImportedEntity(owner, "test_id-4");
        I entity5 = this.buildImportedEntity(owner, "test_id-5");

        assertNotEquals(entity3a, entity3b);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        Map<String, I> map1 = mapper.getImportedEntities();
        assertNotNull(map1);
        assertTrue(map1.isEmpty());

        // Add the first chunk, which should work as one expects
        Collection<I> chunk1 = List.of(entity1, entity2, entity3a);

        EntityMapper<E, I> result1 = mapper.addImportedEntities(chunk1);
        assertSame(mapper, result1);

        Map<String, I> map2 = mapper.getImportedEntities();
        assertNotNull(map2);
        assertEquals(3, map2.size());

        chunk1.forEach(entity -> assertThat(map2, hasEntry(this.getEntityId(entity), entity)));

        // Add the second chunk, and verify that duplicates overwrite on the key
        Collection<I> chunk2 = List.of(entity3b, entity4, entity5);

        EntityMapper<E, I> result2 = mapper.addImportedEntities(chunk2);
        assertSame(mapper, result2);

        Map<String, I> map3 = mapper.getImportedEntities();
        assertNotNull(map3);
        assertEquals(5, map3.size());

        chunk2.forEach(entity -> assertThat(map3, hasEntry(this.getEntityId(entity), entity)));

        // Verify only the second instance of entity3 is mapped
        assertThat(map3, hasEntry(this.getEntityId(entity3a), entity3b));
    }

    @Test
    public void testAddImportedEntitiesIgnoresNullValues() {
        Owner owner = TestUtil.createOwner();
        I entity1 = this.buildImportedEntity(owner, "test_id-1");
        I entity2 = this.buildImportedEntity(owner, "test_id-2");
        I entity3 = this.buildImportedEntity(owner, "test_id-3");

        Collection<I> input = Arrays.asList(entity1, null, entity2, null, entity3);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        EntityMapper<E, I> result = mapper.addImportedEntities(input);
        assertSame(mapper, result);

        Map<String, I> map = mapper.getImportedEntities();
        assertNotNull(map);
        assertEquals(3, map.size());

        assertThat(map, hasEntry(this.getEntityId(entity1), entity1));
        assertThat(map, hasEntry(this.getEntityId(entity2), entity2));
        assertThat(map, hasEntry(this.getEntityId(entity3), entity3));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testAddImportedEntitiesFailsWithNullOrEmptyEntityIds(String entityId) {
        Owner owner = TestUtil.createOwner();
        I entity1 = this.buildImportedEntity(owner, "test_id-1");
        I entity2 = this.buildImportedEntity(owner, "test_id-2");
        I entity3 = this.buildImportedEntity(owner, "test_id-3");
        I badEntity = this.buildImportedEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        Collection<I> input = Arrays.asList(entity1, badEntity, entity2, badEntity, entity3);

        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntities(input));
    }

    @Test
    public void testGetImportedEntity() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        I entity = this.buildImportedEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addImportedEntity(entity);

        I output = mapper.getImportedEntity(entityId);
        assertSame(entity, output);
    }

    @Test
    public void testGetImportedEntityWithWrongId() {
        Owner owner = TestUtil.createOwner();
        I entity = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addImportedEntity(entity);

        I output = mapper.getImportedEntity("wrong_id");
        assertNull(output);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetImportedEntityWithNullOrEmptyId(String input) {
        Owner owner = TestUtil.createOwner();
        I entity = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper()
            .addImportedEntity(entity);

        I output = mapper.getImportedEntity(input);
        assertNull(output);
    }

    @Test
    public void testGetImportedEntities() {
        Owner owner = TestUtil.createOwner();
        I entity1 = this.buildImportedEntity(owner, "test_id-1");
        I entity2 = this.buildImportedEntity(owner, "test_id-2");
        I entity3 = this.buildImportedEntity(owner, "test_id-3");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify the initial state is an empty map
        Map<String, I> initial = mapper.getImportedEntities();
        assertNotNull(initial);
        assertEquals(0, initial.size());

        // Add entities and verify the output state matches
        List<I> mapped = new ArrayList<>();
        for (I entity : List.of(entity1, entity2, entity3)) {
            mapper.addImportedEntity(entity);
            mapped.add(entity);

            Map<String, I> output = mapper.getImportedEntities();
            assertNotNull(output);
            assertEquals(mapped.size(), output.size());
            mapped.forEach(expected -> assertThat(output, hasEntry(this.getEntityId(expected), expected)));
        }
    }

    @Test
    public void testHasEntityForMatchingExistingEntity() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add a matching existing entity
        mapper.addExistingEntity(existing);

        // Verify the result is valid with the matching entity in place
        result = mapper.hasEntity(id);
        assertTrue(result);
    }

    @Test
    public void testHasEntityForNonMatchingExistingEntity() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, "different_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add a dummy entity
        mapper.addExistingEntity(existing);

        // Verify the result is valid with a dummy entity in place
        result = mapper.hasEntity(id);
        assertFalse(result);
    }

    @Test
    public void testHasEntityForMatchingImportedEntity() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add a matching imported entity
        mapper.addImportedEntity(imported);

        // Verify the result is valid with the matching entity in place
        result = mapper.hasEntity(id);
        assertTrue(result);
    }

    @Test
    public void testHasEntityForMatchingExistingAndImportedEntities() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add our entities
        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(id);
        assertTrue(result);
    }

    @Test
    public void testHasEntityForMatchingExistingAndNonMatchingImportedEntities() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);
        I imported = this.buildImportedEntity(owner, "different_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add our entities
        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(id);
        assertTrue(result);
    }

    @Test
    public void testHasEntityForNonMatchingExistingAndMatchingImportedEntities() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, "different_id");
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add our entities
        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(id);
        assertTrue(result);
    }

    @Test
    public void testHasEntityForNonMatchingExistingAndNonMatchingImportedEntities() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, "different_id");
        I imported = this.buildImportedEntity(owner, "another_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(id);
        assertFalse(result);

        // Add our entities
        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(id);
        assertFalse(result);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testHasEntityWithNullOrEmptyId(String input) {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(input);
        assertFalse(result);

        // Add our entities
        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(input);
        assertFalse(result);
    }

    @Test
    public void testGetEntityIds() {
        Owner owner = TestUtil.createOwner();

        // Test set should include 2 unique existing, 2 unique imported, and 2 shared
        String existing1Id = "existing-1";
        String existing2Id = "existing-2";
        String existing3Id = "shared_id-1";
        String existing4Id = "shared_id-2";
        String imported1Id = "imported-1";
        String imported2Id = "imported-2";
        String imported3Id = "shared_id-1";
        String imported4Id = "shared_id-2";
        E existing1 = this.buildLocalEntity(owner, existing1Id);
        E existing2 = this.buildLocalEntity(owner, existing2Id);
        E existing3 = this.buildLocalEntity(owner, existing3Id);
        E existing4 = this.buildLocalEntity(owner, existing4Id);
        I imported1 = this.buildImportedEntity(owner, imported1Id);
        I imported2 = this.buildImportedEntity(owner, imported2Id);
        I imported3 = this.buildImportedEntity(owner, imported3Id);
        I imported4 = this.buildImportedEntity(owner, imported4Id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return an empty set
        Set<String> ids = mapper.getEntityIds();

        assertNotNull(ids);
        assertThat(ids, empty());

        // Add some entities
        mapper.addExistingEntities(Arrays.asList(existing1, existing2, existing3, existing4));
        mapper.addImportedEntities(Arrays.asList(imported1, imported2, imported3, imported4));

        // Verify the output contains 6 ids
        ids = mapper.getEntityIds();

        assertNotNull(ids);
        assertEquals(6, ids.size());
        assertThat(ids, hasItems(existing1Id, existing2Id, existing3Id, existing4Id));
        assertThat(ids, hasItems(imported1Id, imported2Id, imported3Id, imported4Id));
    }

    @Test
    public void testClear() {
        Owner owner = TestUtil.createOwner();

        String existingId = "test_id-1";
        String importedId = "test_id-1";
        E existing = this.buildLocalEntity(owner, existingId);
        I imported = this.buildImportedEntity(owner, importedId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        mapper.addExistingEntity(existing);
        mapper.addImportedEntity(imported);

        // Verify our state includes some data presence
        assertEquals(existing, mapper.getExistingEntity(existingId));
        assertEquals(imported, mapper.getImportedEntity(importedId));
        assertTrue(mapper.hasEntity(existingId));
        assertTrue(mapper.hasEntity(importedId));

        // Clear the state and re-verify
        mapper.clear();

        assertNull(mapper.getExistingEntity(existingId));
        assertNull(mapper.getImportedEntity(importedId));
        assertFalse(mapper.hasEntity(existingId));
        assertFalse(mapper.hasEntity(importedId));
    }

}
