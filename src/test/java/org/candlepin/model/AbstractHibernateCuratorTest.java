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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.candlepin.auth.Principal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.LockModeType;
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
        public E lockAndLoad(Class<E> entityClass, Serializable id) {
            return super.lockAndLoad(entityClass, id);
        }

        @Override
        public List<E> lockAndLoad(Class<E> entityClass, Iterable<? extends Serializable> ids) {
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

    @BeforeEach
    public void setup() {
        this.testOwnerCurator = new TestHibernateCurator<>(Owner.class);
        this.testContentCurator = new TestHibernateCurator<>(Content.class);
        this.testEnvironmentCurator = new TestHibernateCurator<>(Environment.class);

        this.injectMembers(this.testOwnerCurator);
        this.injectMembers(this.testContentCurator);
        this.injectMembers(this.testEnvironmentCurator);
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
        Content content = this.createContent("c1", "content-1");

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
        Content content = this.createContent("c1", "content-1");

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
    public void testLockAndLoadMultiIdDoesNotReturnNullValuesForInvalidIds() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        List<String> input = List.of(owner1.getId(), "invalid owner ID", owner3.getId());

        List<Owner> output = this.testOwnerCurator.lockAndLoad(input);

        assertThat(output)
            .isNotNull()
            .hasSize(2)
            .contains(owner1, owner3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testLockAndLoadMultiIdDoesNotFailWithNullOrEmptyInput(Collection<String> ids) {
        List<Owner> output = this.testOwnerCurator.lockAndLoad(ids);

        assertThat(output)
            .isNotNull()
            .isEmpty();
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
    public void testLockAndLoadMultiIdWithClassDoesNotReturnNullValuesForInvalidIds() {
        Owner owner1 = this.createOwner("owner_key-1", "owner-1");
        Owner owner2 = this.createOwner("owner_key-2", "owner-2");
        Owner owner3 = this.createOwner("owner_key-3", "owner-3");

        List<String> input = List.of(owner1.getId(), "invalid owner ID", owner3.getId());

        List<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, input);

        assertThat(output)
            .isNotNull()
            .hasSize(2)
            .contains(owner1, owner3);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testLockAndLoadMultiIdWithClassDoesNotFailWithNullOrEmptyInput(Collection<String> ids) {
        List<Owner> output = this.testOwnerCurator.lockAndLoad(Owner.class, ids);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetPrimaryKeyName() {
        String environmentPrimaryKeyName = this.testEnvironmentCurator.getPrimaryKeyName();
        String ownerPrimaryKeyName = this.testOwnerCurator.getPrimaryKeyName();

        assertEquals("id", environmentPrimaryKeyName);
        assertEquals("id", ownerPrimaryKeyName);
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
            (new LinkedList<>());

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
            assertThat(result.getExpressions()).contains(permissionPredicate);
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
        assertThat(result.getExpressions()).contains(permissionPredicate1);
        assertThat(result.getExpressions()).contains(permissionPredicate2);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @EnumSource(value = LockModeType.class, names = { "PESSIMISTIC_READ", "PESSIMISTIC_WRITE" })
    public void testGetSystemLock(LockModeType lockMode) {
        String lockId = "test_lock";

        this.ownerCurator.getSystemLock(lockId, lockMode);

        SystemLock lock = this.getEntityManager()
            .find(SystemLock.class, lockId);

        assertNotNull(lock);
        assertEquals(lockMode, this.getEntityManager().getLockMode(lock));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @EnumSource(value = LockModeType.class, names = { "PESSIMISTIC_READ", "PESSIMISTIC_WRITE" })
    public void testGetSystemLockIsReentrant(LockModeType lockMode) {
        this.ownerCurator.getSystemLock("test_lock", lockMode);
        this.ownerCurator.getSystemLock("test_lock", lockMode);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @EnumSource(value = LockModeType.class, names = { "PESSIMISTIC_READ", "PESSIMISTIC_WRITE" },
        mode = EnumSource.Mode.EXCLUDE)
    public void testGetSystemLockRequiresPessimisticLock(LockModeType lockMode) {
        assertThrows(IllegalArgumentException.class, () -> this.ownerCurator
            .getSystemLock("test_lock", lockMode));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetSystemLockRequiresLockName(String lockName) {
        assertThrows(IllegalArgumentException.class, () -> this.ownerCurator
            .getSystemLock(lockName, LockModeType.PESSIMISTIC_READ));
    }

    @Test
    public void testGetSystemLockRequiresLockMode() {
        assertThrows(IllegalArgumentException.class, () -> this.ownerCurator
            .getSystemLock("test_lock", null));
    }

    @Test
    public void testTakeSubListWithQueryArgumentAndNullOffset() {
        QueryArguments argument = new QueryArguments<>();
        argument.setLimit(1);

        List<Owner> expected = List.of(new Owner());

        List<Owner> actual = this.ownerCurator.takeSubList(argument, expected);

        assertEquals(expected, actual);
    }

    @Test
    public void testTakeSubListWithQueryArgumentAndNullLimit() {
        QueryArguments argument = new QueryArguments<>();
        argument.setOffset(1);

        List<Owner> expected = List.of(new Owner());

        List<Owner> actual = this.ownerCurator.takeSubList(argument, expected);

        assertEquals(expected, actual);
    }

    @Test
    public void testTakeSubListWithQueryArgumentAndEmptyResults() {
        QueryArguments argument = new QueryArguments<>()
            .setOffset(1)
            .setLimit(10);

        List<Owner> actual = this.ownerCurator.takeSubList(argument, new ArrayList<>());

        assertEquals(0, actual.size());
    }

    @Test
    public void testTakeSubListWithQueryArgumentAndNullResults() {
        QueryArguments argument = new QueryArguments<>()
            .setOffset(1)
            .setLimit(10);

        List<Owner> actual = this.ownerCurator.takeSubList(argument, null);

        assertNull(actual);
    }

    @Test
    public void testTakeSubListWithQueryArgument() {
        QueryArguments argument = new QueryArguments<>()
            .setOffset(1)
            .setLimit(3);

        Owner owner1 = TestUtil.createOwner();
        Owner owner2 = TestUtil.createOwner();
        Owner owner3 = TestUtil.createOwner();
        Owner owner4 = TestUtil.createOwner();
        Owner owner5 = TestUtil.createOwner();
        Owner owner6 = TestUtil.createOwner();

        List<Owner> owners = new LinkedList<>();
        owners.add(owner1);
        owners.add(owner2);
        owners.add(owner3);
        owners.add(owner4);
        owners.add(owner5);
        owners.add(owner6);

        List<Owner> actual = this.ownerCurator.takeSubList(argument, owners);

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrder(owner1, owner2, owner3);
    }

    @Test
    public void testBuildQueryArgumentInPredicateWithNullPath() {
        Optional<Predicate> actual = ownerCurator
            .buildQueryArgumentInPredicate(null, List.of(TestUtil.randomString()));

        assertThat(actual)
            .isEmpty();
    }

    @Test
    public void testBuildQueryArgumentInPredicateWithNullCollection() {
        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> query = builder.createQuery(Owner.class);
        Root<Owner> root = query.from(Owner.class);

        Optional<Predicate> actual = ownerCurator.buildQueryArgumentInPredicate(root, null);

        assertThat(actual)
            .isEmpty();
    }

    @Test
    public void testBuildQueryArgumentInPredicateWithEmptyCollection() {
        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> query = builder.createQuery(Owner.class);
        Root<Owner> root = query.from(Owner.class);

        Optional<Predicate> actual = ownerCurator.buildQueryArgumentInPredicate(root, List.of());

        assertThat(actual)
            .isEmpty();
    }

    @Test
    public void testBuildQueryArgumentInPredicate() {
        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> query = builder.createQuery(Owner.class);
        Root<Owner> root = query.from(Owner.class);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(TestUtil.randomString());
        }

        Optional<Predicate> actual = ownerCurator.buildQueryArgumentInPredicate(root, ids);
        assertThat(actual)
            .isNotEmpty()
            .containsInstanceOf(Predicate.class);
    }

    @Test
    public void testBuildQueryArgumentInPredicateWithCollectionExceedingMaxSize() {
        CriteriaBuilder builder = this.getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Owner> query = builder.createQuery(Owner.class);
        Root<Owner> root = query.from(Owner.class);

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < QueryArguments.COLLECTION_SIZE_LIMIT + 10; i++) {
            ids.add(TestUtil.randomString());
        }

        assertThrows(IllegalArgumentException.class, () -> ownerCurator
            .buildQueryArgumentInPredicate(root, ids));
    }

}
