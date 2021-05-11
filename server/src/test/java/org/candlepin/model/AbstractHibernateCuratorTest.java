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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.Principal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;



/**
 * AbstractHibernateCuratorTest
 */
// Allow a non-static MethodSource
@TestInstance(Lifecycle.PER_CLASS)
public class AbstractHibernateCuratorTest extends DatabaseTestFixture {
    /**
     * Test implementation that provides access to some protected methods
     */
    private static class TestHibernateCurator<E extends Persisted> extends AbstractHibernateCurator<E> {
        public TestHibernateCurator(Class entityClass) {
            super(entityClass);
        }

        @Override
        public int bulkSQLUpdate(String table, String column, Map<Object, Object> values,
            Map<String, Object> criteria) {

            return super.bulkSQLUpdate(table, column, values, criteria);
        }

        @Override
        public E lockAndLoad(Class<E> entityClass, Serializable id) {
            return super.lockAndLoad(entityClass, id);
        }

        @Override
        public Collection<E> lockAndLoad(Class<E> entityClass, Iterable<? extends Serializable> ids) {
            return super.lockAndLoad(entityClass, ids);
        }

        @Override
        public boolean checkQueryArgumentCollection(Collection<?> collection) {
            return super.checkQueryArgumentCollection(collection);
        }

        @Override
        public <T> Predicate getSecurityPredicate(Class<T> entityClass, CriteriaBuilder builder,
            From<?, T> root) {
            return super.getSecurityPredicate(entityClass, builder, root);
        }
    }

    AbstractHibernateCurator<Owner> testOwnerCurator;
    AbstractHibernateCurator<Content> testContentCurator;
    AbstractHibernateCurator<Environment> testEnvironmentCurator;
    AbstractHibernateCurator<OwnerProduct> testOwnerProductCurator;

    @BeforeEach
    public void setup() {
        this.testOwnerCurator = new TestHibernateCurator<>(Owner.class);
        this.testContentCurator = new TestHibernateCurator<>(Content.class);
        this.testEnvironmentCurator = new TestHibernateCurator<>(Environment.class);
        this.testOwnerProductCurator = new TestHibernateCurator<>(OwnerProduct.class);
        this.injectMembers(this.testOwnerCurator);
        this.injectMembers(this.testContentCurator);
        this.injectMembers(this.testEnvironmentCurator);
        this.injectMembers(this.testOwnerProductCurator);
    }

    @Test
    public void testBulkSQLUpdate() throws Exception {
        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();
        values.put("c1", "c1updated");
        values.put("c2", "c2updated");
        values.put("content ?", "should not exist");

        int result = this.cdnCurator.bulkSQLUpdate(Cdn.DB_TABLE, "name", values, null);

        // Note:
        // This looks like it should be 2, and technically that's what's happening here, but with
        // the way the bulk updater works, even the non-matching columns are getting updated to
        // themselves.
        assertEquals(3, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("c1updated", c1.getName());
        assertEquals("c2updated", c2.getName());
        assertEquals("c3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateSingleUpdate() throws Exception {

        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();
        values.put("http://url1.com", "http://url1Updated.com");

        int result = this.cdnCurator.bulkSQLUpdate(Cdn.DB_TABLE, "url", values, null);

        assertEquals(1, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("http://url1Updated.com", c1.getUrl());
        assertEquals("http://url2.com", c2.getUrl());
        assertEquals("http://url3.com", c3.getUrl());
    }

    @Test
    public void testBulkSQLUpdateSingleUpdateNoChange() throws Exception {
        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();
        values.put("content B", "update 1");

        int result = this.cdnCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);

        assertEquals(0, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("c1", c1.getName());
        assertEquals("c2", c2.getName());
        assertEquals("c3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithEmptyValues() throws Exception {
        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();

        int result = this.cdnCurator.bulkSQLUpdate(Cdn.DB_TABLE, "name", values, null);

        assertEquals(0, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("c1", c1.getName());
        assertEquals("c2", c2.getName());
        assertEquals("c3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithSingleCriteria() {
        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();
        values.put("c1", "c1a");
        values.put("c2", "c2a");
        values.put("c4", "c4a");

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("name", values.keySet());

        int result = this.cdnCurator.bulkSQLUpdate(Cdn.DB_TABLE, "name", values, criteria);

        // Unlike the base test where the result count is 3, this filters by only the values we
        // intend to update, so it should be 2.
        assertEquals(2, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("c1a", c1.getName());
        assertEquals("c2a", c2.getName());
        assertEquals("c3", c3.getName());
    }

    @Test
    public void testBulkSQLUpdateWithMultipleCriteria() {
        Cdn c1 = this.createCdn("c1", "http://url1.com");
        Cdn c2 = this.createCdn("c2", "http://url2.com");
        Cdn c3 = this.createCdn("c3", "http://url3.com");

        Map<Object, Object> values = new HashMap<>();
        values.put("c1", "update 1");
        values.put("c2", "update 2");
        values.put("content ?", "should not exist");

        Map<String, Object> criteria = new HashMap<>();
        criteria.put("name", values.keySet());
        criteria.put("id", c2.getId());

        int result = this.cdnCurator.bulkSQLUpdate(Cdn.DB_TABLE, "name", values, criteria);

        // Unlike the base test where the result count is 3, this filters by only the values we
        // intend to update, so it should be 1.
        assertEquals(1, result);

        this.getEntityManager().refresh(c1);
        this.getEntityManager().refresh(c2);
        this.getEntityManager().refresh(c3);

        assertEquals("c1", c1.getName());
        assertEquals("update 2", c2.getName());
        assertEquals("c3", c3.getName());
    }

    protected Stream<Object[]> largeValueSetSizes() {
        int caseBlockSize = getConfigForParameters().getInt(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE);

        return Stream.of(
            new Object[] { (int) (caseBlockSize), 0 },
            new Object[] { (int) (caseBlockSize + 1), 0 },
            new Object[] { (int) (caseBlockSize * 1.5), 0 },
            new Object[] { (int) (caseBlockSize * 1.5 + 1), 0 },
            new Object[] { (int) (caseBlockSize * 2), 0 },
            new Object[] { (int) (caseBlockSize * 2 + 1), 0 },
            new Object[] { (int) (caseBlockSize * 2.5), 0 },
            new Object[] { (int) (caseBlockSize * 2.5 + 1), 0 },
            new Object[] { (int) (caseBlockSize * 3), 0 },
            new Object[] { (int) (caseBlockSize * 3 + 1), 0 },
            new Object[] { (int) (caseBlockSize * 3.5), 0 },
            new Object[] { (int) (caseBlockSize * 3.5 + 1), 0 },

            new Object[] { (int) (caseBlockSize), 1 },
            new Object[] { (int) (caseBlockSize + 1), 1 },
            new Object[] { (int) (caseBlockSize * 1.5), 1 },
            new Object[] { (int) (caseBlockSize * 1.5 + 1), 1 },
            new Object[] { (int) (caseBlockSize * 2), 1 },
            new Object[] { (int) (caseBlockSize * 2 + 1), 1 },
            new Object[] { (int) (caseBlockSize * 2.5), 1 },
            new Object[] { (int) (caseBlockSize * 2.5 + 1), 1 },
            new Object[] { (int) (caseBlockSize * 3), 1 },
            new Object[] { (int) (caseBlockSize * 3 + 1), 1 },
            new Object[] { (int) (caseBlockSize * 3.5), 1 },
            new Object[] { (int) (caseBlockSize * 3.5 + 1), 1 }
        );
    }

    @ParameterizedTest
    @MethodSource("largeValueSetSizes")
    public void testBulkSQLUpdateWithLargeValueSets(int count, int skip) {
        Owner owner = this.createOwner();

        for (int i = 1; i <= count; ++i) {
            this.createContent("c" + i, "content-" + i, owner);
        }

        Map<Object, Object> values = new LinkedHashMap<>();

        for (int i = 1; i <= count; ++i) {
            // We want every odd value to be unaffected, but we still want a fake update entry
            // for the query
            values.put("content-" + (i % 2 == skip ? i : "X" + i), "update-" + i);
        }

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, null);
        assertEquals(count, result);

        testContentCurator.clear();

        for (int i = 1; i <= count; ++i) {
            Content content = this.ownerContentCurator.getContentById(owner, "c" + i);

            if (i % 2 == skip) {
                assertEquals("update-" + i, content.getName());
            }
            else {
                assertEquals("content-" + i, content.getName());
            }
        }
    }

    protected Object[] largeValueSetAndCriteriaSizes() {
        List<Object[]> entries = new LinkedList<>();

        // Declaring these as variables because the constant names are loooooooong
        int caseBlockSize = getConfigForParameters().getInt(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE);
        int inBlockSize = getConfigForParameters().getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE);

        for (float multi = 1; multi < 4.0f; multi += 0.5) {
            entries.add(new Object[] { (int) (caseBlockSize * multi), (int) (inBlockSize * multi) });
            entries.add(new Object[] { (int) (caseBlockSize * multi + 1), (int) (inBlockSize * multi + 1) });
        }

        return entries.toArray();
    }

    @ParameterizedTest
    @MethodSource("largeValueSetAndCriteriaSizes")
    public void testBulkSQLUpdateWithLargeValueSetAndCriteriaList(int valueCount, int criteriaListSize) {
        Owner owner = this.createOwner();

        Map<Object, Object> values = new HashMap<>();

        for (int i = 1; i <= valueCount; ++i) {
            this.createContent("c" + i, "content-" + i, owner);

            // We want every odd value to be unaffected, but we still want a fake update entry
            // for the query
            values.put("content-" + (i % 2 == 0 ? i : "X" + i), "update-" + i);
        }

        Map<String, Object> criteria = new HashMap<>();
        List<String> valueList = new LinkedList<>();
        criteria.put("name", valueList);

        for (int i = 1; i <= criteriaListSize; ++i) {
            valueList.add("content-" + (i % 2 == 0 ? i : "X" + i));
        }

        int result = this.testContentCurator.bulkSQLUpdate(Content.DB_TABLE, "name", values, criteria);
        assertEquals(valueCount / 2, result);

        testContentCurator.clear();

        for (int i = 1; i <= valueCount; ++i) {
            Content content = this.ownerContentCurator.getContentById(owner, "c" + i);

            if (i % 2 == 0) {
                assertEquals("update-" + i, content.getName());
            }
            else {
                assertEquals("content-" + i, content.getName());
            }
        }
    }

    @Test
    public void testLockAndLoadWithSingleId() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        this.testOwnerCurator.flush();
        this.testOwnerCurator.clear();

        // Verify that we're getting an equal entity back out
        Owner output = this.testOwnerCurator.lockAndLoad(owner.getId());
        assertEquals(owner, output);
    }

    @Test
    public void testLockAndLoadWithSingleIdUsesCache() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify that we're getting an equal entity back out
        Owner output = this.testOwnerCurator.lockAndLoad(owner.getId());
        assertSame(owner, output);
    }

    @Test
    public void testLockAndLoadWithSingleIdDoesNotRevertPropertyChange() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner = this.createOwner("owner_key-1", "owner-1");

        owner.setDisplayName("changed_name");
        this.testOwnerCurator.lockAndLoad(owner.getId());
        assertEquals("changed_name", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdRetainsFlushedChanged() {
        Owner owner = this.createOwner();
        Content content = this.createContent("c1", "content-1", owner);

        // Verify that a flush will make the change persistent
        content.setName("changed_name");
        testContentCurator.merge(content);
        testContentCurator.flush();
        this.testContentCurator.lockAndLoad(content.getId());
        assertEquals("changed_name", content.getName());
    }

    @Test
    public void testLockAndLoadWithSingleIdDoesNotRevertUnflushedMerge() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify that even a pending merge will be reverted
        owner.setDisplayName("changed_name");
        testOwnerCurator.merge(owner);
        this.testOwnerCurator.lockAndLoad(owner.getId());
        assertEquals("changed_name", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdIgnoresEvicted() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify evicted/detached elements aren't affected
        owner.setDisplayName("detached");
        testOwnerCurator.evict(owner);
        Owner output = this.testOwnerCurator.lockAndLoad(owner.getId());
        assertNotNull(output);
        assertNotEquals(owner, output);
        assertEquals("owner-1", output.getName());
        assertEquals("detached", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdHandlesDeleted() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify evicted/detached elements aren't affected
        owner.setDisplayName("deleted");
        testOwnerCurator.delete(owner);

        Owner output = this.testOwnerCurator.lockAndLoad(owner.getId());
        assertNull(output);
    }

    @Test
    public void testLockAndLoadWithSingleIdWithClassAndId() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify that we're getting an equal entity back out
        Owner output = this.testOwnerCurator.lockAndLoad(Owner.class, owner.getId());
        assertEquals(owner, output);
    }

    @Test
    public void testLockAndLoadWithSingleIdWithClassAndIdDoesNotRevertPropertyChange() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify that lockAndLoad's refresh reverts our name change
        owner.setDisplayName("changed_name");
        this.testOwnerCurator.lockAndLoad(Owner.class, owner.getId());
        assertEquals("changed_name", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdWithClassAndIdDoesNotRevertUnflushedMerge() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify that even a pending merge will be reverted
        owner.setDisplayName("changed_name");
        testOwnerCurator.merge(owner);
        this.testOwnerCurator.lockAndLoad(Owner.class, owner.getId());
        assertEquals("changed_name", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdWithClassAndIdIgnoresEvicted() {
        Owner owner = this.createOwner("owner_key-1", "owner-1");

        // Verify evicted/detached elements aren't affected
        owner.setDisplayName("detached");
        testOwnerCurator.evict(owner);
        Owner output = this.testOwnerCurator.lockAndLoad(Owner.class, owner.getId());
        assertNotNull(output);
        assertNotEquals(owner, output);
        assertEquals("owner-1", output.getDisplayName());
        assertEquals("detached", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadWithSingleIdWithClassAndIdRetainsFlushedChanged() {
        Owner owner = this.createOwner();
        Content content = this.createContent("c1", "content-1", owner);

        // Verify that a flush will make the change persistent
        owner.setDisplayName("changed_name");
        testOwnerCurator.merge(owner);
        testOwnerCurator.flush();
        this.testOwnerCurator.lockAndLoad(Owner.class, owner.getId());
        assertEquals("changed_name", owner.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiId() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify we're getting the correct number of entities out
        Collection<String> input = Arrays.asList(owner1.getId(), owner2.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertEquals(3, output.size());

        // Note: the instances may be different here, but as long as they're equal (including UUID),
        // we're okay.
        for (Owner expected : Arrays.asList(owner1, owner2, owner3)) {
            boolean found = false;

            for (Owner owner : output) {
                if (expected.equals(owner)) {
                    assertFalse(found);
                    assertEquals(expected.getId(), owner.getId());
                    found = true;

                    // We don't break here because we're verifying we didn't receive any duplicates.
                }
            }

            assertTrue(found, "expected entity was not found in output: " + expected.getId());
        }
    }

    @Test
    public void testLockAndLoadMultiIdDoesNotRevertPropertyChange() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that lockAndLoad's refresh reverts our name changes only where applicable
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdDoesNotRevertUnflushedMerge() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that even a pending merge will be reverted
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.merge(owner1);
        this.testOwnerCurator.merge(owner2);
        this.testOwnerCurator.merge(owner3);

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdRetainsFlushedChanged() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that a flush will make the change persistent
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.merge(owner1);
        this.testOwnerCurator.merge(owner2);
        this.testOwnerCurator.merge(owner3);
        this.testOwnerCurator.flush();

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdIgnoresEvicted() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify evicted/detached elements aren't affected
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.evict(owner1);
        this.testOwnerCurator.evict(owner2);
        this.testOwnerCurator.evict(owner3);

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertEquals(2, output.size());
        assertFalse(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertFalse(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());

        for (Owner entity : output) {
            assertTrue(entity.getDisplayName().matches("owner-\\d"));
        }
    }

    @Test
    public void testLockAndLoadMultiIdWithClassAndIds() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify we're getting the correct number of entities out
        Collection<String> input = Arrays.asList(owner1.getId(), owner2.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertEquals(3, output.size());

        // Note: the instances may be different here, but as long as they're equal (including UUID),
        // we're okay.
        for (Owner expected : Arrays.asList(owner1, owner2, owner3)) {
            boolean found = false;

            for (Owner owner : output) {
                if (expected.equals(owner)) {
                    assertFalse(found);
                    assertEquals(expected.getId(), owner.getId());
                    found = true;

                    // We don't break here because we're verifying we didn't receive any duplicates.
                }
            }

            assertTrue(found, "expected entity was not found in output: " + expected.getId());
        }
    }

    @Test
    public void testLockAndLoadMultiIdWithClassAndIdsDoesNotRevertPropertyChange() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that lockAndLoad's refresh reverts our name changes only where applicable
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdWithClassAndIdsDoesNotRevertUnflushedMerge() {
        // Note: this test is based on expected caching configurations and behaviors
        // within Hibernate. If these change, this test may need to be updated/dropped
        // accordingly.

        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that even a pending merge will be reverted
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.merge(owner1);
        this.testOwnerCurator.merge(owner2);
        this.testOwnerCurator.merge(owner3);

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdWithClassAndIdsRetainsFlushedChanged() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify that a flush will make the change persistent
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.merge(owner1);
        this.testOwnerCurator.merge(owner2);
        this.testOwnerCurator.merge(owner3);
        this.testOwnerCurator.flush();

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertEquals(2, output.size());
        assertTrue(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertTrue(output.contains(owner3));
        assertEquals("name change 1", owner1.getDisplayName());
        assertEquals("name change 2", owner2.getDisplayName());
        assertEquals("name change 3", owner3.getDisplayName());
    }

    @Test
    public void testLockAndLoadMultiIdWithClassAndIdsIgnoresEvicted() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        // Verify evicted/detached elements aren't affected
        owner1.setDisplayName("name change 1");
        owner2.setDisplayName("name change 2");
        owner3.setDisplayName("name change 3");
        this.testOwnerCurator.evict(owner1);
        this.testOwnerCurator.evict(owner2);
        this.testOwnerCurator.evict(owner3);

        Collection<String> input = Arrays.asList(owner1.getId(), owner3.getId());
        Collection<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertEquals(2, output.size());
        assertFalse(output.contains(owner1));
        assertFalse(output.contains(owner2));
        assertFalse(output.contains(owner3));
        assertEquals("name change 1", owner1.getName());
        assertEquals("name change 2", owner2.getName());
        assertEquals("name change 3", owner3.getName());

        for (Owner entity : output) {
            assertTrue(entity.getName().matches("owner-\\d"));
        }
    }

    @Test
    public void testGetPrimaryKeyName() {
        String environmentPrimaryKeyName = this.testEnvironmentCurator.getPrimaryKeyName();
        String ownerPrimaryKeyName = this.testOwnerCurator.getPrimaryKeyName();

        assertEquals("id", environmentPrimaryKeyName);
        assertEquals("id", ownerPrimaryKeyName);
    }

    @Test
    public void testGetPrimaryKeyNameOnCompositeKey() {
        assertNull(this.testOwnerProductCurator.getPrimaryKeyName());
    }

    @Test
    public void testExists() {
        Owner owner = this.createOwner("ownerPrimaryKey", "owner-1");
        Environment environment = this.createEnvironment(owner,
            "SomeId", "fooBar", null, null, null);

        assertTrue(this.testOwnerCurator.exists(owner.getId()));
        assertFalse(this.testOwnerCurator.exists("randomOwnerId"));
        assertTrue(this.environmentCurator.exists(environment.getId()));
        assertFalse(this.environmentCurator.exists("randomEnvId"));
    }

    @Test
    public void testCheckQueryArgumentCollection() {
        List<Double> collection = Stream.generate(Math::random)
            .limit(5)
            .collect(Collectors.toList());

        TestHibernateCurator<?> curator = new TestHibernateCurator<>(Owner.class);
        boolean result = curator.checkQueryArgumentCollection(collection);

        assertTrue(result);
    }

    @Test
    public void testCheckQueryArgumentCollectionWithNull() {
        TestHibernateCurator<?> curator = new TestHibernateCurator<>(Owner.class);
        boolean result = curator.checkQueryArgumentCollection(null);

        assertFalse(result);
    }

    @Test
    public void testCheckQueryArgumentCollectionWithEmpty() {
        List<?> collection = Collections.emptyList();

        TestHibernateCurator<?> curator = new TestHibernateCurator<>(Owner.class);
        boolean result = curator.checkQueryArgumentCollection(collection);

        assertFalse(result);
    }

    @Test
    public void testCheckQueryArgumentCollectionFailsWithTooManyElements() {
        List<Double> collection = Stream.generate(Math::random)
            .limit(QueryArguments.COLLECTION_SIZE_LIMIT + 1)
            .collect(Collectors.toList());

        TestHibernateCurator<?> curator = new TestHibernateCurator<>(Owner.class);
        assertThrows(IllegalArgumentException.class, () -> curator.checkQueryArgumentCollection(collection));
    }

    @Test
    public void testGetSecurityPredicateWithNoPrincipal() {
        TestHibernateCurator<?> curator = spy(new TestHibernateCurator<>(Owner.class));
        doReturn(null).when(curator).getPrincipal();

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> criteriaQuery = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> root = criteriaQuery.from(Owner.class);

        Predicate result = curator.getSecurityPredicate(Owner.class, criteriaBuilder, root);

        assertNull(result);
    }

    private Principal mockPrincipal(boolean superAdmin, Permission... permissions) {
        Principal principal = mock(Principal.class);

        List<Permission> plist = permissions != null && permissions.length > 0 ?
            Arrays.asList(permissions) :
            new LinkedList<>();

        doReturn(superAdmin).when(principal).hasFullAccess();
        doReturn(plist).when(principal).getPermissions();

        return principal;
    }

    @Test
    public void testGetSecurityPredicateWithSuperAdminPrincipal() {
        Principal mockPrincipal = this.mockPrincipal(true);

        TestHibernateCurator<?> curator = spy(new TestHibernateCurator<>(Owner.class));
        this.injectMembers(curator);
        doReturn(mockPrincipal).when(curator).getPrincipal();

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> criteriaQuery = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> root = criteriaQuery.from(Owner.class);

        Predicate result = curator.getSecurityPredicate(Owner.class, criteriaBuilder, root);

        assertNull(result);
    }

    @Test
    public void testGetSecurityPredicateWithNoRestrictivePermissions() {
        Permission mockPermission1 = mock(Permission.class);
        Permission mockPermission2 = mock(Permission.class);
        // impl note: we don't need to do anything more with these permission mocks, as the
        // default non-restrictive result is null already.
        Principal mockPrincipal = this.mockPrincipal(false, mockPermission1, mockPermission2);

        TestHibernateCurator<?> curator = spy(new TestHibernateCurator<>(Owner.class));
        this.injectMembers(curator);
        doReturn(mockPrincipal).when(curator).getPrincipal();

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> criteriaQuery = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> root = criteriaQuery.from(Owner.class);

        Predicate result = curator.getSecurityPredicate(Owner.class, criteriaBuilder, root);

        assertNull(result);
    }

    @Test
    public void testGetSecurityPredicateWithSingleRestrictivePermission() {
        Permission mockPermission1 = mock(Permission.class);
        Permission mockPermission2 = mock(Permission.class);
        Principal mockPrincipal = this.mockPrincipal(false, mockPermission1, mockPermission2);

        TestHibernateCurator<?> curator = spy(new TestHibernateCurator<>(Owner.class));
        this.injectMembers(curator);
        doReturn(mockPrincipal).when(curator).getPrincipal();

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> criteriaQuery = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> root = criteriaQuery.from(Owner.class);

        Predicate permissionPredicate = criteriaBuilder.equal(root.get(Owner_.key), "some key");

        doReturn(permissionPredicate).when(mockPermission2)
            .getQueryRestriction(Owner.class, criteriaBuilder, root);

        Predicate result = curator.getSecurityPredicate(Owner.class, criteriaBuilder, root);

        assertNotNull(result);

        if (result != permissionPredicate) {
            // If the predicate wasn't passed through, it needs to be wrapped in an OR like
            // the multi-predicate case.
            assertEquals(Predicate.BooleanOperator.OR, result.getOperator());
            assertEquals(1, result.getExpressions().size());
            assertThat(result.getExpressions(), hasItem(permissionPredicate));
        }
        else {
            // In this single-predicate case, that one predicate being passed through is valid
        }
    }

    @Test
    public void testGetSecurityPredicateWithMultipleRestrictivePermissions() {
        Permission mockPermission1 = mock(Permission.class);
        Permission mockPermission2 = mock(Permission.class);
        Principal mockPrincipal = this.mockPrincipal(false, mockPermission1, mockPermission2);

        TestHibernateCurator<?> curator = spy(new TestHibernateCurator<>(Owner.class));
        this.injectMembers(curator);
        doReturn(mockPrincipal).when(curator).getPrincipal();

        CriteriaBuilder criteriaBuilder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> criteriaQuery = criteriaBuilder.createQuery(Owner.class);
        Root<Owner> root = criteriaQuery.from(Owner.class);

        Predicate permissionPredicate1 = criteriaBuilder.equal(root.get(Owner_.id), "some id");
        Predicate permissionPredicate2 = criteriaBuilder.equal(root.get(Owner_.key), "some key");

        doReturn(permissionPredicate1).when(mockPermission1)
            .getQueryRestriction(Owner.class, criteriaBuilder, root);
        doReturn(permissionPredicate2).when(mockPermission2)
            .getQueryRestriction(Owner.class, criteriaBuilder, root);

        Predicate result = curator.getSecurityPredicate(Owner.class, criteriaBuilder, root);

        assertNotNull(result);
        assertEquals(Predicate.BooleanOperator.OR, result.getOperator());
        assertEquals(2, result.getExpressions().size());
        assertThat(result.getExpressions(), hasItem(permissionPredicate1));
        assertThat(result.getExpressions(), hasItem(permissionPredicate2));
    }

}
