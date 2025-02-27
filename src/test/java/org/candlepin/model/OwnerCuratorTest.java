/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.controller.OwnerContentAccess;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.OwnerCurator.OwnerQueryArguments;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import javax.persistence.PersistenceException;
import javax.persistence.RollbackException;



public class OwnerCuratorTest extends DatabaseTestFixture {

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetByKeySecureWithInvalidKey(String key) {
        assertNull(this.ownerCurator.getByKeySecure(key));
    }

    @Test
    public void testGetByKeySecure() {
        Owner expected = this.createOwner();
        Owner owner2 = this.createOwner();

        User user = new User(TestUtil.randomString(), TestUtil.randomString());
        Set<Permission> perms = new HashSet<>();
        perms.add(new OwnerPermission(expected, Access.ALL));
        UserPrincipal principal = new UserPrincipal(user.getUsername(), perms, false);
        setupPrincipal(principal);

        Owner actual = this.ownerCurator.getByKeySecure(expected.getKey());

        assertThat(actual)
            .isNotNull()
            .isEqualTo(expected);

        // Verify that the user cannot access an owner that they do not have permissions for.
        assertNull(this.ownerCurator.getByKeySecure(owner2.getKey()));
    }

    @Test
    public void testGetByKeySecureWithUnknownOwner() {
        this.createOwner();

        assertNull(this.ownerCurator.getByKeySecure(TestUtil.randomString()));
    }

    @Test
    public void basicImport() {
        Owner owner = new Owner()
            .setKey("testing")
            .setDisplayName("testing");
        owner.setId("testing-primary-key");

        this.ownerCurator.replicate(owner);

        assertEquals("testing", this.ownerCurator.get("testing-primary-key").getKey());
    }

    @Test
    public void primaryKeyCollision() {
        Owner owner = new Owner()
            .setKey("dude")
            .setDisplayName("dude");
        owner = this.ownerCurator.create(owner);

        Owner newOwner = new Owner()
            .setKey("someoneElse")
            .setDisplayName("someoneElse");
        newOwner.setId(owner.getId());

        this.ownerCurator.replicate(newOwner);
        assertThrows(RollbackException.class, () -> this.commitTransaction());
    }

    @Test
    public void upstreamUuidConstraint() {
        UpstreamConsumer uc = new UpstreamConsumer("sameuuid");

        Owner owner1 = new Owner()
            .setKey("owner1")
            .setDisplayName("owner1");
        owner1.setUpstreamConsumer(uc);
        Owner owner2 = new Owner()
            .setKey("owner2")
            .setDisplayName("owner2");
        owner2.setUpstreamConsumer(uc);

        assertThrows(PersistenceException.class, () -> ownerCurator.create(owner1));
    }

    private void createAndConsumePool(Owner o, Product p) {
        Pool pool = TestUtil.createPool(o, p, 5);
        poolCurator.create(pool);

        Consumer c = createConsumer(o);
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(o, c, pool, cert);
        entitlementCurator.create(ent);
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void getByUpstreamUuidWithInvalidUuid(String uuid) {
        assertNull(ownerCurator.getByUpstreamUuid(uuid));
    }

    @Test
    public void getByUpstreamUuid() {
        Owner owner = new Owner()
            .setKey("owner1")
            .setDisplayName("owner1");

        owner = ownerCurator.create(owner);
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        consumerTypeCurator.create(type);
        UpstreamConsumer uc = new UpstreamConsumer("test-upstream-consumer", owner, type, "someuuid");
        owner.setUpstreamConsumer(uc);
        ownerCurator.merge(owner);

        Owner found = ownerCurator.getByUpstreamUuid("someuuid");

        assertNotNull(found);
        assertEquals(owner.getId(), found.getId());

        // Test with a non-existing upstream uuid
        assertNull(ownerCurator.getByUpstreamUuid(TestUtil.randomString()));
    }

    @Test
    public void getConsumerUuids() {
        ConsumerType type = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        consumerTypeCurator.create(type);

        Owner owner = new Owner()
            .setKey("owner")
            .setDisplayName("owner");
        Owner otherOwner = new Owner()
            .setKey("other owner")
            .setDisplayName("other owner");

        ownerCurator.create(owner);
        ownerCurator.create(otherOwner);

        Consumer c1 = new Consumer()
            .setName("name1")
            .setUsername("uname1")
            .setOwner(owner)
            .setType(type);

        Consumer c2 = new Consumer()
            .setName("name2")
            .setUsername("uname2")
            .setOwner(owner)
            .setType(type);

        Consumer c3 = new Consumer()
            .setName("name3")
            .setUsername("uname3")
            .setOwner(otherOwner)
            .setType(type);

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);

        List<String> result = ownerCurator.getConsumerUuids(owner);
        assertEquals(2, result.size());
        assertTrue(result.contains(c1.getUuid()));
        assertTrue(result.contains(c2.getUuid()));
        assertFalse(result.contains(c3.getUuid()));
    }

    private List<Owner> setupDBForLookupOwnersForProductTests() {
        Owner owner1 = this.createOwner("owner1");
        Owner owner2 = this.createOwner("owner2");
        Owner owner3 = this.createOwner("owner3");

        Product provided1 = this.createProduct("provided1", "eng1");
        Product provided2 = this.createProduct("provided2", "eng2");
        Product provided3 = this.createProduct("provided3", "eng3");

        Product sku1derived = TestUtil.createProduct("sku1d", "sku1_derived")
            .setProvidedProducts(List.of(provided2));
        Product sku1 = TestUtil.createProduct("sku1", "sku1_owner1")
            .setProvidedProducts(List.of(provided1))
            .setDerivedProduct(sku1derived);

        Product sku2derived = TestUtil.createProduct("sku2d", "sku2_derived")
            .setProvidedProducts(List.of(provided3));
        Product sku2 = TestUtil.createProduct("sku2", "sku2_owner2")
            .setProvidedProducts(List.of(provided1, provided2))
            .setDerivedProduct(sku2derived);

        Product sku3derived = TestUtil.createProduct("sku3d", "sku3_derived")
            .setProvidedProducts(List.of(provided3));
        Product sku3 = TestUtil.createProduct("sku3", "sku3_owner3")
            .setProvidedProducts(List.of(provided1))
            .setDerivedProduct(sku3derived);

        this.createProduct(sku1derived);
        this.createProduct(sku1);
        this.createProduct(sku2derived);
        this.createProduct(sku2);
        this.createProduct(sku3derived);
        this.createProduct(sku3);

        Pool pool1 = new Pool()
            .setOwner(owner1)
            .setProduct(sku1)
            .setStartDate(TestUtil.createDate(2000, 1, 1))
            .setEndDate(TestUtil.createDate(3000, 1, 1))
            .setQuantity(5L);

        Pool pool2 = new Pool()
            .setOwner(owner2)
            .setProduct(sku2)
            .setStartDate(TestUtil.createDate(1000, 1, 1))
            .setEndDate(TestUtil.createDate(2000, 1, 1))
            .setQuantity(5L);

        Pool pool3 = new Pool()
            .setOwner(owner3)
            .setProduct(sku3)
            .setStartDate(new Date())
            .setEndDate(new Date())
            .setQuantity(5L);

        this.poolCurator.create(pool1);
        this.poolCurator.create(pool2);
        this.poolCurator.create(pool3);

        return List.of(owner1, owner2, owner3);
    }

    @Test
    public void testGetOwnersWithProducts() {
        List<Owner> owners = this.setupDBForLookupOwnersForProductTests();
        Owner owner1 = owners.get(0);
        Owner owner2 = owners.get(1);
        Owner owner3 = owners.get(2);
        Set<Owner> uniqueOwners = null;

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("sku1"));
        assertTrue(CollectionUtils.isEqualCollection(Set.of(owner1), uniqueOwners));

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("sku2d"));
        assertTrue(CollectionUtils.isEqualCollection(Set.of(owner2), uniqueOwners));

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("provided1"));
        assertTrue(CollectionUtils.isEqualCollection(Set.of(owner1, owner2, owner3), uniqueOwners));

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("provided3"));
        assertTrue(CollectionUtils.isEqualCollection(Set.of(owner2, owner3), uniqueOwners));

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("sku1", "sku3"));
        assertTrue(CollectionUtils.isEqualCollection(Set.of(owner1, owner3), uniqueOwners));

        uniqueOwners = this.ownerCurator.getOwnersWithProducts(List.of("nope"));
        assertEquals(0, uniqueOwners.size());
    }

    @Test
    public void testLockAndLoadByKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(ownerKey);

        assertNotNull(actual);
        assertEquals(ownerKey, actual.getKey());
    }

    @Test
    public void testLockAndLoadByKeyDoesntLoadBadKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(ownerKey + "-bad");

        assertNull(actual);
    }

    @Test
    public void testLockAndLoadByKeyDoesntLoadNullKey() {
        String ownerKey = "test_key";

        Owner owner = this.createOwner(ownerKey);
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        Owner actual = this.ownerCurator.lockAndLoadByKey(null);

        assertNull(actual);
    }

    @Test
    public void fetchesOwnerContentAccess() {
        String expectedContentAccessMode = "org_environment";
        String expectedContentAccessModeList = "org_environment";
        Owner owner = this.createOwner("test_key");
        this.ownerCurator.flush();
        this.ownerCurator.clear();

        OwnerContentAccess actual = this.ownerCurator.getOwnerContentAccess(owner.getKey());

        assertEquals(expectedContentAccessMode, actual.getContentAccessMode());
        assertEquals(expectedContentAccessModeList, actual.getContentAccessModeList());
    }

    @Test
    public void throwsWhenOwnerMissing() {
        String unknownOwnerKey = "test_key";

        assertThrows(OwnerNotFoundException.class,
            () -> this.ownerCurator.getOwnerContentAccess(unknownOwnerKey));
    }

    @Test
    void ownerDoesNotExist() {
        assertFalse(this.ownerCurator.existsByKey("test_key"));
    }

    @Test
    void ownerExists() {
        Owner owner = this.createOwner("test_key");

        assertTrue(this.ownerCurator.existsByKey(owner.getKey()));
    }

    @Test
    public void testSetLastContentUpdateForOwnersWithProducts() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Product prod1 = this.createProduct("test_prod-1");
        Product prod2 = this.createProduct("test_prod-2");
        Product prod3 = this.createProduct("test_prod-3");

        Pool owner1pool1 = this.createPool(owner1, prod1);
        Pool owner2pool1 = this.createPool(owner2, prod2);
        Pool owner3pool1 = this.createPool(owner3, prod3);

        Instant now = Instant.now();

        List<String> input = List.of(prod1.getUuid(), prod2.getUuid());

        int count = this.ownerCurator.setLastContentUpdateForOwnersWithProducts(input);
        assertEquals(2, count);

        this.ownerCurator.refresh(owner1, owner2, owner3);
        assertTrue(now.isBefore(owner1.getLastContentUpdate().toInstant()));
        assertTrue(now.isBefore(owner2.getLastContentUpdate().toInstant()));
        assertFalse(now.isBefore(owner3.getLastContentUpdate().toInstant()));
    }

    @Test
    public void testSetLastContentUpdateForOwnersWithProductsNoMatch() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Product prod1 = this.createProduct("test_prod-1");
        Product prod2 = this.createProduct("test_prod-2");
        Product prod3 = this.createProduct("test_prod-3");
        Product prod4 = this.createProduct("test_prod-4");

        Pool owner1pool1 = this.createPool(owner1, prod1);
        Pool owner2pool1 = this.createPool(owner2, prod2);
        Pool owner3pool1 = this.createPool(owner3, prod3);

        Instant now = Instant.now();

        List<String> input = List.of(prod4.getUuid(), "invalid_uuid");

        int count = this.ownerCurator.setLastContentUpdateForOwnersWithProducts(input);
        assertEquals(0, count);

        this.ownerCurator.refresh(owner1, owner2, owner3);
        assertFalse(now.isBefore(owner1.getLastContentUpdate().toInstant()));
        assertFalse(now.isBefore(owner2.getLastContentUpdate().toInstant()));
        assertFalse(now.isBefore(owner3.getLastContentUpdate().toInstant()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testSetLastContentUpdateForOwnersWithProductsHandlesNullAndEmptyInputs(List<String> input) {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Product prod1 = this.createProduct("test_prod-1");
        Product prod2 = this.createProduct("test_prod-2");
        Product prod3 = this.createProduct("test_prod-3");

        Pool owner1pool1 = this.createPool(owner1, prod1);
        Pool owner2pool1 = this.createPool(owner2, prod2);
        Pool owner3pool1 = this.createPool(owner3, prod3);

        Instant now = Instant.now();

        int count = this.ownerCurator.setLastContentUpdateForOwnersWithProducts(input);
        assertEquals(0, count);

        this.ownerCurator.refresh(owner1, owner2, owner3);
        assertFalse(now.isBefore(owner1.getLastContentUpdate().toInstant()));
        assertFalse(now.isBefore(owner2.getLastContentUpdate().toInstant()));
        assertFalse(now.isBefore(owner3.getLastContentUpdate().toInstant()));
    }

    @Test
    public void testListAll() {
        this.createOwner("test_owner-1");
        this.createOwner("test_owner-2");
        this.createOwner("test_owner-3");

        OwnerQueryArguments args = new OwnerQueryArguments()
            .setOffset(0);
        assertEquals(3, ownerCurator.listAll(args).size());
    }

    @Test
    public void testListAllWithKeys() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        OwnerQueryArguments args = new OwnerQueryArguments()
            .setOffset(0)
            .setKeys(List.of("test_owner-1", "test_owner-3"));
        assertIterableEquals(List.of(owner1, owner3), ownerCurator.listAll(args));
    }

    @Test
    public void testListAllWithKeysOrdered() throws InterruptedException {
        Owner owner1 = this.createOwner("test_owner-1");
        // ensure timestamp uniqueness
        sleep(1000);
        Owner owner2 = this.createOwner("test_owner-2");
        sleep(1000);
        Owner owner3 = this.createOwner("test_owner-3");

        OwnerQueryArguments args = new OwnerQueryArguments()
            .setOffset(0)
            .setKeys(List.of("test_owner-1", "test_owner-3"))
            .addOrder("created", true);
        assertEquals(owner3, ownerCurator.listAll(args).get(0));
    }

    @Test
    public void testListAllPaged() {
        IntStream.range(0, 10).forEach(element -> {
            createOwner("test_owner-" + element);
        });

        OwnerQueryArguments args = new OwnerQueryArguments()
            .setOffset(5)
            .setLimit(5);
        List<Owner> page = ownerCurator.listAll(args);
        assertEquals(5, page.size());
        assertEquals("test_owner-5", page.get(0).getKey());
        assertEquals("test_owner-9", page.get(4).getKey());
    }

    @Test
    public void testGetOwnerCount() {
        IntStream.range(0, 10).forEach(element -> {
            createOwner("test_owner-" + element);
        });

        OwnerQueryArguments args = new OwnerQueryArguments();
        Long result = ownerCurator.getOwnerCount(args);
        assertEquals(10L, result);
    }
}
