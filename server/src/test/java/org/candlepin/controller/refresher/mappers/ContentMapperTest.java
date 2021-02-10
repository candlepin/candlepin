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

import org.candlepin.model.Content;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ContentMapper class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ContentMapperTest {

    @Test
    public void testAddExistingEntity() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

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
        Content existing = new Content();

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(existing));
    }

    @Test
    public void testAddExistingEntityWithEmptyId() {
        Content existing = new Content("");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(existing));
    }

    @Test
    public void testAddExistingEntityById() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addExistingEntity(existing.getId(), existing);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addExistingEntity(existing.getId(), existing);
            assertFalse(result);
        }
    }

    @Test
    public void testAddExistingEntityByIdWithNullId() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity(null, existing));
    }

    @Test
    public void testAddExistingEntityByIdWithEmptyId() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addExistingEntity("", existing));
    }

    @Test
    public void testAddExistingEntities() {
        Content existing1 = TestUtil.createContent("test_id-1");
        Content existing2 = TestUtil.createContent("test_id-2");
        Content existing3 = TestUtil.createContent("test_id-3");

        Collection<Content> entities = Arrays.asList(existing1, existing2, existing3);

        ContentMapper mapper = new ContentMapper();

        // The first addition should be completely successful and return an integer value matching
        // the list size
        int result = mapper.addExistingEntities(entities);
        assertEquals(entities.size(), result);

        // Repeated additions should return 0, as all entities are already mapped
        for (int i = 0; i < 5; ++i) {
            result = mapper.addExistingEntities(entities);
            assertEquals(0, result);
        }

        Content existing4 = TestUtil.createContent("test_id-4");
        Content existing5 = TestUtil.createContent("test_id-5");

        entities = Arrays.asList(existing1, existing2, existing3, existing4, existing5);

        // Expanding the collection with two new entities should give us an output of 2
        result = mapper.addExistingEntities(entities);
        assertEquals(2, result);
    }

    @Test
    public void testGetExistingEntity() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is not null, and identical to our expected object
        Content output = mapper.getExistingEntity(existing.getId());
        assertSame(existing, output);
    }

    @Test
    public void testGetExistingEntityWithWrongId() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        Content output = mapper.getExistingEntity("wrong_id");
        assertNull(output);
    }

    @Test
    public void testGetExistingEntityWithEmptyId() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        Content output = mapper.getExistingEntity("");
        assertNull(output);
    }

    @Test
    public void testGetExistingEntityWithNullId() {
        Content existing = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addExistingEntity(existing);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        Content output = mapper.getExistingEntity(null);
        assertNull(output);
    }

    @Test
    public void testGetExistingEntities() {
        Content existing1 = TestUtil.createContent("test_id-1");
        Content existing2 = TestUtil.createContent("test_id-2");
        Content existing3 = TestUtil.createContent("test_id-3");

        ContentMapper mapper = new ContentMapper();

        // Verify the initial state is an empty map
        Map<String, Content> output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(0, output.size());

        boolean result = mapper.addExistingEntity(existing1);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(1, output.size());
        assertThat(output, hasEntry(existing1.getId(), existing1));

        // Add another element
        result = mapper.addExistingEntity(existing2);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(2, output.size());
        assertThat(output, hasEntry(existing1.getId(), existing1));
        assertThat(output, hasEntry(existing2.getId(), existing2));

        // Add another element
        result = mapper.addExistingEntity(existing3);
        assertTrue(result);

        output = mapper.getExistingEntities();

        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(existing1.getId(), existing1));
        assertThat(output, hasEntry(existing2.getId(), existing2));
        assertThat(output, hasEntry(existing3.getId(), existing3));
    }

    @Test
    public void testAddImportedEntity() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

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
        ContentInfo imported = new Content();

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(imported));
    }

    @Test
    public void testAddImportedEntityWithEmptyId() {
        ContentInfo imported = new Content("");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(imported));
    }

    @Test
    public void testAddImportedEntityById() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // The first addition should be successful and return true.
        boolean result = mapper.addImportedEntity(imported.getId(), imported);
        assertTrue(result);

        // Repeated additions should be ignored and return false.
        for (int i = 0; i < 5; ++i) {
            result = mapper.addImportedEntity(imported.getId(), imported);
            assertFalse(result);
        }
    }

    @Test
    public void testAddImportedEntityByIdWithNullId() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity(null, imported));
    }

    @Test
    public void testAddImportedEntityByIdWithEmptyId() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();
        assertThrows(IllegalArgumentException.class, () -> mapper.addImportedEntity("", imported));
    }

    @Test
    public void testAddImportedEntities() {
        ContentInfo imported1 = TestUtil.createContent("test_id-1");
        ContentInfo imported2 = TestUtil.createContent("test_id-2");
        ContentInfo imported3 = TestUtil.createContent("test_id-3");

        Collection<ContentInfo> entities = Arrays.asList(imported1, imported2, imported3);

        ContentMapper mapper = new ContentMapper();

        // The first addition should be completely successful and return an integer value matching
        // the list size
        int result = mapper.addImportedEntities(entities);
        assertEquals(entities.size(), result);

        // Repeated additions should return 0, as all entities are already mapped
        for (int i = 0; i < 5; ++i) {
            result = mapper.addImportedEntities(entities);
            assertEquals(0, result);
        }

        ContentInfo imported4 = TestUtil.createContent("test_id-4");
        ContentInfo imported5 = TestUtil.createContent("test_id-5");

        entities = Arrays.asList(imported1, imported2, imported3, imported4, imported5);

        // Expanding the collection with two new entities should give us an output of 2
        result = mapper.addImportedEntities(entities);
        assertEquals(2, result);
    }

    @Test
    public void testGetImportedEntity() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is not null, and identical to our expected object
        ContentInfo output = mapper.getImportedEntity(imported.getId());
        assertSame(imported, output);
    }

    @Test
    public void testGetImportedEntityWithWrongId() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        ContentInfo output = mapper.getImportedEntity("wrong_id");
        assertNull(output);
    }

    @Test
    public void testGetImportedEntityWithEmptyId() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        ContentInfo output = mapper.getImportedEntity("");
        assertNull(output);
    }

    @Test
    public void testGetImportedEntityWithNullId() {
        ContentInfo imported = TestUtil.createContent("test_id");

        ContentMapper mapper = new ContentMapper();

        // Verify that the entity was added
        boolean result = mapper.addImportedEntity(imported);
        assertTrue(result);

        // Verify the output is null, since this ID shouldn't exist
        ContentInfo output = mapper.getImportedEntity(null);
        assertNull(output);
    }

    @Test
    public void testGetImportedEntities() {
        ContentInfo imported1 = TestUtil.createContent("test_id-1");
        ContentInfo imported2 = TestUtil.createContent("test_id-2");
        ContentInfo imported3 = TestUtil.createContent("test_id-3");

        ContentMapper mapper = new ContentMapper();

        // Verify the initial state is an empty map
        Map<String, ContentInfo> output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(0, output.size());

        boolean result = mapper.addImportedEntity(imported1);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(1, output.size());
        assertThat(output, hasEntry(imported1.getId(), imported1));

        // Add another element
        result = mapper.addImportedEntity(imported2);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(2, output.size());
        assertThat(output, hasEntry(imported1.getId(), imported1));
        assertThat(output, hasEntry(imported2.getId(), imported2));

        // Add another element
        result = mapper.addImportedEntity(imported3);
        assertTrue(result);

        output = mapper.getImportedEntities();

        assertNotNull(output);
        assertEquals(3, output.size());
        assertThat(output, hasEntry(imported1.getId(), imported1));
        assertThat(output, hasEntry(imported2.getId(), imported2));
        assertThat(output, hasEntry(imported3.getId(), imported3));
    }

    @Test
    public void testHasEntityForMatchingExistingEntity() {
        String id = "test_id";
        Content existing = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent("different_id");

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        ContentInfo imported = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent(id);
        ContentInfo imported = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent(id);
        ContentInfo imported = TestUtil.createContent("different_id");

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent("different_id");
        ContentInfo imported = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent("different_id");
        ContentInfo imported = TestUtil.createContent("another_id");

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent(id);
        ContentInfo imported = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        String id = "test_id";
        Content existing = TestUtil.createContent(id);
        ContentInfo imported = TestUtil.createContent(id);

        ContentMapper mapper = new ContentMapper();

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
        // Test set should include 2 unique existing, 2 unique imported, and 2 shared
        Content existing1 = TestUtil.createContent("existing-1");
        Content existing2 = TestUtil.createContent("existing-2");
        Content existing3 = TestUtil.createContent("shared_id-1");
        Content existing4 = TestUtil.createContent("shared_id-2");
        ContentInfo imported1 = TestUtil.createContent("imported-1");
        ContentInfo imported2 = TestUtil.createContent("imported-2");
        ContentInfo imported3 = TestUtil.createContent("shared_id-1");
        ContentInfo imported4 = TestUtil.createContent("shared_id-2");

        ContentMapper mapper = new ContentMapper();

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
        assertThat(ids, hasItems(existing1.getId(), existing2.getId(), existing3.getId(), existing4.getId()));
        assertThat(ids, hasItems(imported1.getId(), imported2.getId(), imported3.getId(), imported4.getId()));
    }


    private Map<String, Set<Content>> buildCandidateEntitiesMap(int ids, int candidatesPerId, String prefix) {
        Map<String, Set<Content>> ceMap = new HashMap<>();

        for (int i = 0; i < ids; ++i) {
            String id = String.format("%s-%d", prefix, i);
            Set<Content> candidates = new HashSet<>();

            for (int c = 0; c < candidatesPerId; ++c) {
                Content candidate = TestUtil.createContent(id, String.format("%s-%d.%d", prefix, i, c));

                candidates.add(candidate);
            }

            ceMap.put(id, candidates);
        }

        return ceMap;
    }


    @Test
    public void testSetCandidateEntities() {
        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        boolean result = mapper.setCandidateEntitiesMap(ceMap);
        assertTrue(result);

        // Repeated sets should be false
        result = mapper.setCandidateEntitiesMap(ceMap);
        assertFalse(result);
    }

    @Test
    public void testGetCandidateEntities() {
        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        boolean result = mapper.setCandidateEntitiesMap(ceMap);
        assertTrue(result);

        // Ensure that for each key in the provided map, we get the expected set back out.
        for (Map.Entry<String, Set<Content>> entry : ceMap.entrySet()) {
            String key = entry.getKey();
            Set<Content> expected = entry.getValue();

            Set<Content> output = mapper.getCandidateEntities(key);

            assertNotNull(output);
            assertEquals(expected.size(), output.size());

            for (Content entity : expected) {
                assertThat(output, hasItem(entity));
            }
        }
    }

    @Test
    public void testGetCandidateEntitiesWithCandidatelessId() {
        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        boolean result = mapper.setCandidateEntitiesMap(ceMap);
        assertTrue(result);

        Set<Content> output = mapper.getCandidateEntities("candidateless_id");

        assertNull(output);
    }


    @Test
    public void testGetCandidateEntitiesWithNullId() {
        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        boolean result = mapper.setCandidateEntitiesMap(ceMap);
        assertTrue(result);

        Set<Content> output = mapper.getCandidateEntities(null);

        assertNull(output);
    }

    @Test
    public void testGetCandidateEntitiesWithEmptyId() {
        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        boolean result = mapper.setCandidateEntitiesMap(ceMap);
        assertTrue(result);

        Set<Content> output = mapper.getCandidateEntities("");

        assertNull(output);
    }

    @Test
    public void testClear() {
        Content existing = TestUtil.createContent("test_id-1");
        ContentInfo imported = TestUtil.createContent("test_id-2");

        Map<String, Set<Content>> ceMap = this.buildCandidateEntitiesMap(3, 3, "candidate");

        ContentMapper mapper = new ContentMapper();

        assertTrue(mapper.addExistingEntity(existing));
        assertTrue(mapper.addImportedEntity(imported));
        assertTrue(mapper.setCandidateEntitiesMap(ceMap));

        // Verify our state includes some data presence
        assertEquals(existing, mapper.getExistingEntity(existing.getId()));
        assertEquals(imported, mapper.getImportedEntity(imported.getId()));
        assertNotNull(mapper.getCandidateEntities("candidate-1"));
        assertTrue(mapper.hasEntity(existing.getId()));
        assertTrue(mapper.hasEntity(imported.getId()));

        // Clear the state and re-verify
        mapper.clear();

        assertNull(mapper.getExistingEntity(existing.getId()));
        assertNull(mapper.getImportedEntity(imported.getId()));
        assertNull(mapper.getCandidateEntities("candidate-1"));
        assertFalse(mapper.hasEntity(existing.getId()));
        assertFalse(mapper.hasEntity(imported.getId()));
    }
}
