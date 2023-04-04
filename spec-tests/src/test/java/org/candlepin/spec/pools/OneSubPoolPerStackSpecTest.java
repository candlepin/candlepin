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
package org.candlepin.spec.pools;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
@OnlyInStandalone
public class OneSubPoolPerStackSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;
    private static final String STACKED_VIRT_POOL_1 = "123";
    private static final String STACKED_VIRT_POOL_2 = "456";
    private static final String STACKED_VIRT_POOL_3 = "444";
    private static final String STACKED_NON_VIRT_POOL = "789";
    private static final String NON_STACKED_POOL = "234";
    private static final String DATACENTER_POOL = "222";
    private static final String REGULAR_STACKED_WITH_DIFF_STACK_ID = "888";

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    @Test
    public void shouldCreateTheProperPools() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));

        String stackId1 = StringUtil.random("stack");
        String stackId2 = StringUtil.random("stack");

        createVirtLimitProductPools(owner, stackId1);
        createVirtLimitProduct2Pool(owner, stackId1);
        createRegularStackedProductPool(owner, stackId1);
        createNonStackedProductPool(owner);
        createStackedDatacenterProductPool(owner, stackId1);
        createStackedProductDiffIdPool(owner, stackId2);

        List<PoolDTO> createdPools = userClient.owners().listOwnerPools(
            owner.getKey()).stream()
            .filter(x -> !x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList());
        assertThat(createdPools).hasSize(7);

        assertThat(createdPools.stream().filter(x -> STACKED_VIRT_POOL_1.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream().filter(x -> STACKED_VIRT_POOL_2.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream().filter(x -> STACKED_VIRT_POOL_3.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream().filter(x -> STACKED_NON_VIRT_POOL.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream().filter(x -> NON_STACKED_POOL.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream().filter(x -> DATACENTER_POOL.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
        assertThat(createdPools.stream()
            .filter(x -> REGULAR_STACKED_WITH_DIFF_STACK_ID.equals(x.getContractNumber()))
            .collect(Collectors.toList())).hasSize(1);
    }

    @Test
    public void shouldCreateOneSubPoolWhenHostBindsToStackableVirtLimit() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1));
        List<PoolDTO> originalPools = ownerApi.listOwnerPools(data.owner.getKey()).stream()
            .filter(x -> !x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList());

        assertNotNull(bindHostPool(data, pools, STACKED_VIRT_POOL_1));
        assertThat(data.hostClient.pools().listPoolsByConsumer(data.host.getUuid()))
            .hasSize(originalPools.size());
        // sub pool should have been created
        List<PoolDTO> guestPools = data.guestClient.pools()
            .listPoolsByConsumer(data.guest.getUuid());
        assertThat(guestPools).hasSize(originalPools.size() + 1);
        assertThat(guestPools).filteredOn(x -> data.stackId1.equals(x.getSourceStackId()))
            .singleElement()
            .returns(data.host.getUuid(), x -> getAttributeValue("requires_host", x.getAttributes()));
    }

    @Test
    public void shouldNotIncludeHostEntitlementsFromAnotherStack() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createStackedProductDiffIdPool(data.owner, data.stackId2);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, REGULAR_STACKED_WITH_DIFF_STACK_ID));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, REGULAR_STACKED_WITH_DIFF_STACK_ID);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(ent1.getStartDate(), PoolDTO::getStartDate)
            .returns(ent1.getEndDate(), PoolDTO::getEndDate)
            .returns(null, x -> getAttributeValue("sockets", x.getProductAttributes()));
    }

    @Test
    public void shouldDeleteSubPoolWhenAllHostEntitlementsAreRemovedFromTheStack() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, STACKED_VIRT_POOL_2, STACKED_NON_VIRT_POOL));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_VIRT_POOL_2);
        EntitlementDTO ent3 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);

        assertThat(data.hostClient.pools().listPoolsByConsumer(data.host.getUuid())).hasSize(3);
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement();

        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent1.getId());
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent2.getId());
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent3.getId());
        assertThat(data.hostClient.consumers().listEntitlements(data.host.getUuid())).hasSize(0);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .isEmpty();
    }

    @Test
    public void shouldUpdateSubPoolDateRangeWhenAnotherStackedEntitlementIsAdded() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1, STACKED_VIRT_POOL_2));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_VIRT_POOL_2);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(ent1.getStartDate(), PoolDTO::getStartDate)
            .returns(ent2.getEndDate(), PoolDTO::getEndDate);
    }

    @Test
    public void shouldUpdateProductDataOnAddingEntitlementOfSameStack() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1, STACKED_NON_VIRT_POOL));
        EntitlementDTO ent = bindHostPool(data, pools, STACKED_VIRT_POOL_1);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(pools.get(0).getProductId(), PoolDTO::getProductId);

        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);
        // verify product has not changed
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(pools.get(0).getProductId(), PoolDTO::getProductId)
            .returns("3", x -> getAttributeValue("virt_limit", x.getProductAttributes()))
            .returns("yes", x -> getAttributeValue("multi-entitlement", x.getProductAttributes()))
            .returns(data.stackId1, x -> getAttributeValue("stacking_id", x.getProductAttributes()));

        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent.getId());
        // verify product has changed
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(pools.get(1).getProductId(), PoolDTO::getProductId)
            .returns(null, x -> getAttributeValue("virt_limit", x.getProductAttributes()))
            .returns("yes", x -> getAttributeValue("multi-entitlement", x.getProductAttributes()))
            .returns(data.stackId1, x -> getAttributeValue("stacking_id", x.getProductAttributes()));
    }

    @Test
    public void shouldUpdateProductDataOnRemovingEntitlementOfSameStack() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1, STACKED_NON_VIRT_POOL));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent1.getId());

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(null, x -> getAttributeValue("virt_limit", x.getProductAttributes()))
            .returns("yes", x -> getAttributeValue("multi-entitlement", x.getProductAttributes()))
            .returns("6", x -> getAttributeValue("sockets", x.getProductAttributes()))
            .returns(data.stackId1, x -> getAttributeValue("stacking_id", x.getProductAttributes()));
    }

    @Test
    public void shouldNotUpdateProductDataFromProductsNotInTheStack() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createNonStackedProductPool(data.owner);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1, NON_STACKED_POOL));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, NON_STACKED_POOL);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(null, x -> getAttributeValue("cores", x.getProductAttributes()));
    }


    @Test
    public void shouldRevokeGuestEntitlementFromSubPoolWhenLastHostEntInStackIsRemoved() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, STACKED_VIRT_POOL_2, STACKED_NON_VIRT_POOL));
        assertThat(pools).hasSize(3);
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_VIRT_POOL_2);
        EntitlementDTO ent3 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);

        PoolDTO subPool = getSubPool(data);
        data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1);
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(1);

        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent1.getId());
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent2.getId());
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent3.getId());
        assertThat(data.hostClient.consumers().listEntitlements(data.host.getUuid()))
            .hasSize(0);
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(0);
    }

    @Test
    public void shouldRemoveGuestEntitlementWhenHostUnregisters() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, STACKED_VIRT_POOL_2, STACKED_NON_VIRT_POOL));
        assertThat(pools).hasSize(3);
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_VIRT_POOL_2);
        EntitlementDTO ent3 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);

        PoolDTO subPool = getSubPool(data);
        data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1);
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(1);

        data.hostClient.consumers().deleteConsumer(data.host.getUuid());
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(0);
    }

    @Test
    public void shouldEnsureHostUnregistrationDoesNotAffectOtherStackedPools() {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        String sharedStackingId = StringUtil.random("stack");
        // Create target product/pool for owner1
        createTargetPoolAndProducts(owner1, sharedStackingId);
        // Create target product/pool for owner2
        createTargetPoolAndProducts(owner2, sharedStackingId);
        // Get initial pool count
        List<PoolDTO> owner1PoolsStage1 = ownerApi.listOwnerPools(owner1.getKey());
        List<PoolDTO> owner2PoolsStage1 = ownerApi.listOwnerPools(owner2.getKey());

        // Create some consumers, and have them consume the pool
        String owner2ConsumerUuid = "";
        for (int i = 0; i < 3; i++) {
            ConsumerDTO owner1Consumer = client.consumers().createConsumer(Consumers.random(owner1));
            ConsumerDTO owner2Consumer = client.consumers().createConsumer(Consumers.random(owner2));
            client.consumers().bindPool(owner1Consumer.getUuid(), owner1PoolsStage1.get(0).getId(), 1);
            client.consumers().bindPool(owner2Consumer.getUuid(), owner2PoolsStage1.get(0).getId(), 1);
            owner2ConsumerUuid = owner2Consumer.getUuid();
        }

        // Verify our updated pool count (attaching a VDC pool should trigger the creation of a new sub pool
        // for each consumer)
        assertThat(ownerApi.listOwnerPools(owner1.getKey())).hasSize(owner1PoolsStage1.size() + 3);
        assertThat(ownerApi.listOwnerPools(owner2.getKey())).hasSize(owner2PoolsStage1.size() + 3);

        // Unregister one of the hosts and verify that (a) only their pool was removed and (b) the other org's
        // pools were not at all affected
        client.consumers().deleteConsumer(owner2ConsumerUuid);
        assertThat(ownerApi.listOwnerPools(owner1.getKey())).hasSize(owner1PoolsStage1.size() + 3);
        assertThat(ownerApi.listOwnerPools(owner2.getKey())).hasSize(owner2PoolsStage1.size() + 2);
    }

    @Test
    public void shouldRemoveGuestEntitlementWhenGuestIsMigrated() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);

        PoolDTO subPool = getSubPool(data);
        data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1);
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(1);

        // Simulate migration
        ConsumerDTO host2 = client.consumers()
            .createConsumer(Consumers.random(data.owner)
            .guestIds(List.of(new GuestIdDTO().guestId(data.guestVirtUuid))));
        ApiClient host2Client = ApiClients.ssl(host2);
        host2Client.consumers().updateConsumer(host2.getUuid(),
            new ConsumerDTO().guestIds(List.of(new GuestIdDTO().guestId(data.guestVirtUuid))));
        // Guest entitlement should now be revoked.
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .hasSize(1);
    }

    @Test
    public void shouldUseDerivedProductInSubPoolWhenPrimaryPoolHasDerivedProduct() {
        TestData data = new TestData();
        createStackedDatacenterProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(DATACENTER_POOL));
        EntitlementDTO ent1 = bindHostPool(data, pools, DATACENTER_POOL);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())))
            .singleElement()
            .returns(pools.get(0).getDerivedProductId(), PoolDTO::getProductId)
            .returns(1, x -> x.getProvidedProducts().size())
            .returns(pools.get(0).getDerivedProvidedProducts().iterator().next()
            .getProductId(), x -> x.getProvidedProducts().iterator().next().getProductId());

        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent1.getId());
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid()).stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId())).collect(Collectors.toList()))
            .isEmpty();
    }

    @Test
    public void shouldUpdateGuestSubPoolEntAsHostStackIsUpdated() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, STACKED_NON_VIRT_POOL));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);

        PoolDTO subPool = getSubPool(data);
        data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1);

        //  Remove an ent from the host so that the guest ent will be updated.
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent2.getId());

        // Check that the product data was copied.
        assertThat(data.guestClient.consumers().listEntitlements(data.guest.getUuid()))
            .singleElement()
            .returns("3", x -> getAttributeValue(
            "virt_limit", x.getPool().getProductAttributes()))
            .returns("yes", x -> getAttributeValue(
            "multi-entitlement", x.getPool().getProductAttributes()))
            .returns(data.stackId1, x -> getAttributeValue(
            "stacking_id", x.getPool().getProductAttributes()))
            .returns(null, x -> getAttributeValue(
            "sockets", x.getPool().getProductAttributes()));
    }

    @Test
    public void shouldUpdateQuantityOfSubPoolWhenStackChanges() throws InterruptedException {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        createVirtLimitProduct2Pool(data.owner, data.stackId1);
        createRegularStackedProductPool(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data,
            List.of(STACKED_VIRT_POOL_1, STACKED_NON_VIRT_POOL, STACKED_VIRT_POOL_3));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);

        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(3L, PoolDTO::getQuantity)
            .returns(pools.get(0).getProductId(), PoolDTO::getProductId);

        // sleep to ensure ent2 is later
        sleep(1000);

        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_NON_VIRT_POOL);
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(3L, PoolDTO::getQuantity)
            .returns(pools.get(0).getProductId(), PoolDTO::getProductId);

        // sleep to ensure ent3 is later
        sleep(1000);

        EntitlementDTO ent3 = bindHostPool(data, pools, STACKED_VIRT_POOL_3);
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(3L, PoolDTO::getQuantity)
            .returns(pools.get(0).getProductId(), PoolDTO::getProductId);

        assertThat(data.hostClient.consumers().listEntitlements(data.host.getUuid()))
            .hasSize(3);

        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent1.getId());
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(6L, PoolDTO::getQuantity)
            .returns(pools.get(1).getProductId(), PoolDTO::getProductId);
        data.hostClient.consumers().unbindByEntitlementId(data.host.getUuid(), ent3.getId());
        // quantity should not have changed since there was no entitlement
        // specifying virt_limit -- use the last instead.
        assertThat(data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList()))
            .singleElement()
            .returns(6L, PoolDTO::getQuantity)
            .returns(pools.get(1).getProductId(), PoolDTO::getProductId);
    }

    @Test
    public void shouldRegenerateEntCertsWhenSubPoolIsUpdateAndClientChecksIn() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1, STACKED_VIRT_POOL_2));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);

        PoolDTO subPool = getSubPool(data);
        EntitlementDTO initialGuestEnt = ApiClient.MAPPER.convertValue(
            data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1).get(0),
            EntitlementDTO.class);
        assertThat(initialGuestEnt)
            .isNotNull()
            .returns(1, x -> x.getCertificates().size());
        CertificateDTO initialGuestCert = initialGuestEnt.getCertificates().iterator().next();

        // Grab another ent for the host to force a change in the guest's cert.
        EntitlementDTO ent2 = bindHostPool(data, pools, STACKED_VIRT_POOL_2);

        // Listing the certs will cause a regeneration of dirty ents before
        // returning them (simulate client checkin).
        assertThat(data.guestClient.consumers().fetchCertificates(data.guest.getUuid()))
            .singleElement()
            .doesNotReturn(initialGuestCert.getId(), CertificateDTO::getId);

        // Make sure the entitlement picked up the pool's date change:
        subPool = data.guestClient.pools().getPool(subPool.getId(), data.guest.getUuid(), null);
        assertThat(data.guestClient.entitlements().getEntitlement(initialGuestEnt.getId()))
            .returns(subPool.getStartDate(), EntitlementDTO::getStartDate)
            .returns(subPool.getEndDate(), EntitlementDTO::getEndDate);
    }

    @Test
    public void shouldNotRegenerateCertsOnRefreshPoolsWhenSubPoolHasNotBeenChanged() {
        TestData data = new TestData();
        createVirtLimitProductPools(data.owner, data.stackId1);
        List<PoolDTO> pools = getHostPools(data, List.of(STACKED_VIRT_POOL_1));
        EntitlementDTO ent1 = bindHostPool(data, pools, STACKED_VIRT_POOL_1);

        PoolDTO subPool = getSubPool(data);
        EntitlementDTO initialGuestEnt = ApiClient.MAPPER.convertValue(
            data.guestClient.consumers().bindPool(data.guest.getUuid(), subPool.getId(), 1).get(0),
            EntitlementDTO.class);
        assertThat(initialGuestEnt)
            .isNotNull()
            .returns(1, x -> x.getCertificates().size());
        CertificateDTO initialGuestCert = initialGuestEnt.getCertificates().iterator().next();

        // Perform refresh pools -- an old bug marked sub pool as dirty
        ownerApi.refreshPools(data.owner.getKey(), false);
        // Listing the certs will cause a regeneration of dirty ents before
        // returning them (simulate client checkin).
        assertThat(data.guestClient.consumers().fetchCertificates(data.guest.getUuid()))
            .singleElement()
            .returns(initialGuestCert.getId(), CertificateDTO::getId);
    }

    private ProductDTO createVirtLimitProduct1(OwnerDTO owner, String stackId) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.VirtualLimit.withValue("3"),
                ProductAttributes.StackingId.withValue(stackId),
                ProductAttributes.MultiEntitlement.withValue("yes")))
            .providedProducts(Set.of(ownerProductApi.createProductByOwner(
                owner.getKey(), Products.randomEng()))));
    }

    private List<PoolDTO> createVirtLimitProductPools(OwnerDTO owner, String stackId) {
        ProductDTO virtLimitProduct = createVirtLimitProduct1(owner, stackId);
        List<PoolDTO> pools = new ArrayList<>();
        pools.add(ownerApi.createPool(owner.getKey(), Pools.random(
            virtLimitProduct)
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .endDate(OffsetDateTime.now().plusDays(365L))
            .contractNumber(STACKED_VIRT_POOL_1)
            .accountNumber("312")
            .orderNumber("333")));
        pools.add(ownerApi.createPool(owner.getKey(), Pools.random(virtLimitProduct)
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .endDate(OffsetDateTime.now().plusDays(380L))
            .contractNumber(STACKED_VIRT_POOL_2)
            .accountNumber("")
            .orderNumber("")));
        return pools;
    }

    private ProductDTO createVirtLimitProduct2(OwnerDTO owner, String stackId) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.VirtualLimit.withValue("6"),
                ProductAttributes.StackingId.withValue(stackId),
                ProductAttributes.MultiEntitlement.withValue("yes")))
            .providedProducts(Set.of(ownerProductApi.createProductByOwner(
                owner.getKey(), Products.randomEng()))));
    }

    private PoolDTO createVirtLimitProduct2Pool(OwnerDTO owner, String stackId) {
        return ownerApi.createPool(owner.getKey(), Pools.random(
            createVirtLimitProduct2(owner, stackId))
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .endDate(OffsetDateTime.now().plusDays(380L))
            .contractNumber(STACKED_VIRT_POOL_3)
            .accountNumber("312")
            .orderNumber("333"));
    }

    private ProductDTO createRegularStackedProduct(OwnerDTO owner, String stackId) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.StackingId.withValue(stackId),
                ProductAttributes.MultiEntitlement.withValue("yes"),
                ProductAttributes.Sockets.withValue("6")))
            .providedProducts(Set.of(ownerProductApi.createProductByOwner(owner.getKey(),
                Products.randomEng()))));
    }

    private PoolDTO createRegularStackedProductPool(OwnerDTO owner, String stackId) {
        return ownerApi.createPool(owner.getKey(), Pools.random(
            createRegularStackedProduct(owner, stackId))
            .quantity(4L)
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .endDate(OffsetDateTime.now().plusDays(365L))
            .contractNumber(STACKED_NON_VIRT_POOL)
            .accountNumber("")
            .orderNumber(""));
    }

    private ProductDTO createNonStackedProduct(OwnerDTO owner) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.Sockets.withValue("2"),
                ProductAttributes.Cores.withValue("4"))));
    }

    private PoolDTO createNonStackedProductPool(OwnerDTO owner) {
        return ownerApi.createPool(owner.getKey(), Pools.random(
            createNonStackedProduct(owner))
            .quantity(4L)
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .contractNumber(NON_STACKED_POOL)
            .accountNumber("")
            .orderNumber(""));
    }

    private ProductDTO createDerivedProduct(OwnerDTO owner, ProductDTO derivedProvidedProduct) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.Sockets.withValue("8"),
                ProductAttributes.Cores.withValue("6")))
            .providedProducts(Set.of(derivedProvidedProduct)));
    }

    private ProductDTO createStackedDatacenterProduct(OwnerDTO owner, ProductDTO derivedProvidedProduct,
        String stackId) {
        return ownerProductApi.createProductByOwner(owner.getKey(), Products.random()
            .attributes(List.of(
                ProductAttributes.VirtualLimit.withValue("unlimited"),
                ProductAttributes.StackingId.withValue(stackId),
                ProductAttributes.MultiEntitlement.withValue("yes"),
                ProductAttributes.Sockets.withValue("2")))
            .derivedProduct(createDerivedProduct(owner, createDerivedProduct(owner, derivedProvidedProduct)))
            .providedProducts(Set.of(derivedProvidedProduct)));
    }

    private PoolDTO createStackedDatacenterProductPool(OwnerDTO owner, String stackId) {
        return ownerApi.createPool(owner.getKey(), Pools.random(
            createStackedDatacenterProduct(owner, ownerProductApi.createProductByOwner(owner.getKey(),
            Products.randomEng()), stackId))
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .contractNumber(DATACENTER_POOL)
            .accountNumber("")
            .orderNumber(""));
    }

    private PoolDTO createStackedProductDiffIdPool(OwnerDTO owner, String stackId) {
        return ownerApi.createPool(owner.getKey(), Pools.random(
            createRegularStackedProduct(owner, stackId))
            .quantity(2L)
            .startDate(OffsetDateTime.now().minusDays(3L))
            .endDate(OffsetDateTime.now().plusDays(6L))
            .contractNumber(REGULAR_STACKED_WITH_DIFF_STACK_ID)
            .accountNumber("")
            .orderNumber(""));
    }

    private void createTargetPoolAndProducts(OwnerDTO owner, String stackingId) {
        ContentDTO ownerContent1 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ContentDTO ownerContent2 = ownerContentApi.createContent(owner.getKey(), Contents.random());
        ProductDTO ownerDerivedEngProduct = ownerProductApi.createProductByOwner(
            owner.getKey(), Products.random());
        ownerProductApi.addBatchContent(owner.getKey(), ownerDerivedEngProduct.getId(),
            Map.of(ownerContent1.getId(), true, ownerContent2.getId(), true));

        ProductDTO ownerDerivedSku = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .multiplier(1L)
            .attributes(List.of(ProductAttributes.HostLimited.withValue("true"))));

        ProductDTO ownerSku = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.random()
            .multiplier(1L)
            .attributes(List.of(
            ProductAttributes.HostLimited.withValue("true"),
            ProductAttributes.StackingId.withValue(stackingId),
            ProductAttributes.VirtualLimit.withValue("unlimited"))));

        ownerApi.createPool(owner.getKey(), Pools.random(ownerSku)
            .stackId(stackingId)
            .stacked(true)
            .startDate(OffsetDateTime.now().minusSeconds(5L))
            .derivedProductId(ownerDerivedSku.getId())
            .derivedProvidedProducts(Set.of(
                new ProvidedProductDTO()
            .productId(ownerDerivedEngProduct.getId())
            .productName(ownerDerivedEngProduct.getName()))));
    }

    private String getAttributeValue(String name, List<AttributeDTO> attributes) {
        List<AttributeDTO> matches = attributes.stream()
            .filter(x -> name.equals(x.getName()))
            .collect(Collectors.toList());
        return matches.size() > 0 ? matches.get(0).getValue() : null;
    }

    private List<PoolDTO> getHostPools(TestData data, List<String> contracts) {
        return data.hostClient.pools().listPoolsByConsumer(data.host.getUuid()).stream()
            .filter(item -> contracts.contains(item.getContractNumber()))
            .sorted(Comparator.comparing(item -> contracts.indexOf(item.getContractNumber())))
            .collect(Collectors.toList());
    }

    private EntitlementDTO bindHostPool(TestData data, List<PoolDTO> pools, String contract) {
        PoolDTO pool = pools.stream()
            .filter(x -> contract.equals(x.getContractNumber()))
            .collect(Collectors.toList()).get(0);
        return ApiClient.MAPPER.convertValue(
            data.hostClient.consumers().bindPool(data.host.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
    }

    private PoolDTO getSubPool(TestData data) {
        List<PoolDTO> pools = data.guestClient.pools().listPoolsByConsumer(data.guest.getUuid())
            .stream()
            .filter(x -> data.stackId1.equals(x.getSourceStackId()))
            .collect(Collectors.toList());
        return pools.isEmpty() ? null : pools.get(0);
    }

    private static class TestData {
        OwnerDTO owner;
        ApiClient userClient;
        String guestVirtUuid;
        ConsumerDTO guest;
        ApiClient guestClient;
        ConsumerDTO host;
        ApiClient hostClient;
        String stackId1;
        String stackId2;

        public TestData() {
            owner = ownerApi.createOwner(Owners.random());
            userClient = ApiClients.basic(UserUtil.createUser(client, owner));

            guestVirtUuid = StringUtil.random("uuid");
            guest = userClient.consumers().createConsumer(Consumers.random(owner)
                .facts(Map.of("virt.is_guest", "true", "virt.uuid", guestVirtUuid)));
            guestClient = ApiClients.ssl(guest);
            host = userClient.consumers().createConsumer(Consumers.random(owner)
                .guestIds(List.of(new GuestIdDTO().guestId(guestVirtUuid))));
            hostClient = ApiClients.ssl(host);

            stackId1 = StringUtil.random("stack");
            stackId2 = StringUtil.random("stack");
        }
    }
}
