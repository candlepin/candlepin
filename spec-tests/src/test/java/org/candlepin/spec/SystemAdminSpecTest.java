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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;


@SpecTest
public class SystemAdminSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
    }

    // user type 1 tests
    @Test
    public void shouldOnlySeeTheirSystems() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        // Registered by our system admin
        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        // Registered by somebody else
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        // Admin should see both systems in the org:
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), Set.of())).hasSize(2);
        // User should just see one:
        assertThat(userClient.owners().listOwnerConsumers(owner.getKey()))
            .singleElement()
            .returns(consumer1.getUuid(), x -> x.getUuid());
        assertNotFound(() -> userClient.consumers().getConsumer(consumer2.getUuid()));
    }

    @Test
    public void shouldListPoolsForOnlyTheirSystems() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        assertThat(userClient.pools().listPoolsByConsumer(consumer1.getUuid())).hasSize(0);
        assertNotFound(() -> userClient.pools().listPoolsByConsumer(consumer2.getUuid()));
    }

    @Test
    public void shouldUnregisterOnlyTheirSystems() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        userClient.consumers().deleteConsumer(consumer1.getUuid());
        assertNotFound(() -> userClient.consumers().deleteConsumer(consumer2.getUuid()));
    }

    @Test
    public void shouldCreateEntitlementsOnlyForTheirSystems() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1);
        assertNotFound(() -> userClient.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1));
    }

    @Test
    public void shouldCreateEntitlementsAsynchronously() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));

        PoolDTO pool = createPool(owner);
        AsyncJobStatusDTO job = AsyncJobStatusDTO.fromJson(userClient.consumers().bind(consumer1.getUuid(),
            pool.getId(), null, 1, null, null, true, null, List.of()));
        client.jobs().waitForJob(job);
    }

    @Test
    public void shouldViewOnlyTheirSystemsEntitlements() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            client.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);

        // These should work:
        assertThat(userClient.entitlements().getEntitlement(ent1.getId()))
            .isNotNull()
            .returns(ent1.getId(), EntitlementDTO::getId);
        assertThat(userClient.consumers().listEntitlements(consumer1.getUuid())).hasSize(1);

        // These should not:
        assertForbidden(() -> userClient.entitlements().getEntitlement(ent2.getId()));
        assertNotFound(() -> userClient.consumers().listEntitlements(consumer2.getUuid()));
    }

    @Test
    public void shouldOnlyUnsubscribeTheirSystemsEntitlements() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            client.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);

        userClient.consumers().unbindByEntitlementId(consumer1.getUuid(), ent1.getId());
        // Technically being unable to view consumer2 causes this before we even
        //  verify the entitlement.
        assertNotFound(() -> userClient.consumers().unbindByEntitlementId(consumer2.getUuid(), ent2.getId()));
    }

    // user type 2 tests
    @Test
    public void shouldListTheirOwners() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);
        assertThat(userClient.users().listUserOwners(user.getUsername())).hasSize(1);
    }

    @Test
    public void shouldSeeAllSystemsInOrg() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));
        // Admin should see both systems in the org:
        assertThat(ownerApi.listOwnerConsumers(owner.getKey())).hasSize(2);
        // User should also see them:
        assertThat(userClient.owners().listOwnerConsumers(owner.getKey())).hasSize(2);
        assertThat(userClient.consumers().getConsumer(consumer2.getUuid()))
            .isNotNull()
            .returns(consumer2.getUuid(), ConsumerDTO::getUuid);
    }

    @Test
    public void shouldListPoolsForAllSystemsInOrg() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        assertThat(userClient.pools().listPoolsByConsumer(consumer1.getUuid())).hasSize(0);
        assertThat(userClient.pools().listPoolsByConsumer(consumer2.getUuid())).hasSize(0);
    }

    @Test
    public void shouldOnlyUnregisterTheirSystems() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        userClient.consumers().deleteConsumer(consumer1.getUuid());
        // We expect forbidden here because this user can actually *see*
        //  the system, it just can't delete it:
        assertForbidden(() -> userClient.consumers().deleteConsumer(consumer2.getUuid()));
    }


    @Test
    public void shouldCreateEntitlementsOnlyForTheirSystemsUser2() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1);
        assertForbidden(() -> userClient.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1));
    }

    @Test
    public void shouldViewEntitlementsForOtherSystemsInTheOrg() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            client.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);

        assertThat(userClient.entitlements().getEntitlement(ent1.getId()))
            .isNotNull()
            .returns(ent1.getId(), EntitlementDTO::getId);
        assertThat(userClient.consumers().listEntitlements(consumer1.getUuid())).hasSize(1);

        assertThat(userClient.entitlements().getEntitlement(ent2.getId()))
            .isNotNull()
            .returns(ent2.getId(), EntitlementDTO::getId);
        assertThat(userClient.consumers().listEntitlements(consumer2.getUuid())).hasSize(1);
    }

    @Test
    public void shouldOnlyUnsubscribeTheirSystemsEntitlementsUser2() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = createUserTypeOwnerReadOnly(owner);
        ApiClient userClient = ApiClients.basic(user);

        ConsumerDTO consumer1 = userClient.consumers().createConsumer(
            Consumers.random(owner).username(user.getUsername()));
        ConsumerDTO consumer2 = client.consumers().createConsumer(
            Consumers.random(owner).username("admin"));

        PoolDTO pool = createPool(owner);
        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            userClient.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            client.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);

        userClient.consumers().unbindByEntitlementId(consumer1.getUuid(), ent1.getId());
        // We can view consumer2 but not remove the entitlement
        assertForbidden(() -> userClient.consumers().unbindByEntitlementId(
            consumer2.getUuid(), ent2.getId()));
    }

    private UserDTO createUserTypeAllAccess(OwnerDTO owner) {
        return UserUtil.createWith(client,
            Permissions.USERNAME_CONSUMERS.all(owner),
            Permissions.OWNER_POOLS.all(owner),
            Permissions.ATTACH.all(owner));
    }

    private UserDTO createUserTypeOwnerReadOnly(OwnerDTO owner) {
        return UserUtil.createWith(client,
            Permissions.USERNAME_CONSUMERS.all(owner),
            Permissions.OWNER.readOnly(owner),
            Permissions.ATTACH.all(owner));
    }

    private PoolDTO createPool(OwnerDTO owner) {
        ProductDTO prod = ownerProductApi.createProductByOwner(owner.getKey(), Products.random());
        return ownerApi.createPool(owner.getKey(), Pools.random(prod));
    }
}
