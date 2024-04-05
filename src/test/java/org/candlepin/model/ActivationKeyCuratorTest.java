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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

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
