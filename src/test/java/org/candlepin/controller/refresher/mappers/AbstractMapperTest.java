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
package org.candlepin.controller.refresher.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Owner;
import org.candlepin.service.model.ServiceAdapterModel;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;



/**
 * General tests that apply to all entity mappers. Test suites for specific entity mapper
 * implementations should extend this class and override the necessary methods.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractMapperTest<E extends AbstractHibernateObject, I extends ServiceAdapterModel> {

    /**
     * Builds an EntityMapper instance
     *
     * @return
     *  the newly created EntityMapper instance
     */
    protected abstract EntityMapper<E, I> buildEntityMapper();

    /**
     * Builds a new "local" entity to be used for testing.
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
     * Builds a new "imported" entity to be used for testing
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
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addExistingEntity(existing);
            assertFalse(result);
        }
    }

    @Test
    public void testAddExistingEntityWithNullId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, null);

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(existing));
    }

    @Test
    public void testAddExistingEntityWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(existing));
    }

    @Test
    public void testAddExistingEntityById() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        E existing = this.buildLocalEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addExistingEntity(entityId, existing);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addExistingEntity(entityId, existing);
            assertFalse(result);
        }
    }

    @Test
    public void testAddExistingEntityByIdWithNullId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(null, existing));
    }

    @Test
    public void testAddExistingEntityByIdWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity("", existing));
    }

    @Test
    public void testAddExistingEntities() {
        Owner owner = TestUtil.createOwner();
        E existing1 = this.buildLocalEntity(owner, "test_id-1");
        E existing2 = this.buildLocalEntity(owner, "test_id-2");
        E existing3 = this.buildLocalEntity(owner, "test_id-3");

        Collection<E> entities = Arrays.asList(existing1, existing2, existing3);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be completely successful and return an integer value matching
        // the list size
        int result = mapper.addExistingEntities(entities);
        assertEquals(entities.size(), result);

        // Repeated additions should return 0, as all entities are already mapped
        for (int i = 0; i < 5; ++i) {
            result = mapper.addExistingEntities(entities);
            assertEquals(0, result);
        }

        E existing4 = this.buildLocalEntity(owner, "test_id-4");
        E existing5 = this.buildLocalEntity(owner, "test_id-5");

        entities = Arrays.asList(existing1, existing2, existing3, existing4, existing5);

        // Expanding the collection with two new entities should give us an output of 2
        result = mapper.addExistingEntities(entities);
        assertEquals(2, result);
    }

    @Test
    public void testGetExistingEntity() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        E existing = this.buildLocalEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is not null, and identical to our expected object
        E output = mapper.getExistingEntity(entityId);
        assertSame(existing, output);
    }

    @Test
    public void testGetExistingEntityWithWrongId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        E output = mapper.getExistingEntity("wrong_id");
        assertNull(output);
    }

    @Test
    public void testGetExistingEntityWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        E output = mapper.getExistingEntity("");
        assertNull(output);
    }

    @Test
    public void testGetExistingEntityWithNullId() {
        Owner owner = TestUtil.createOwner();
        E existing = this.buildLocalEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        E output = mapper.getExistingEntity(null);
        assertNull(output);
    }

    @Test
    public void testGetExistingEntities() {
        Owner owner = TestUtil.createOwner();
        String existing1Id = "test_id-1";
        String existing2Id = "test_id-2";
        String existing3Id = "test_id-3";
        E existing1 = this.buildLocalEntity(owner, existing1Id);
        E existing2 = this.buildLocalEntity(owner, existing2Id);
        E existing3 = this.buildLocalEntity(owner, existing3Id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify the initial state is an empty map
        Map<String, E> output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(0, output.size());

        boolean result = mapper.addExistingEntity(existing1);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(1, output.size());
        assertThat(output, hasEntry(existing1Id, existing1));

        // Add another element
        result = mapper.addExistingEntity(existing2);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(2, output.size());
        assertThat(output, hasEntry(existing1Id, existing1));
        assertThat(output, hasEntry(existing2Id, existing2));

        // Add another element
        result = mapper.addExistingEntity(existing3);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(existing1Id, existing1));
        assertThat(output, hasEntry(existing2Id, existing2));
        assertThat(output, hasEntry(existing3Id, existing3));
    }

    @Test
    public void testAddImportedEntity() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addImportedEntity(imported);
            assertFalse(result);
        }
    }

    @Test
    public void testAddImportedEntityWithNullId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, null);

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(imported));
    }

    @Test
    public void testAddImportedEntityWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(imported));
    }

    @Test
    public void testAddImportedEntityById() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        I imported = this.buildImportedEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addImportedEntity(entityId, imported);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addImportedEntity(entityId, imported);
            assertFalse(result);
        }
    }

    @Test
    public void testAddImportedEntityByIdWithNullId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(null, imported));
    }

    @Test
    public void testAddImportedEntityByIdWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity("", imported));
    }

    @Test
    public void testAddImportedEntities() {
        Owner owner = TestUtil.createOwner();
        I imported1 = this.buildImportedEntity(owner, "test_id-1");
        I imported2 = this.buildImportedEntity(owner, "test_id-2");
        I imported3 = this.buildImportedEntity(owner, "test_id-3");

        Collection<I> entities = Arrays.asList(imported1, imported2, imported3);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // The first addition should be completely successful and return an integer value matching
        // the list size
        int result = mapper.addImportedEntities(entities);
        assertEquals(entities.size(), result);

        // Repeated additions should return 0, as all entities are already mapped
        for (int i = 0; i < 5; ++i) {
            result = mapper.addImportedEntities(entities);
            assertEquals(0, result);
        }

        I imported4 = this.buildImportedEntity(owner, "test_id-4");
        I imported5 = this.buildImportedEntity(owner, "test_id-5");

        entities = Arrays.asList(imported1, imported2, imported3, imported4, imported5);

        // Expanding the collection with two new entities should give us an output of 2
        result = mapper.addImportedEntities(entities);
        assertEquals(2, result);
    }

    @Test
    public void testGetImportedEntity() {
        Owner owner = TestUtil.createOwner();
        String entityId = "test_id";
        I imported = this.buildImportedEntity(owner, entityId);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is not null, and identical to our expected object
        I output = mapper.getImportedEntity(entityId);
        assertSame(imported, output);
    }

    @Test
    public void testGetImportedEntityWithWrongId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        I output = mapper.getImportedEntity("wrong_id");
        assertNull(output);
    }

    @Test
    public void testGetImportedEntityWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        I output = mapper.getImportedEntity("");
        assertNull(output);
    }

    @Test
    public void testGetImportedEntityWithNullId() {
        Owner owner = TestUtil.createOwner();
        I imported = this.buildImportedEntity(owner, "test_id");

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        I output = mapper.getImportedEntity(null);
        assertNull(output);
    }

    @Test
    public void testGetImportedEntities() {
        Owner owner = TestUtil.createOwner();
        String imported1Id = "test_id-1";
        String imported2Id = "test_id-2";
        String imported3Id = "test_id-3";
        I imported1 = this.buildImportedEntity(owner, imported1Id);
        I imported2 = this.buildImportedEntity(owner, imported2Id);
        I imported3 = this.buildImportedEntity(owner, imported3Id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Verify the initial state is an empty map
        Map<String, I> output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(0, output.size());

        boolean result = mapper.addImportedEntity(imported1);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(1, output.size());
        assertThat(output, hasEntry(imported1Id, imported1));

        // Add another element
        result = mapper.addImportedEntity(imported2);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(2, output.size());
        assertThat(output, hasEntry(imported1Id, imported1));
        assertThat(output, hasEntry(imported2Id, imported2));

        // Add another element
        result = mapper.addImportedEntity(imported3);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(imported1Id, imported1));
        assertThat(output, hasEntry(imported2Id, imported2));
        assertThat(output, hasEntry(imported3Id, imported3));
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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

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
        result = mapper.addImportedEntity(imported);
        assertTrue(result);

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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

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
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(id);
        assertFalse(result);
    }

    @Test
    public void testHasEntityWithNullId() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity(null);
        assertFalse(result);

        // Add our entities
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity(null);
        assertFalse(result);
    }

    @Test
    public void testHasEntityWithEmptyId() {
        Owner owner = TestUtil.createOwner();
        String id = "test_id";
        E existing = this.buildLocalEntity(owner, id);
        I imported = this.buildImportedEntity(owner, id);

        EntityMapper<E, I> mapper = this.buildEntityMapper();

        // Initial state should always return false
        boolean result = mapper.hasEntity("");
        assertFalse(result);

        // Add our entities
        result = mapper.addExistingEntity(existing);
        assertTrue(result);

        result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the result is valid with both entities in place
        result = mapper.hasEntity("");
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
        int count = mapper.addExistingEntities(Arrays.asList(existing1, existing2, existing3, existing4));
        assertEquals(4, count);

        count = mapper.addImportedEntities(Arrays.asList(imported1, imported2, imported3, imported4));
        assertEquals(4, count);

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

        assertTrue(mapper.addExistingEntity(existing));
        assertTrue(mapper.addImportedEntity(imported));

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
