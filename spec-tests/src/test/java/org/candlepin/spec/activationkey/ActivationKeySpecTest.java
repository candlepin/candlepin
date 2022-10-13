/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.activationkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.client.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.ContentOverrides;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpecTest
public class ActivationKeySpecTest {

    @Test
    public void shouldAllowActivationKeyFieldFiltering() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        ActivationKeyDTO keyFull = adminClient.activationKeys().getActivationKey(activationKey.getId());
        ActivationKeyDTO keyFiltered = Request.from(adminClient)
            .setPath("/activation_keys/{activation_key_id}")
            .setPathParam("activation_key_id", activationKey.getId())
            .addQueryParam("exclude", "name")
            .execute()
            .deserialize(ActivationKeyDTO.class);

        assertThat(keyFull)
            .usingRecursiveComparison()
            .ignoringFields("created")
            .ignoringFields("updated")
            .ignoringFields("name")
            .isEqualTo(keyFiltered);
        assertThat(keyFiltered)
            .hasFieldOrPropertyWithValue("name", null);
    }

    @Test
    public void shouldSetAutoAttachOnActivationKey() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);

        ActivationKeyDTO activationKey = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).autoAttach(null));
        assertThat(activationKey)
            .hasFieldOrPropertyWithValue("autoAttach", null);

        ActivationKeyDTO keyWithAutoAttach = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).autoAttach(true));
        assertThat(keyWithAutoAttach)
            .hasFieldOrPropertyWithValue("autoAttach", true);

        ActivationKeyDTO keyWithoutAutoAttach = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).autoAttach(false));
        assertThat(keyWithoutAutoAttach)
            .hasFieldOrPropertyWithValue("autoAttach", false);
    }

    @Test
    public void shouldAllowOwnersToListExistingActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);

        List<ActivationKeyDTO> activationKeys = List.of(
            createActivationKey(adminClient, owner),
            createActivationKey(adminClient, owner),
            createActivationKey(adminClient, owner));

        List<ActivationKeyDTO> listOfActivationKeys =
            adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(3)
            .usingRecursiveComparison()
            .ignoringFields("created", "updated")
            .isEqualTo(activationKeys);
    }

    @Test
    public void shouldAllowUpdatingOfNames() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomName1 = StringUtil.random("name1");
        activationKey.name(randomName1);
        ActivationKeyDTO activationKeyUpdated = updateActivationKey(adminClient, activationKey);
        assertThat(activationKeyUpdated)
            .hasFieldOrPropertyWithValue("name", randomName1);

        String randomName2 = StringUtil.random("name_2");
        activationKey.name(randomName2);
        activationKeyUpdated = updateActivationKey(userClient, activationKey);
        assertThat(activationKeyUpdated)
            .hasFieldOrPropertyWithValue("name", randomName2);
    }

    @Test
    public void shouldNotAllowUpdatingOfNamesFromAnotherOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        OwnerDTO anotherOwner = createOwner(adminClient);
        ApiClient anotherUserClient = createUserClient(adminClient, anotherOwner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomName3 = StringUtil.random("not-gonna-happen");
        activationKey.name(randomName3);
        assertNotFound(() -> updateActivationKey(anotherUserClient, activationKey));
    }

    @Test
    public void shouldNotAllowUpdatingSpecialCharacterAsName() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomName3 = StringUtil.random("foo%%Bar$$");
        activationKey.name(randomName3);
        assertBadRequest(() -> updateActivationKey(userClient, activationKey));
    }

    @Test
    public void shouldNotAllowUpdatingEmptyStringAsName() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        activationKey.name("");
        assertBadRequest(() -> updateActivationKey(userClient, activationKey));
    }

    @Test
    public void shouldAllowUpdatingHyphenAndUnderscoreStringAsName() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomName = StringUtil.random("test_activation-1");
        activationKey.name(randomName);
        ActivationKeyDTO activationKeyUpdated = updateActivationKey(userClient, activationKey);
        assertThat(activationKeyUpdated)
            .hasFieldOrPropertyWithValue("name", randomName);
    }

    @Test
    public void shouldAllowUpdatingOfDescriptions() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomDescription1 = StringUtil.random("very descriptive text");
        activationKey.description(randomDescription1);
        ActivationKeyDTO activationKeyUpdated = updateActivationKey(adminClient, activationKey);
        assertThat(activationKeyUpdated)
            .hasFieldOrPropertyWithValue("description", randomDescription1);

        String randomDescription2 = StringUtil.random("more descriptive text");
        activationKey.description(randomDescription2);
        activationKeyUpdated = updateActivationKey(userClient, activationKey);
        assertThat(activationKeyUpdated)
            .hasFieldOrPropertyWithValue("description", randomDescription2);
    }

    @Test
    public void shouldNotAllowUpdatingOfDescriptionsFromAnotherOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        OwnerDTO anotherOwner = createOwner(adminClient);
        ApiClient anotherUserClient = createUserClient(adminClient, anotherOwner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        String randomDescription3 = StringUtil.random("nope");
        activationKey.description(randomDescription3);
        assertNotFound(() -> updateActivationKey(anotherUserClient, activationKey));
    }

    @Test
    public void shouldAllowSuperadminToDeleteTheirActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        UserDTO superadminUser = UserUtil.createAdminUser(adminClient, owner);
        ApiClient superadminClient = ApiClients.basic(superadminUser.getUsername(),
            superadminUser.getPassword());

        ActivationKeyDTO activationKey = createActivationKey(superadminClient, owner);
        List<ActivationKeyDTO> listOfActivationKeys =
            adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(1);
        superadminClient.activationKeys().deleteActivationKey(activationKey.getId());
        listOfActivationKeys = adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(0);
    }

    @Test
    public void shouldAllowOwnerToDeleteTheirActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);

        ActivationKeyDTO activationKey = createActivationKey(userClient, owner);
        List<ActivationKeyDTO> listOfActivationKeys =
            adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(1);
        userClient.activationKeys().deleteActivationKey(activationKey.getId());
        listOfActivationKeys = adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(0);
    }

    @Test
    public void shouldNotAllowWrongOwnerToDeleteActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        OwnerDTO anotherOwner = createOwner(adminClient);
        ApiClient anotherUserClient = createUserClient(adminClient, anotherOwner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        assertNotFound(() -> anotherUserClient.activationKeys().deleteActivationKey(activationKey.getId()));
    }

    @Test
    public void shouldAllowPoolsToBeAddedToAndRemovedFromActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(adminClient, owner);
        ProductDTO product = createProduct(adminClient, owner);
        PoolDTO pool = createPool(adminClient, owner, product);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        adminClient.activationKeys().addPoolToKey(activationKey.getId(), pool.getId(), null);
        ActivationKeyDTO updatedActivationKey = adminClient.activationKeys()
            .getActivationKey(activationKey.getId());
        assertEquals(1, updatedActivationKey.getPools().size());
        adminClient.activationKeys().removePoolFromKey(activationKey.getId(), pool.getId());
        updatedActivationKey = adminClient.activationKeys().getActivationKey(activationKey.getId());
        assertEquals(0, updatedActivationKey.getPools().size());

        userClient.activationKeys().addPoolToKey(activationKey.getId(), pool.getId(), null);
        updatedActivationKey = userClient.activationKeys().getActivationKey(activationKey.getId());
        assertEquals(1, updatedActivationKey.getPools().size());
        userClient.activationKeys().removePoolFromKey(activationKey.getId(), pool.getId());
        updatedActivationKey = userClient.activationKeys().getActivationKey(activationKey.getId());
        assertEquals(0, updatedActivationKey.getPools().size());
    }

    @Test
    public void shouldNotAllowPoolsToBeAddedToAndRemovedFromActivationKeysFromAnotherOwner() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        OwnerDTO anotherOwner = createOwner(adminClient);
        ApiClient anotherUserClient = createUserClient(adminClient, anotherOwner);
        ProductDTO product = createProduct(adminClient, owner);
        PoolDTO pool = createPool(adminClient, owner, product);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        assertNotFound(() -> {
            anotherUserClient.activationKeys().addPoolToKey(activationKey.getId(), pool.getId(), null);
        });
        assertNotFound(() -> {
            anotherUserClient.activationKeys().removePoolFromKey(activationKey.getId(), pool.getId());
        });
    }

    @Test
    public void shouldAllowProductIdsToBeAddedToAndRemovedFromActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ProductDTO product = createProduct(adminClient, owner);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        adminClient.activationKeys().addProductIdToKey(activationKey.getId(), product.getId());
        ActivationKeyDTO updatedActivationKey = adminClient.activationKeys()
            .getActivationKey(activationKey.getId());
        assertThat(updatedActivationKey.getProducts())
            .hasSize(1)
            .first()
            .hasFieldOrPropertyWithValue("productId", product.getId());
        adminClient.activationKeys().removeProductIdFromKey(activationKey.getId(), product.getId());
        updatedActivationKey = adminClient.activationKeys()
            .getActivationKey(activationKey.getId());
        assertThat(updatedActivationKey.getProducts())
            .hasSize(0);
    }

    @Test
    public void shouldAllowAutoAttachFlagToBeSetOnActivationKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        ActivationKeyDTO updatedActivationKey =
            updateActivationKey(adminClient, activationKey.autoAttach(true));
        assertThat(updatedActivationKey)
            .hasFieldOrPropertyWithValue("autoAttach", true);
        updatedActivationKey = updateActivationKey(adminClient, activationKey.autoAttach(false));
        assertThat(updatedActivationKey)
            .hasFieldOrPropertyWithValue("autoAttach", false);
    }

    @Test
    public void shouldAllowOverridesToBeAddedToKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);
        ContentOverrideDTO contentOverride = ContentOverrides.random();

        adminClient.activationKeys().addActivationKeyContentOverrides(
            activationKey.getId(), List.of(contentOverride));
        List<ContentOverrideDTO> listOfContentOverride = adminClient.activationKeys()
            .listActivationKeyContentOverrides(activationKey.getId());
        assertThat(listOfContentOverride)
            .hasSize(1)
            .first()
            .usingRecursiveComparison()
            .ignoringFields("created")
            .ignoringFields("updated")
            .isEqualTo(contentOverride);
    }

    @Test
    public void shouldAllowOverridesToBeRemovedFromKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);
        ContentOverrideDTO contentOverride = ContentOverrides.random();

        adminClient.activationKeys().addActivationKeyContentOverrides(
            activationKey.getId(), List.of(contentOverride));
        List<ContentOverrideDTO> listOfContentOverride = adminClient.activationKeys()
            .listActivationKeyContentOverrides(activationKey.getId());
        assertThat(listOfContentOverride)
            .hasSize(1);
        adminClient.activationKeys().deleteActivationKeyContentOverrides(
            activationKey.getId(), List.of(contentOverride));
        listOfContentOverride = adminClient.activationKeys()
            .listActivationKeyContentOverrides(activationKey.getId());
        assertThat(listOfContentOverride)
            .hasSize(0);
    }

    @Test
    public void shouldAllowNullContentOverrides() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        String name = StringUtil.random("name");

        ActivationKeyDTO activationKey = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).name(name).contentOverrides(null));
        assertThat(activationKey)
            .hasFieldOrPropertyWithValue("name", name);
    }

    @Test
    public void shouldVerifyOverrideNameIsValid() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        assertBadRequest(() -> {
            adminClient.activationKeys().addActivationKeyContentOverrides(
                activationKey.getId(), List.of(ContentOverrides.random().name("label")));
        });
    }

    @Test
    public void shouldVerifyOverrideNameIsBelow256Length() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);

        assertBadRequest(() -> {
            adminClient.activationKeys().addActivationKeyContentOverrides(
                activationKey.getId(), List.of(ContentOverrides.random().name(StringUtil.random(256, "a"))));
        });
    }

    @Test
    public void shouldAllowReleaseToBeSetOnKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKey = createActivationKey(adminClient, owner);
        ReleaseVerDTO randomVer = new ReleaseVerDTO().releaseVer(StringUtil.random("version-"));

        activationKey.releaseVer(randomVer);
        ActivationKeyDTO updatedActivationKey = updateActivationKey(adminClient, activationKey);
        assertThat(updatedActivationKey)
            .extracting("releaseVer")
            .isEqualTo(randomVer);
    }

    @Test
    public void shouldAllowServiceLevelToBeSetOnKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ProductDTO product = createProduct(
            adminClient, owner, ProductAttributes.SupportLevel.withValue("VIP"));
        createPool(adminClient, owner, product);

        ActivationKeyDTO activationKey = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).serviceLevel("VIP"));
        assertThat(activationKey)
            .hasFieldOrPropertyWithValue("serviceLevel", "VIP");
    }

    @Test
    public void shouldAllowServiceLevelToBeUpdatedOnKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ProductDTO product = createProduct(
            adminClient, owner, ProductAttributes.SupportLevel.withValue("VIP"));
        ProductDTO product1 = createProduct(
            adminClient, owner, ProductAttributes.SupportLevel.withValue("Ultra-VIP"));
        createPool(adminClient, owner, product);
        createPool(adminClient, owner, product1);

        ActivationKeyDTO activationKey = createActivationKey(
            adminClient, owner, ActivationKeys.random(owner).serviceLevel("VIP"));
        assertThat(activationKey)
            .hasFieldOrPropertyWithValue("serviceLevel", "VIP");

        activationKey
            .serviceLevel("Ultra-VIP");
        ActivationKeyDTO updatedActivationKey = updateActivationKey(adminClient, activationKey);
        assertThat(updatedActivationKey)
            .hasFieldOrPropertyWithValue("serviceLevel", "Ultra-VIP");
    }

    @Test
    public void shouldAllowSyspurposeAttributesToBeSetOnKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);

        ActivationKeyDTO activationKey = ActivationKeys.random(owner)
            .usage(StringUtil.random("test-usage"))
            .role(StringUtil.random("test-role"))
            .addOns(Set.of(StringUtil.random("test-addon1"), StringUtil.random("test-addon2")));

        ActivationKeyDTO registeredActivationKey = createActivationKey(adminClient, owner, activationKey);

        assertThat(registeredActivationKey)
            .hasFieldOrPropertyWithValue("usage", activationKey.getUsage())
            .hasFieldOrPropertyWithValue("role", activationKey.getRole())
            .hasFieldOrPropertyWithValue("addOns", activationKey.getAddOns());
    }

    @Test
    public void shouldAllowSyspurposeAttributesToBeUpdatedOnKeys() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);

        ActivationKeyDTO activationKey = ActivationKeys.random(owner)
            .usage(StringUtil.random("test-usage"))
            .role(StringUtil.random("test-role"))
            .addOns(Set.of(StringUtil.random("test-addon1"), StringUtil.random("test-addon2")));

        ActivationKeyDTO registeredActivationKey = createActivationKey(adminClient, owner, activationKey);

        registeredActivationKey
            .usage(StringUtil.random("updated-usage"))
            .role(StringUtil.random("updated-role"))
            .addOns(Set.of(StringUtil.random("updated-addon1"), StringUtil.random("updated-addon2")));

        ActivationKeyDTO updateActivationKey = updateActivationKey(adminClient, registeredActivationKey);

        assertThat(updateActivationKey)
            .hasFieldOrPropertyWithValue("usage", registeredActivationKey.getUsage())
            .hasFieldOrPropertyWithValue("role", registeredActivationKey.getRole())
            .hasFieldOrPropertyWithValue("addOns", registeredActivationKey.getAddOns());
    }

    @Test
    public void shouldReturnCorrectExceptionForConstraintViolations() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO activationKeyWithNullName = ActivationKeys.random(owner)
            .name(null);
        ActivationKeyDTO activationKeyWithLongName = ActivationKeys.random(owner)
            .name(StringUtil.random(256));
        ActivationKeyDTO activationKeyWithLongAttribute = ActivationKeys.random(owner)
            .serviceLevel(StringUtil.random(256));

        assertBadRequest(() -> {
            createActivationKey(adminClient, owner, activationKeyWithNullName);
        });
        assertBadRequest(() -> {
            createActivationKey(adminClient, owner, activationKeyWithLongName);
        });
        assertBadRequest(() -> {
            createActivationKey(adminClient, owner, activationKeyWithLongAttribute);
        });
    }

    @Test
    public void shouldAllowSetProductAndPoolId() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ProductDTO product = createProduct(adminClient, owner);
        PoolDTO pool1 = createPool(adminClient, owner, product);
        PoolDTO pool2 = createPool(adminClient, owner, product);
        ActivationKeyProductDTO activationKeyProduct = new ActivationKeyProductDTO()
            .productId(product.getId());
        ActivationKeyPoolDTO activationKeyPool1 = new ActivationKeyPoolDTO()
            .poolId(pool1.getId())
            .quantity(5L);
        ActivationKeyPoolDTO activationKeyPool2 = new ActivationKeyPoolDTO()
            .poolId(pool2.getId());
        ActivationKeyDTO activationKey = ActivationKeys.random(owner)
            .products(Set.of(activationKeyProduct))
            .pools(Set.of(activationKeyPool1, activationKeyPool2))
            .contentOverrides(Set.of(ContentOverrides.random()));

        ActivationKeyDTO registeredActivationKey = createActivationKey(adminClient, owner, activationKey);
        assertThat(registeredActivationKey)
            .hasFieldOrPropertyWithValue("products", Set.of(activationKeyProduct))
            .hasFieldOrPropertyWithValue("pools", Set.of(activationKeyPool1, activationKeyPool2));
        assertThat(registeredActivationKey.getContentOverrides())
            .usingRecursiveComparison()
            .ignoringFields("updated", "created")
            .isEqualTo(activationKey.getContentOverrides());
    }

    @Test
    public void shouldThrowAnExceptionWithMalformedData() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ActivationKeyDTO withBadPoolData = ActivationKeys.random(owner)
            .pools(Set.of(new ActivationKeyPoolDTO().poolId(null).quantity(null)));
        ActivationKeyDTO productAndPoolBadData = ActivationKeys.random(owner)
            .pools(Set.of())
            .products(Set.of(new ActivationKeyProductDTO().productId(null)));
        Set<ContentOverrideDTO> setWithNullElement = new HashSet<>();
        setWithNullElement.add(null);
        ActivationKeyDTO nullContent = ActivationKeys.random(owner)
            .contentOverrides(setWithNullElement);

        assertBadRequest(() -> createActivationKey(adminClient, owner, withBadPoolData));
        assertBadRequest(() -> createActivationKey(adminClient, owner, productAndPoolBadData));
        assertBadRequest(() -> createActivationKey(adminClient, owner, nullContent));
    }

    @Test
    public void shouldListActivationKeysWithPopulatedEntityCollections() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ProductDTO product1 = createProduct(adminClient, owner);
        ProductDTO product2 = createProduct(adminClient, owner);
        PoolDTO pool1 = createPool(adminClient, owner, product1);
        PoolDTO pool2 = createPool(adminClient, owner, product2);
        ActivationKeyDTO activationKey1 = createActivationKey(adminClient, owner);

        adminClient.activationKeys().addProductIdToKey(activationKey1.getId(), product1.getId());
        adminClient.activationKeys().addProductIdToKey(activationKey1.getId(), product2.getId());
        adminClient.activationKeys().addPoolToKey(activationKey1.getId(), pool1.getId(), 1L);
        adminClient.activationKeys().addPoolToKey(activationKey1.getId(), pool2.getId(), 1L);

        // Generate some more activation keys so we have multiple keys to list
        ActivationKeyDTO activationKey2 = createActivationKey(adminClient, owner);
        adminClient.activationKeys().addProductIdToKey(activationKey2.getId(), product1.getId());
        adminClient.activationKeys().addProductIdToKey(activationKey2.getId(), product2.getId());

        ActivationKeyDTO activationKey3 = createActivationKey(adminClient, owner);
        adminClient.activationKeys().addPoolToKey(activationKey3.getId(), pool1.getId(), 1L);
        adminClient.activationKeys().addPoolToKey(activationKey3.getId(), pool2.getId(), 1L);

        ActivationKeyDTO activationKey4 = createActivationKey(adminClient, owner);

        // List keys. We should get all four keys back without error
        List<ActivationKeyDTO> listOfActivationKeys =
            adminClient.owners().ownerActivationKeys(owner.getKey(), null);
        assertThat(listOfActivationKeys)
            .hasSize(4);

        List<String> processed = new ArrayList<>();
        for (ActivationKeyDTO activationKey : listOfActivationKeys) {
            if (processed.contains(activationKey.getId())) {
                Assertions.fail("Duplicate activation keys received.");
            }
            if (activationKey.getId().equals(activationKey1.getId())) {
                assertProductPoolLength(activationKey, 2, 2);
            }
            else if (activationKey.getId().equals(activationKey2.getId())) {
                assertProductPoolLength(activationKey, 2, 0);
            }
            else if (activationKey.getId().equals(activationKey3.getId())) {
                assertProductPoolLength(activationKey, 0, 2);
            }
            else if (activationKey.getId().equals(activationKey4.getId())) {
                assertProductPoolLength(activationKey, 0, 0);
            }
            processed.add(activationKey.getId());
        }
    }

    private static void assertProductPoolLength(
        ActivationKeyDTO activationKey, int productLength, int poolLength) {
        assertThat(activationKey.getProducts())
            .hasSize(productLength);
        assertThat(activationKey.getPools())
            .hasSize(poolLength);
    }

    private static ActivationKeyDTO createActivationKey(
        ApiClient client, OwnerDTO owner, ActivationKeyDTO activationKey) {
        return client.owners().createActivationKey(owner.getKey(), activationKey);
    }

    private static PoolDTO createPool(ApiClient client, OwnerDTO owner, ProductDTO product) {
        return client.owners().createPool(owner.getKey(), Pools.random(product));
    }

    private static ProductDTO createProduct(ApiClient client, OwnerDTO owner) {
        return client.ownerProducts().createProductByOwner(owner.getKey(), Products.random());
    }

    private static ActivationKeyDTO updateActivationKey(ApiClient client, ActivationKeyDTO activationKey) {
        return client.activationKeys().updateActivationKey(activationKey.getId(), activationKey);
    }

    private static ActivationKeyDTO createActivationKey(ApiClient client, OwnerDTO owner) {
        return createActivationKey(client, owner, ActivationKeys.random(owner));
    }

    private static OwnerDTO createOwner(ApiClient client) {
        return client.owners().createOwner(Owners.random());
    }

    private ApiClient createUserClient(ApiClient client, OwnerDTO owner) {
        UserDTO user = UserUtil.createUser(client, owner);
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private ProductDTO createProduct(ApiClient client, OwnerDTO owner, AttributeDTO... attributes) {
        return client.ownerProducts().createProductByOwner(
            owner.getKey(), Products.withAttributes(attributes));
    }
}
