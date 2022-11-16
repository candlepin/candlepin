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

package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// This spec tests virt limited products in a standalone Candlepin deployment.
@OnlyInStandalone
@SpecTest
public class UnmappedGuestSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldAllowANewGuestWithNoHostToAttachToAnUnmappedGuestPool() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        // should only be the base pool and the bonus pool for unmapped guests
        List<PoolDTO> pools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        assertThat(pools).hasSize(2);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        guestClient.consumers().bindPool(guest.getUuid(), unmapped.getId(), 1);

        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);
    }

    @Test
    public void shouldNotAllowANewGuestWithAHostToAttachToAnUnmappedGuestPool() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);
        ConsumerDTO host = Consumers.random(owner);
        host = userClient.consumers().createConsumer(host);
        client.guestIds().updateGuests(host.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        final String guestId = guest.getUuid();
        assertForbidden(() -> guestClient.consumers().bindPool(guestId, unmapped.getId(), 1));
    }

    @Test
    public void shouldEnsureUnmappedGuestWillAttachToUnmappedGuestPoolOnAutoAttach() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        ConsumerDTO host = Consumers.random(owner);
        host = userClient.consumers().createConsumer(host);
        ApiClient hostClient = ApiClients.ssl(host);
        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());

        PoolDTO normal = allPools.stream()
            .filter(x -> x.getType().equals("NORMAL"))
            .collect(Collectors.toList()).get(0);
        hostClient.consumers().bindPool(host.getUuid(), normal.getId(), 1);
        ownerApi.refreshPools(owner.getKey(), false);

        allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        assertThat(allPools).hasSize(3);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        guestClient.consumers().autoBind(guest.getUuid());
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);
        PoolDTO boundPool = ents.get(0).getPool();

        assertThat(getAttributeValue(boundPool, "unmapped_guests_only")).isNotNull();
        assertEquals("UNMAPPED_GUEST", boundPool.getType());
    }

    @Test
    public void shouldEnsureUnmappedGuestWillAttachToUnmappedGuestPoolOnlyOnceOnAutoAttach()
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        ConsumerDTO host = Consumers.random(owner);
        host = userClient.consumers().createConsumer(host);
        ApiClient hostClient = ApiClients.ssl(host);
        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());

        PoolDTO normal = allPools.stream()
            .filter(x -> x.getType().equals("NORMAL"))
            .collect(Collectors.toList()).get(0);
        hostClient.consumers().bindPool(host.getUuid(), normal.getId(), 1);
        ownerApi.refreshPools(owner.getKey(), false);

        assertThat(userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId())).hasSize(3);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        guestClient.consumers().autoBind(guest.getUuid());
        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);

        guestClient.consumers().autoBind(guest.getUuid());
        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);

        guestClient.consumers().autoBind(guest.getUuid());
        assertThat(guestClient.consumers().listEntitlements(guest.getUuid())).hasSize(1);
    }

    @Test
    public void shouldNotHavePoolEndDateOnGuestEntitlement() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        guestClient.consumers().bindPool(guest.getUuid(), unmapped.getId(), 1);

        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);
        assertNotEquals(ents.get(0).getEndDate(), unmapped.getEndDate());
    }

    @Test
    public void shouldRevokeTheUnmappedGuestPoolOnceTheGuestIsMapped() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO host = Consumers.random(owner);
        host = userClient.consumers().createConsumer(host);
        ApiClient hostClient = ApiClients.ssl(host);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        guestClient.consumers().bindPool(guest.getUuid(), unmapped.getId(), 1);
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);
        String originalEntId = ents.get(0).getId();

        client.guestIds().updateGuests(host.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));

        ents = guestClient.consumers().listEntitlements(
            guest.getUuid(), null, true, List.of(), null, null, null, null);
        assertThat(ents).isNotNull()
            .singleElement()
            .doesNotReturn(originalEntId, EntitlementDTO::getId);
    }

    @Test
    public void shouldHideUnmappedGuestPoolsFromPoolListsIfInstructedTo() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        assertThat(allPools.stream()
            .filter(x -> virtLimitProduct.getId().equals(x.getProductId()))).hasSize(allPools.size());
        List<PoolDTO> filteredPools = userClient.owners().listOwnerPoolsByProductWithAttributes(
            owner.getKey(), virtLimitProduct.getId(), List.of("unmapped_guests_only:!true"));
        assertThat(filteredPools).hasSize(allPools.size() - 1);

        assertThat(filteredPools.stream()
            .filter(x -> x.getAttributes().stream()
                .filter(y -> y.getName().equals("unmapped_guests_only") && y.getValue().equals("true"))
                .collect(Collectors.toList()).size() != 0)
            .collect(Collectors.toList())).hasSize(0);
    }

    @Test
    public void shouldRevokeEntitlementFromAnotherHostDuringAnAutoAttach() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO host1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient hostClient1 = ApiClients.ssl(host1);
        client.guestIds().updateGuests(host1.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));
        ConsumerDTO host2 = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient hostClient2 = ApiClients.ssl(host2);

        PoolDTO basePool = allPools.stream()
            .filter(x -> x.getAttributes().stream()
            .filter(y -> y.getName().equals("unmapped_guests_only") && y.getValue().equals("true"))
            .collect(Collectors.toList()).size() == 0)
            .collect(Collectors.toList()).get(0);
        hostClient1.consumers().bindPool(host1.getUuid(), basePool.getId(), 1);
        hostClient2.consumers().bindPool(host2.getUuid(), basePool.getId(), 1);
        ownerApi.refreshPools(owner.getKey(), false);

        // should be the base pool, the bonus pool for unmapped guests, plus two pools for the hosts' guests
        assertThat(userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId())).hasSize(4);

        guestClient.consumers().bindProduct(guest.getUuid(), virtLimitProduct.getId());
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(ents).hasSize(1);

        PoolDTO boundPool = ents.get(0).getPool();
        String requiresHost = getAttributeValue(boundPool, "requires_host");
        assertEquals(requiresHost, host1.getUuid());

        // should not remove until attached to new host
        client.guestIds().updateGuests(host1.getUuid(), List.of());
        ents = guestClient.consumers().listEntitlements(
            guest.getUuid(), null, true, List.of(), null, null, null, null);
        assertThat(ents).hasSize(1);

        // now migrate the guest
        client.guestIds().updateGuests(host2.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));

        guestClient.consumers().bindProduct(guest.getUuid(), virtLimitProduct.getId());
        ents = guestClient.consumers().listEntitlements(
            guest.getUuid(), null, true, List.of(), null, null, null, null);
        assertThat(ents).hasSize(1);

        boundPool = ents.get(0).getPool();
        requiresHost = getAttributeValue(boundPool, "requires_host");
        assertEquals(requiresHost, host2.getUuid());
    }

    @Test
    public void shouldReplaceEntitlementFromAnotherHostWithUnmappedDuringAnAutoAttach() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        ConsumerDTO host1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient hostClient1 = ApiClients.ssl(host1);
        client.guestIds().updateGuests(host1.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));
        ConsumerDTO host2 = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient hostClient2 = ApiClients.ssl(host2);

        PoolDTO basePool = allPools.stream()
            .filter(x -> x.getAttributes().stream()
            .filter(y -> y.getName().equals("unmapped_guests_only") && y.getValue().equals("true"))
            .collect(Collectors.toList()).size() == 0)
            .collect(Collectors.toList()).get(0);
        hostClient1.consumers().bindPool(host1.getUuid(), basePool.getId(), 1);
        hostClient2.consumers().bindPool(host2.getUuid(), basePool.getId(), 1);
        ownerApi.refreshPools(owner.getKey(), false);

        // should be the base pool, the bonus pool for unmapped guests, plus two pools for the hosts' guests
        assertThat(userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId())).hasSize(4);

        guestClient.consumers().bindProduct(guest.getUuid(), virtLimitProduct.getId());

        // should not remove until attached to new host
        client.guestIds().updateGuests(host1.getUuid(), List.of());

        // do not finish the migration
        // client.guestIds().updateGuests(host2.getUuid(), List.of(new GuestIdDTO().guestId(uuid)));

        guestClient.consumers().bindProduct(guest.getUuid(), virtLimitProduct.getId());
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(
            guest.getUuid(), null, true, List.of(), null, null, null, null);
        assertThat(ents).hasSize(1);

        assertEquals("UNMAPPED_GUEST", ents.get(0).getPool().getType());
    }

    @Test
    public void shouldComplianceStatusForEntitledUnmappedGuestBePartial() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, virtLimitProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        guestClient.consumers().bindPool(guest.getUuid(), unmapped.getId(), 1);

        ComplianceStatusDTO complianceStatus = guestClient.consumers().getComplianceStatus(guest.getUuid(),
            null);
        assertThat(complianceStatus)
            .returns("partial", ComplianceStatusDTO::getStatus)
            .returns(false, ComplianceStatusDTO::getCompliant);
        assertThat(complianceStatus.getReasons()).hasSize(1);
    }

    @Test
    public void shouldComplianceStatusForEntitledUnmappedGuestBePartialWithoutInstalledProduct()
        throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO virtLimitProduct = createVirtLimitPool(owner);

        String uuid = StringUtil.random("system");
        ConsumerDTO guest = createGuest(owner, userClient, uuid, null);
        ApiClient guestClient = ApiClients.ssl(guest);

        List<PoolDTO> allPools = userClient.owners().listOwnerPoolsByProduct(
            owner.getKey(), virtLimitProduct.getId());
        PoolDTO unmapped = allPools.stream()
            .filter(x -> x.getType().equals("UNMAPPED_GUEST"))
            .collect(Collectors.toList()).get(0);
        guestClient.consumers().bindPool(guest.getUuid(), unmapped.getId(), 1);
        List<EntitlementDTO> ents = guestClient.consumers().listEntitlements(
            guest.getUuid(), null, true, List.of(), null, null, null, null);
        assertThat(ents).hasSize(1);

        ComplianceStatusDTO complianceStatus = guestClient.consumers().getComplianceStatus(
            guest.getUuid(), null);
        assertThat(complianceStatus)
            .returns("partial", ComplianceStatusDTO::getStatus)
            .returns(false, ComplianceStatusDTO::getCompliant);
        assertThat(complianceStatus.getReasons()).hasSize(1);
    }

    private ProductDTO createVirtLimitPool(OwnerDTO owner) throws InterruptedException {
        ProductDTO virtLimitProduct = Products.random()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("unlimited"))
            .addAttributesItem(new AttributeDTO().name("host_limited").value("true"))
            .addAttributesItem(new AttributeDTO().name("physical_only").value("true"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"));
        virtLimitProduct = ownerProductApi.createProductByOwner(owner.getKey(), virtLimitProduct);
        PoolDTO pool = Pools.random(virtLimitProduct)
            .subscriptionId(StringUtil.random("sub"))
            .subscriptionSubKey(StringUtil.random("key"))
            .upstreamPoolId(StringUtil.random("pool"));
        ownerApi.createPool(owner.getKey(), pool);
        ownerApi.refreshPools(owner.getKey(), false);
        return virtLimitProduct;
    }

    private ConsumerDTO createGuest(OwnerDTO owner, ApiClient userClient, String virtUuid,
        ProductDTO installedProduct) {
        ConsumerDTO guest = Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", virtUuid, "virt.is_guest", "true", "uname.machine", "x86_64"));
        if (installedProduct != null) {
            guest.installedProducts(Set.of(new ConsumerInstalledProductDTO()
                .productId(installedProduct.getId()).productName(installedProduct.getName())));
        }
        return userClient.consumers().createConsumer(guest);
    }

    private String getAttributeValue(PoolDTO pool, String name) {
        return pool.getAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }
}
