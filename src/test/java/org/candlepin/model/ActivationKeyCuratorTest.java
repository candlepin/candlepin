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
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



class ActivationKeyCuratorTest extends DatabaseTestFixture {

    private Owner owner;

    @BeforeEach
    public void setUp() {
        owner = new Owner()
            .setKey("test-owner")
            .setDisplayName("Test Owner");

        owner = ownerCurator.create(owner);
    }

    @Test
    void noActivationKeysFound() {
        List<ActivationKey> foundKeys = activationKeyCurator
            .findByKeyNames(owner.getKey(), Set.of("test-key1"));

        assertTrue(foundKeys.isEmpty());
    }

    @Test
    void keysFound() {
        activationKeyCurator.create(new ActivationKey("test-key1", owner));
        activationKeyCurator.create(new ActivationKey("test-key2", owner));

        List<ActivationKey> foundKeys = activationKeyCurator
            .findByKeyNames(owner.getKey(), Set.of("test-key1"));

        assertEquals(1, foundKeys.size());
        assertTrue(foundKeys.stream().map(ActivationKey::getName).allMatch("test-key1"::equals));
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testRemoveActivationKeyPoolsWithInvalidOwnerKey(String ownerKey) {
        assertThrows(IllegalArgumentException.class, () -> activationKeyCurator
            .removeActivationKeyPools(ownerKey));
    }

    @Test
    public void testRemoveActivationKeyPools() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Pool pool1 = this.createPool(owner1, product1);
        Pool pool2 = this.createPool(owner1, product2);
        Pool pool3 = this.createPool(owner2, product2);

        ActivationKey key1 = activationKeyCurator.create(new ActivationKey(TestUtil.randomString(), owner1)
            .addPool(pool1, 1L));
        ActivationKey key2 = activationKeyCurator.create(new ActivationKey(TestUtil.randomString(), owner1)
            .addPool(pool1, 1L));
        ActivationKey key3 = activationKeyCurator.create(new ActivationKey(TestUtil.randomString(), owner1)
            .addPool(pool2, 1L));
        ActivationKey key4 = activationKeyCurator.create(new ActivationKey(TestUtil.randomString(), owner1)
            .addPool(pool2, 1L));
        ActivationKey owner2Key = activationKeyCurator
            .create(new ActivationKey(TestUtil.randomString(), owner2)
            .addPool(pool3, 1L));
        String expectedOwner2KeyPoolId = owner2Key.getPools().iterator().next().getId();

        int actual = activationKeyCurator.removeActivationKeyPools(owner1.getKey());
        assertEquals(4, actual);

        assertThat(listAllActivationKeyPoolIdsByOwner(owner1.getKey()))
            .isEmpty();

        assertThat(listAllActivationKeyPoolIdsByOwner(owner2.getKey()))
            .containsExactly(expectedOwner2KeyPoolId);

        assertThat(activationKeyCurator.getByKeyName(owner1, key1.getName()))
            .isNotNull()
            .isEqualTo(key1);

        assertThat(activationKeyCurator.getByKeyName(owner1, key2.getName()))
            .isNotNull()
            .isEqualTo(key2);

        assertThat(activationKeyCurator.getByKeyName(owner1, key3.getName()))
            .isNotNull()
            .isEqualTo(key3);

        assertThat(activationKeyCurator.getByKeyName(owner1, key4.getName()))
            .isNotNull()
            .isEqualTo(key4);

        assertThat(activationKeyCurator.getByKeyName(owner2, owner2Key.getName()))
            .isNotNull()
            .isEqualTo(owner2Key);
    }

    @Test
    public void testListByOwnerWithNullOwner() {
        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(null, null);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "   " })
    @NullAndEmptySource
    public void testListByOwnerWithUnspecifiedKeyName(String keyName) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        List<ActivationKey> owner1Keys = new ArrayList<>();
        List<ActivationKey> owner2Keys = new ArrayList<>();

        // Create activation keys for both owner 1 and 2
        for (int i = 0; i < 5; i++) {
            ActivationKey owner1Key = this.createActivationKey(owner1);
            owner1Keys.add(owner1Key);
            ActivationKey owner2Key = this.createActivationKey(owner2);
            owner2Keys.add(owner2Key);
        }

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(owner1, keyName);

        // Impl note: listByOwner returns detached read models, so instances never match the
        // managed entities created above; compare by ID instead.
        assertThat(actual)
            .isNotNull()
            .extracting(ActivationKey::getId)
            .containsExactlyInAnyOrderElementsOf(owner1Keys.stream()
                .map(ActivationKey::getId)
                .toList());
    }

    @Test
    public void testListByOwnerWithActivationKeyName() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        List<ActivationKey> owner1Keys = new ArrayList<>();
        List<ActivationKey> owner2Keys = new ArrayList<>();

        String keyName = TestUtil.randomString("expected-");

        ActivationKey key = new ActivationKey();

        key.setOwner(owner);

        // Create an activation key with our target key name for both owners
        ActivationKey expected = new ActivationKey()
            .setOwner(owner1)
            .setName(keyName);
        expected = this.activationKeyCurator.create(expected);
        ActivationKey owner2Key = new ActivationKey()
            .setOwner(owner2)
            .setName(keyName);
        owner2Key = this.activationKeyCurator.create(owner2Key);

        // Create other activation keys for both owner 1 and 2
        for (int i = 0; i < 5; i++) {
            ActivationKey owner1Key = this.createActivationKey(owner1);
            owner1Keys.add(owner1Key);
            owner2Key = this.createActivationKey(owner2);
            owner2Keys.add(owner2Key);
        }

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(owner1, keyName);

        // Impl note: listByOwner returns detached read models, so instances never match the
        // managed entities created above; compare by ID instead.
        assertThat(actual)
            .isNotNull()
            .singleElement()
            .extracting(ActivationKey::getId)
            .isEqualTo(expected.getId());
    }

    @Test
    public void testListByOwnerPopulatesKeyFields() {
        ActivationKey expected = new ActivationKey(TestUtil.randomString("key-"), this.owner,
            "A test description")
            .setServiceLevel("Premium")
            .setUsage("Production")
            .setRole("Server")
            .setAutoAttach(true)
            .setReleaseVer(new Release("8.10"));

        ActivationKey persisted = this.activationKeyCurator.create(expected);

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(this.owner);

        assertThat(actual)
            .singleElement()
            .returns(persisted.getId(), ActivationKey::getId)
            .returns(persisted.getName(), ActivationKey::getName)
            .returns(persisted.getDescription(), ActivationKey::getDescription)
            .returns(persisted.getServiceLevel(), ActivationKey::getServiceLevel)
            .returns(persisted.getUsage(), ActivationKey::getUsage)
            .returns(persisted.getRole(), ActivationKey::getRole)
            .returns(persisted.isAutoAttach(), ActivationKey::isAutoAttach)
            .returns(new Release("8.10"), ActivationKey::getReleaseVer)
            .doesNotReturn(null, ActivationKey::getCreated)
            .doesNotReturn(null, ActivationKey::getUpdated)
            .doesNotReturn(null, ActivationKey::getOwner)
            .returns(this.owner.getId(), key -> key.getOwner().getId());
    }

    @Test
    public void testListByOwnerPopulatesProductIds() {
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        this.activationKeyCurator.create(new ActivationKey(TestUtil.randomString("key-"), this.owner)
            .setProductIds(Set.of(product1.getId(), product2.getId())));

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(this.owner);

        assertThat(actual)
            .singleElement()
            .satisfies(key -> assertThat(key.getProductIds())
                .containsExactlyInAnyOrder(product1.getId(), product2.getId()));
    }

    @Test
    public void testListByOwnerPopulatesPools() {
        Pool pool = this.createPool(this.owner, this.createProduct());

        this.activationKeyCurator.create(new ActivationKey(TestUtil.randomString("key-"), this.owner)
            .addPool(pool, 5L));

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(this.owner);

        assertThat(actual)
            .singleElement()
            .satisfies(key -> assertThat(key.getPools())
                .singleElement()
                .satisfies(poolEntry -> {
                    assertThat(poolEntry.getPoolId()).isEqualTo(pool.getId());
                    assertThat(poolEntry.getQuantity()).isEqualTo(5L);
                    assertThat(poolEntry.getKey().getId()).isEqualTo(key.getId());
                }));
    }

    @Test
    public void testListByOwnerPopulatesContentOverrides() {
        ActivationKeyContentOverride override1 = new ActivationKeyContentOverride()
            .setContentLabel("content-label-1")
            .setName("override-name-1")
            .setValue("override-value-1");
        ActivationKeyContentOverride override2 = new ActivationKeyContentOverride()
            .setContentLabel("content-label-2")
            .setName("override-name-2")
            .setValue("override-value-2");

        this.activationKeyCurator.create(new ActivationKey(TestUtil.randomString("key-"), this.owner)
            .addContentOverride(override1)
            .addContentOverride(override2));

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(this.owner);

        assertThat(actual)
            .singleElement()
            .satisfies(key -> {
                assertThat(key.getContentOverrides())
                    .allSatisfy(override -> assertThat(override.getKey().getId()).isEqualTo(key.getId()));

                assertThat(key.getContentOverrides())
                    .extracting(ActivationKeyContentOverride::getContentLabel,
                        ActivationKeyContentOverride::getName,
                        ActivationKeyContentOverride::getValue)
                    .containsExactlyInAnyOrder(
                        tuple("content-label-1", "override-name-1", "override-value-1"),
                        tuple("content-label-2", "override-name-2", "override-value-2"));
            });
    }

    @Test
    public void testListByOwnerPopulatesAddOns() {
        Set<String> addOns = Set.of("addon-1", "addon-2");

        this.activationKeyCurator.create(new ActivationKey(TestUtil.randomString("key-"), this.owner)
            .setAddOns(new HashSet<>(addOns)));

        List<ActivationKey> actual = this.activationKeyCurator.listByOwner(this.owner);

        assertThat(actual)
            .singleElement()
            .satisfies(key -> assertThat(key.getAddOns())
                .containsExactlyInAnyOrderElementsOf(addOns));
    }

    @Test
    public void testDeleteByOwnerWithNullOwner() {
        assertEquals(0, this.activationKeyCurator.deleteByOwner(null));
    }

    @Test
    public void testDeleteByOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product = this.createProduct();
        Pool pool1 = this.createPool(owner1, product);
        Pool pool2 = this.createPool(owner2, product);

        ActivationKey key1 = this.activationKeyCurator
            .create(new ActivationKey(TestUtil.randomString("key-"), owner1)
            .addPool(pool1, 1L)
            .addPool(pool2, 2L)
            .setProductIds(Set.of(product.getId()))
            .addContentOverride(new ActivationKeyContentOverride()
                .setContentLabel("content-label-1")
                .setName("override-name-1")
                .setValue("override-value-1")));
        ActivationKey key2 = this.activationKeyCurator
            .create(new ActivationKey(TestUtil.randomString("key-"), owner1)
            .addPool(pool2, 3L));
        ActivationKey owner2Key = this.activationKeyCurator
            .create(new ActivationKey(TestUtil.randomString("key-"), owner2)
            .addPool(pool2, 4L));
        String expectedOwner2KeyPoolId = owner2Key.getPools().iterator().next().getId();

        int deleted = this.activationKeyCurator.deleteByOwner(owner1);

        assertEquals(2, deleted);
        assertThat(this.activationKeyCurator.getByKeyName(owner1, key1.getName())).isNull();
        assertThat(this.activationKeyCurator.getByKeyName(owner1, key2.getName())).isNull();
        assertThat(this.activationKeyCurator.getByKeyName(owner2, owner2Key.getName()))
            .isNotNull()
            .isEqualTo(owner2Key);

        // All pool references belonging to the deleted keys must be gone, while
        // the surviving owner's key keeps its reference.
        assertThat(this.listAllActivationKeyPoolIdsByOwner(owner2.getKey()))
            .containsExactly(expectedOwner2KeyPoolId);

        // Content overrides and product references are removed by DB cascades;
        // verify the second owner's data is untouched and reloading the deleted
        // key's collections yields nothing.
        assertThat(this.activationKeyCurator.listByOwner(owner2))
            .singleElement()
            .satisfies(key -> {
                assertThat(key.getId()).isEqualTo(owner2Key.getId());
                assertThat(key.getPools()).hasSize(1);
                assertThat(key.getContentOverrides()).isEmpty();
            });
        assertThat(this.activationKeyCurator.listByOwner(owner1)).isEmpty();
    }

    private List<String> listAllActivationKeyPoolIdsByOwner(String ownerKey) {
        String jpql = "SELECT akp.id FROM ActivationKeyPool akp " +
            "JOIN Pool p ON p.id = akp.pool.id " +
            "WHERE p.owner.key = :owner_key";

        return this.getEntityManager()
            .createQuery(jpql, String.class)
            .setParameter("owner_key", ownerKey)
            .getResultList();
    }

}
