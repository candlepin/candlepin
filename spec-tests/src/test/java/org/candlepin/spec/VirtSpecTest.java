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

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ComplianceReasonDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.PoolAttributes;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@SpecTest
@OnlyInStandalone
@SuppressWarnings("indentation")
class VirtSpecTest {

    private static final String GUEST_UUID = StringUtil.random("system.uuid");
    private static final String OTHER_GUEST_UUID = StringUtil.random("system.uuid");

    private ApiClient admin;
    private ApiClient user;
    private OwnerDTO owner;
    private ConsumerDTO host;
    private ApiClient hostClient;
    private ConsumerDTO guest;
    private ApiClient guestClient;

    @BeforeEach
    void setUp() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        user = ApiClients.basic(UserUtil.createUser(admin, owner));

        // Setup host
        host = createHost();
        hostClient = ApiClients.ssl(host);

        // Setup guest
        guest = createGuest(owner, GUEST_UUID);
        guestClient = ApiClients.ssl(guest);
    }

    @Test
    public void shouldAttachHostProvidedPoolsBeforeOtherAvailablePools() throws ApiException {
        ConsumerDTO otherGuest = createGuest(owner, OTHER_GUEST_UUID);
        ApiClient otherGuestClient = ApiClients.ssl(otherGuest);
        ConsumerDTO otherHost = createHost();
        ApiClient otherHostClient = ApiClients.ssl(otherHost);
        ProductDTO derivedProduct1 = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.Cores.withValue("2"),
                ProductAttributes.Sockets.withValue("4"),
                ProductAttributes.StackingId.withValue("stackme1-derived")));

        ProductDTO datacenterProduct1 = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.VirtualLimit.withValue("unlimited"),
                ProductAttributes.Sockets.withValue("2"),
                ProductAttributes.StackingId.withValue("stackme1"),
                ProductAttributes.MultiEntitlement.withValue("yes")).derivedProduct(derivedProduct1));

        ProductDTO derivedProduct2 = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.Cores.withValue("2"),
                ProductAttributes.Sockets.withValue("4"),
                ProductAttributes.StackingId.withValue("stackme2-derived")));

        ProductDTO datacenterProduct2 = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.VirtualLimit.withValue("unlimited"),
                ProductAttributes.Sockets.withValue("2"),
                ProductAttributes.StackingId.withValue("stackme2"),
                ProductAttributes.MultiEntitlement.withValue("yes")).derivedProduct(derivedProduct2));

        ProductDTO bothProducts = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.Type.withValue("MKT"))
            ).providedProducts(Set.of(
                derivedProduct1, derivedProduct2
            ));

        admin.owners().createPool(owner.getKey(), Pools.random(datacenterProduct1));
        admin.owners().createPool(owner.getKey(), Pools.random(datacenterProduct1));
        admin.owners().createPool(owner.getKey(), Pools.random(bothProducts).quantity(1L));

        updateInstalledProducts(otherGuestClient, otherGuest, toInstalled(derivedProduct1, derivedProduct2));

        hostClient.consumers().bindProduct(host.getUuid(), datacenterProduct1.getId());
        hostClient.consumers().bindProduct(host.getUuid(), datacenterProduct2.getId());
        otherHostClient.consumers().bindProduct(otherHost.getUuid(), datacenterProduct1.getId());
        otherHostClient.consumers().bindProduct(otherHost.getUuid(), datacenterProduct2.getId());

        linkHostToGuests(hostClient, host, OTHER_GUEST_UUID);
        assertThatEntitlementsOf(otherGuestClient, otherGuest).isEmpty();

        otherGuestClient.consumers().autoBind(otherGuest.getUuid());

        assertThatEntitlementsOf(otherGuestClient, otherGuest)
            .hasSize(1)
            .allSatisfy(entitlement -> {
                assertProductIdIn(entitlement, derivedProduct1, derivedProduct2);
                assertRequiredHost(entitlement, host);
            });

        // Guest migration
        unlinkHostsGuests(hostClient, host);
        linkHostToGuests(otherHostClient, otherHost, OTHER_GUEST_UUID);

        assertThatEntitlementsOf(otherGuestClient, otherGuest)
            .hasSize(1)
            .allSatisfy(entitlement -> {
                assertProductIdIn(entitlement, derivedProduct1, derivedProduct2);
                assertRequiredHost(entitlement, otherHost);
            });
    }

    @Test
    public void shouldCreateVirtOnlyPoolForHostsGuests() throws ApiException {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ConsumerDTO otherGuest = createGuest(owner, OTHER_GUEST_UUID);
        ApiClient otherGuestClient = ApiClients.ssl(otherGuest);
        ProductDTO virtLimitProduct = createVirtLimitProduct("3");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);

        // Guest 1 should be able to use the pool.
        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        // Other guest should not be able to use the pool as this guest is not on the correct host.
        assertForbidden(() -> otherGuestClient.consumers()
            .bindPool(otherGuest.getUuid(), guestPool.getId(), 1));
    }

    @Test
    public void shouldListHostRestrictedPoolOnlyForItsGuests() throws ApiException {
        ProductDTO virtLimitProduct = createVirtLimitProduct("3");
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));

        List<PoolDTO> guestPols = guestClient.pools().listPoolsByConsumer(guest.getUuid());
        assertThat(guestPols).hasSize(2);

        List<PoolDTO> normalPools = findNormalPools(guestPols);
        assertThat(normalPools).hasSize(1);
    }

    @Test
    public void shouldCheckArchMatchesAndGuestLimitEnforcedOnRestrictedSubPools() throws ApiException {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        String stackId = StringUtil.random("test_stack");
        ProductDTO archVirtProduct = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.VirtualLimit.withValue("3"),
                ProductAttributes.GuestLimit.withValue("1"),
                ProductAttributes.Arch.withValue("ppc64"),
                ProductAttributes.MultiEntitlement.withValue("yes"),
                ProductAttributes.StackingId.withValue(stackId)));
        PoolDTO archVirtPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(archVirtProduct));

        hostClient.consumers().bindPool(host.getUuid(), archVirtPool.getId(), 1);

        List<PoolDTO> guestPools = guestClient.pools()
            .listPoolsByConsumerAndProduct(guest.getUuid(), archVirtProduct.getId());
        assertThat(guestPools).hasSize(2);
        PoolDTO guestPool = guestPools.stream()
            .filter(pool -> isHostRestricted(pool, host))
            .findFirst()
            .orElseThrow();

        linkHostToGuests(guestClient, guest, "random1", "random2");
        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        ComplianceStatusDTO compliance = guestClient.consumers().getComplianceStatus(guest.getUuid(), null);
        assertThat(compliance.getReasons())
            .hasSize(2)
            .map(ComplianceReasonDTO::getKey)
            .containsOnly("GUEST_LIMIT", "ARCH");
    }

    // Covers BZ 1379849
    @Test
    public void shouldRevokeGuestEntitlementsWhenMigrationHappens() {
        ConsumerDTO otherHost = createHost();
        ApiClient otherHostClient = ApiClients.ssl(otherHost);
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("3");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);
        // Adding a product to guest that cannot be covered
        updateInstalledProducts(guestClient, guest, toInstalled(Products.random()));

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        // Guest changes the hypervisor. This will trigger revocation of
        // the entitlement to guest_pool (because it requires_host) and
        // it will also trigger unsuccessful autobind (because the
        // someNonExistentProduct cannot be covered)
        unlinkHostsGuests(hostClient, host);
        linkHostToGuests(otherHostClient, otherHost, GUEST_UUID);
        assertThatEntitlementsOf(guestClient, guest).isEmpty();
    }

    @Test
    public void shouldRevokeGuestEntitlementsWhenHostUnbinds() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        JsonNode entitlement = hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        hostClient.consumers().unbindByEntitlementId(host.getUuid(), entitlement.get(0).get("id").asText());

        assertThatEntitlementsOf(guestClient, guest).isEmpty();
    }

    @Test
    public void shouldRevokeGuestEntitlementsWhenHostUnregisters() {
        ConsumerDTO otherGuest = createGuest(owner, OTHER_GUEST_UUID);
        ApiClient otherGuestClient = ApiClients.ssl(otherGuest);
        linkHostToGuests(hostClient, host, GUEST_UUID, OTHER_GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        otherGuestClient.consumers().bindPool(otherGuest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(otherGuestClient, otherGuest).hasSize(1);

        // without the fix for #811581, this will 500
        hostClient.consumers().deleteConsumer(host.getUuid());

        assertThatEntitlementsOf(guestClient, guest).isEmpty();
        assertThatEntitlementsOf(otherGuestClient, otherGuest).isEmpty();
    }

    @Test
    public void shouldRevokeGuestEntitlementsAndRemoveActivationKeysWhenHostUnbinds() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);

        ActivationKeyDTO activationKey = admin.owners()
            .createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        admin.activationKeys().addPoolToKey(activationKey.getId(), guestPool.getId(), 1L);

        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        hostClient.consumers().unbindAll(host.getUuid());

        assertThatEntitlementsOf(guestClient, guest).isEmpty();
    }

    @Test
    public void shouldNotRevokeGuestEntitlementsWhenHostStopsReportingGuestID() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);

        PoolDTO guestPool = findGuestPool(guestClient, guest);
        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        unlinkHostsGuests(hostClient, host);

        assertThatEntitlementsOf(guestClient, guest).hasSize(1);
    }

    @Test
    public void shouldNotRevokeGuestEntitlementsWhenHostRemovesGuestIdThroughNewApi() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners().
            createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);

        PoolDTO guestPool = findGuestPool(guestClient, guest);
        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);

        hostClient.guestIds().deleteGuest(host.getUuid(), GUEST_UUID, false);

        assertThatEntitlementsOf(guestClient, guest).hasSize(1);
    }

    @Test
    public void shouldLoseEntitlementWhenGuestStopsAndIsRestartedElsewhere() {
        ConsumerDTO otherHost = createHost();
        ApiClient otherHostClient = ApiClients.ssl(otherHost);
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("2");
        PoolDTO virtLimitPool = admin.owners().
            createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);

        PoolDTO guestPool = findGuestPool(guestClient, guest);
        guestClient.consumers().bindPool(guest.getUuid(), guestPool.getId(), 1);

        unlinkHostsGuests(hostClient, host);
        linkHostToGuests(otherHostClient, otherHost, GUEST_UUID);

        assertThatEntitlementsOf(guestClient, guest).isEmpty();
    }

    @Test
    public void shouldAutoHealWhenGuestIsMigratedToAnotherHost() {
        ConsumerDTO otherHost = createHost();
        ApiClient otherHostClient = ApiClients.ssl(otherHost);
        ConsumerDTO otherGuest = createGuest(owner, OTHER_GUEST_UUID);
        ApiClient otherGuestClient = ApiClients.ssl(otherGuest);
        linkHostToGuests(hostClient, host, GUEST_UUID);

        ProductDTO virtLimitProduct = createVirtLimitProduct("3");
        // create a second product in order to test bz #786730
        ProductDTO otherProduct = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO virtLimitPool = admin.owners()
            .createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(otherProduct));

        hostClient.consumers().bindPool(host.getUuid(), virtLimitPool.getId(), 1);
        PoolDTO guestPool = findGuestPool(guestClient, guest);

        updateInstalledProducts(guestClient, guest, toInstalled(virtLimitProduct, otherProduct));
        guestClient.consumers().autoBind(guest.getUuid());
        assertThatEntitlementsOf(guestClient, guest).hasSize(2);

        List<EntitlementDTO> virtLimitEntitlements = guestClient.consumers()
            .listEntitlements(guest.getUuid(), virtLimitProduct.getId());
        String originalPoolId = virtLimitEntitlements.stream()
            .map(EntitlementDTO::getPool)
            .filter(entitlement -> entitlement.getProductId().equals(virtLimitProduct.getId()))
            .map(PoolDTO::getId)
            .findFirst()
            .orElseThrow();

        // Add other guest to host so that we can make sure that only the first guest's
        // entitlements are revoked.
        linkHostToGuests(hostClient, host, OTHER_GUEST_UUID, GUEST_UUID);

        otherGuestClient.consumers().bindPool(otherGuest.getUuid(), guestPool.getId(), 1);
        assertThatEntitlementsOf(otherGuestClient, otherGuest).hasSize(1);

        // Other host reports the new guest before the first Host reports it removed.
        // this is where the error would occur without the 786730 fix
        linkHostToGuests(otherHostClient, otherHost, GUEST_UUID);

        // The old host-specific entitlement should not be on the guest anymore (see 768872 comment #41)
        // Instead the guest should be auto-healed for other host
        // second_product's entitlement should still be there, though.
        assertThatEntitlementsOf(guestClient, guest, otherProduct).hasSize(1);
        List<EntitlementDTO> hostEntitlements = listEntitlementsWithRegen(
            guestClient, guest, virtLimitProduct);
        assertThat(hostEntitlements)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getId)
            .doesNotContainSequence(originalPoolId);
        // Entitlements should have remained the same for other guest as its host is the same.
        assertThatEntitlementsOf(otherGuestClient, otherGuest).hasSize(1);
    }

    @Test
    public void shouldHealTheHostBeforeHealingItself() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("1");
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));

        updateInstalledProducts(guestClient, guest, toInstalled(virtLimitProduct));
        updateInstalledProducts(hostClient, host, Collections.emptySet());
        autoHealConsumer(hostClient, host);

        assertThatEntitlementsOf(hostClient, host).isEmpty();
        assertThatEntitlementsOf(guestClient, guest).isEmpty();

        guestClient.consumers().autoBind(guest.getUuid());

        assertThatEntitlementsOf(hostClient, host).hasSize(1);
        assertThatEntitlementsOf(guestClient, guest).hasSize(1);
    }

    @Test
    public void shouldNotBindProductsOnHostIfVirtOnlyAreAlreadyAvailableForGuest() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("1");
        ProductDTO otherProduct = admin.ownerProducts().createProduct(owner.getKey(),
            Products.withAttributes(ProductAttributes.VirtualOnly.withValue("true"))
                .providedProducts(Set.of(virtLimitProduct)));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(otherProduct));
        Set<ConsumerInstalledProductDTO> installedProducts = toInstalled(virtLimitProduct);
        updateInstalledProducts(guestClient, guest, installedProducts);
        linkHostToGuests(hostClient, host, GUEST_UUID);
        updateInstalledProducts(hostClient, host, Collections.emptySet());
        autoHealConsumer(hostClient, host);

        assertThatEntitlementsOf(hostClient, host).isEmpty();
        assertThatEntitlementsOf(guestClient, guest).isEmpty();

        guestClient.consumers().autoBind(guest.getUuid());

        assertThatEntitlementsOf(guestClient, guest).hasSize(1);
        assertThatEntitlementsOf(hostClient, host).isEmpty();
    }

    @Test
    public void shouldNotHealHostIfNothingIsInstalled() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("1");
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        Set<ConsumerInstalledProductDTO> installedProducts = toInstalled(virtLimitProduct);
        updateInstalledProducts(hostClient, host, installedProducts);
        updateInstalledProducts(guestClient, guest, Collections.emptySet());
        autoHealConsumer(hostClient, host);

        assertThatEntitlementsOf(hostClient, host).isEmpty();
        assertThatEntitlementsOf(guestClient, guest).isEmpty();

        guestClient.consumers().autoBind(guest.getUuid());

        assertThatEntitlementsOf(hostClient, host).isEmpty();
        assertThatEntitlementsOf(guestClient, guest).isEmpty();
    }

    @Test
    public void shouldNotHealTheHostIfTheProductIsAlreadyCompliant() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("1");
        ProductDTO otherProduct = admin.ownerProducts().createProduct(owner.getKey(),
            Products.random().providedProducts(Set.of(virtLimitProduct)));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(otherProduct));
        Set<ConsumerInstalledProductDTO> installedProducts = toInstalled(virtLimitProduct);
        updateInstalledProducts(guestClient, guest, installedProducts);
        linkHostToGuests(hostClient, host, GUEST_UUID);
        updateInstalledProducts(hostClient, host, installedProducts);

        assertThatEntitlementsOf(hostClient, host).isEmpty();
        assertThatEntitlementsOf(guestClient, guest).isEmpty();

        List<PoolDTO> poolDTOS = guestClient.pools().listPoolsByOwner(owner.getId());
        for (PoolDTO poolDTO : poolDTOS) {
            if (otherProduct.getId().equals(poolDTO.getProductId())) {
                hostClient.consumers().bindPool(host.getUuid(), poolDTO.getId(), 1);
            }
        }
        assertThatEntitlementsOf(hostClient, host).hasSize(1);
        assertThatEntitlementsOf(guestClient, guest).isEmpty();

        guestClient.consumers().autoBind(guest.getUuid());

        assertThatEntitlementsOf(guestClient, guest).hasSize(1);
        List<EntitlementDTO> hostEntitlementsAfterBind = hostClient.consumers()
            .listEntitlements(host.getUuid());
        assertThat(hostEntitlementsAfterBind)
            .hasSize(1)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getProductId)
            .containsOnly(otherProduct.getId());
    }

    @Test
    public void shouldNotHealOtherHostProducts() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO virtLimitProduct = createVirtLimitProduct("1");
        ProductDTO otherProduct = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(otherProduct));
        Set<ConsumerInstalledProductDTO> guestInstalledProducts = toInstalled(virtLimitProduct);
        Set<ConsumerInstalledProductDTO> hostInstalledProducts = toInstalled(otherProduct);

        updateInstalledProducts(hostClient, host, hostInstalledProducts);
        updateInstalledProducts(guestClient, guest, guestInstalledProducts);

        autoHealConsumer(hostClient, host);
        List<EntitlementDTO> hostEntitlements = hostClient.consumers().listEntitlements(host.getUuid());
        List<EntitlementDTO> guestEntitlements = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(hostEntitlements).isEmpty();
        assertThat(guestEntitlements).isEmpty();

        guestClient.consumers().autoBind(guest.getUuid());

        List<EntitlementDTO> hostEntitlementsAfterBind = hostClient.consumers()
            .listEntitlements(host.getUuid());
        List<EntitlementDTO> guestEntitlementsAfterBind = guestClient.consumers()
            .listEntitlements(guest.getUuid());
        assertThat(guestEntitlementsAfterBind).hasSize(1);
        assertThat(hostEntitlementsAfterBind)
            .hasSize(1)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getProductId)
            .containsOnly(virtLimitProduct.getId());
    }

    @Test
    public void shouldNotAutoBindVirtLimitingProductsThatDoNotCoverGuests() {
        createGuest(owner, OTHER_GUEST_UUID);
        ProductDTO virtLimitProduct = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.GuestLimit.withValue("1")));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        Set<ConsumerInstalledProductDTO> installedProducts = toInstalled(virtLimitProduct);
        updateInstalledProducts(hostClient, host, installedProducts);
        linkHostToGuests(hostClient, host, GUEST_UUID, OTHER_GUEST_UUID);

        assertThatEntitlementsOf(hostClient, host).isEmpty();

        hostClient.consumers().autoBind(host.getUuid());

        assertThatEntitlementsOf(hostClient, host).isEmpty();
    }

    @Test
    public void shouldAutoBindVirtLimitingProductsThatDoCoverGuests() {
        ProductDTO virtLimitProduct = createVirtLimitProduct("8");
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(virtLimitProduct));
        Set<ConsumerInstalledProductDTO> installedProducts = toInstalled(virtLimitProduct);
        updateInstalledProducts(hostClient, host, installedProducts);
        linkHostToGuests(hostClient, host, GUEST_UUID, OTHER_GUEST_UUID);

        assertThatEntitlementsOf(hostClient, host).isEmpty();

        hostClient.consumers().autoBind(host.getUuid());

        List<EntitlementDTO> entitlementAfterBind = hostClient.consumers().listEntitlements(host.getUuid());
        assertThat(entitlementAfterBind)
            .hasSize(1)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getProductId)
            .containsOnly(virtLimitProduct.getId());
    }

    @Test
    public void shouldNotChangeTheQuantityOnSubPoolWhenTheSourceEntitlementQuantityChanges() {
        linkHostToGuests(hostClient, host, GUEST_UUID);
        ProductDTO product = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.VirtualLimit.withValue("3"),
                ProductAttributes.MultiEntitlement.withValue("yes")));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(product));

        List<PoolDTO> createdPools = guestClient.pools()
            .listPoolsByProduct(owner.getId(), product.getId());
        assertThat(createdPools).hasSize(2);

        PoolDTO normalPool = findNormalPools(createdPools).stream()
            .findFirst()
            .orElseThrow();

        JsonNode entitlement = hostClient.consumers().bindPool(host.getUuid(), normalPool.getId(), 3);

        List<PoolDTO> poolsAfterBind = guestClient.pools()
            .listPoolsByProduct(owner.getId(), product.getId());
        assertThat(poolsAfterBind).hasSize(3);
        assertThat(findNormalPools(poolsAfterBind))
            .filteredOn(pool -> !normalPool.getId().equals(pool.getId()))
            .hasSize(1)
            .map(PoolDTO::getQuantity)
            .containsOnly(3L);

        // reduce entitlement
        hostClient.entitlements()
            .updateEntitlement(entitlement.get(0).get("id").asText(), toUpdate(entitlement, 2));

        List<PoolDTO> poolsAfterUpdate = guestClient.pools()
            .listPoolsByProduct(owner.getId(), product.getId());
        assertThat(poolsAfterUpdate).hasSize(3);
        assertThat(findNormalPools(poolsAfterBind))
            .filteredOn(pool -> !normalPool.getId().equals(pool.getId()))
            .hasSize(1)
            .map(PoolDTO::getQuantity)
            .containsOnly(3L);
    }

    @Test
    public void shouldNotBlockVirtGuest() {
        ProductDTO instanceBased = admin.ownerProducts()
            .createProduct(owner.getKey(), Products.withAttributes(
                ProductAttributes.InstanceMultiplier.withValue("2"),
                ProductAttributes.MultiEntitlement.withValue("yes")));
        admin.owners().createPool(owner.getKey(), Pools.randomUpstream(instanceBased));
        linkHostToGuests(hostClient, host, GUEST_UUID);

        PoolDTO pool = guestClient.pools()
            .listPoolsByConsumerAndProduct(guest.getUuid(), instanceBased.getId())
            .stream()
            .findFirst()
            .orElseThrow();

        guestClient.consumers().bindPool(guest.getUuid(), pool.getId(), 3);
        List<EntitlementDTO> entitlement = guestClient.consumers().listEntitlements(guest.getUuid());
        assertThat(entitlement).isNotEmpty();
    }

    private Boolean isHostRestricted(PoolDTO pool, ConsumerDTO host) {
        return getAttribute(pool, "requires_host")
            .map(AttributeDTO::getValue)
            .map(host.getUuid()::equals)
            .orElse(false);
    }

    private ProductDTO createVirtLimitProduct(String limit) {
        ProductDTO product = Products.withAttributes(
            ProductAttributes.VirtualLimit.withValue(limit));
        return admin.ownerProducts().createProduct(owner.getKey(), product);
    }

    private ListAssert<EntitlementDTO> assertThatEntitlementsOf(ApiClient client, ConsumerDTO consumer) {
        return assertThat(client.consumers().listEntitlementsWithRegen(consumer.getUuid()));
    }

    private ListAssert<EntitlementDTO> assertThatEntitlementsOf(
        ApiClient client, ConsumerDTO consumer, ProductDTO product) {
        return assertThat(client.consumers().listEntitlementsWithRegen(consumer.getUuid(), product.getId()));
    }

    private List<EntitlementDTO> listEntitlementsWithRegen(
        ApiClient client, ConsumerDTO consumer, ProductDTO product) {
        return client.consumers().listEntitlementsWithRegen(consumer.getUuid(), product.getId());
    }

    private void autoHealConsumer(ApiClient client, ConsumerDTO consumer) {
        client.consumers().updateConsumer(consumer.getUuid(), consumer.autoheal(true));
    }

    private void updateInstalledProducts(ApiClient client, ConsumerDTO consumer,
        Set<ConsumerInstalledProductDTO> hostInstalledProducts) {
        client.consumers()
            .updateConsumer(consumer.getUuid(), consumer.installedProducts(hostInstalledProducts));
    }

    private Set<ConsumerInstalledProductDTO> toInstalled(ProductDTO... products) {
        return Arrays.stream(products)
            .map(Products::toInstalled)
            .collect(Collectors.toSet());
    }

    private EntitlementDTO toUpdate(JsonNode entitlement, int quantity) {
        return new EntitlementDTO()
            .id(entitlement.get(0).get("id").asText())
            .quantity(quantity);
    }

    private static Optional<AttributeDTO> getAttribute(PoolDTO pool, String key) {
        return pool.getAttributes().stream()
            .filter(attribute -> key.equalsIgnoreCase(attribute.getName()))
            .findFirst();
    }

    private void assertRequiredHost(EntitlementDTO entitlement, ConsumerDTO host) {
        Map<String, String> collect = entitlement.getPool().getAttributes().stream()
            .collect(Collectors.toMap(AttributeDTO::getName, AttributeDTO::getValue));
        assertThat(collect).containsEntry("requires_host", host.getUuid());
    }

    private static void assertProductIdIn(EntitlementDTO entitlement, ProductDTO... products) {
        Set<String> productIds = Arrays.stream(products)
            .map(ProductDTO::getId)
            .collect(Collectors.toSet());
        assertThat(entitlement)
            .extracting(EntitlementDTO::getPool)
            .extracting(PoolDTO::getProductId)
            .isIn(productIds);
    }

    private void linkHostToGuests(ApiClient hostClient, ConsumerDTO host, String... guestUuids) {
        List<GuestIdDTO> guestIds = Arrays.stream(guestUuids)
            .map(this::toGuestId)
            .map(guestIdDTO -> guestIdDTO.attributes(
                Map.ofEntries(
                    Map.entry("active", "1"),
                    Map.entry("virtWhoType", "libvirt")
                )
            ))
            .collect(Collectors.toList());
        linkHostToGuests(hostClient, host, guestIds);
    }

    private void linkHostToGuests(ApiClient hostClient, ConsumerDTO host, List<GuestIdDTO> guestIds) {
        hostClient.consumers().updateConsumer(host.getUuid(), host.guestIds(guestIds));
    }

    private void unlinkHostsGuests(ApiClient hostClient, ConsumerDTO host) {
        hostClient.consumers().updateConsumer(host.getUuid(), host.guestIds(List.of()));
    }

    private PoolDTO findGuestPool(ApiClient guestClient, ConsumerDTO guest) {
        List<PoolDTO> guestPools = guestClient.pools().listPoolsByConsumer(guest.getUuid());
        return findGuestPool(guestPools);
    }

    private PoolDTO findGuestPool(List<PoolDTO> pools) {
        return pools.stream()
            .sorted(Comparator.comparing(PoolDTO::getId))
            .filter(poolDTO -> poolDTO.getSourceEntitlement() != null)
            .findFirst()
            .orElseThrow();
    }

    private ConsumerDTO createHost() {
        return user.consumers().createConsumer(Consumers.random(owner));
    }

    private ConsumerDTO createGuest(OwnerDTO owner, String virtUuid) {
        return user.consumers().createConsumer(Consumers.random(owner).facts(Map.ofEntries(
            Facts.VirtUuid.withValue(virtUuid),
            Facts.VirtIsGuest.withValue("true"),
            Facts.Arch.withValue("x86_64")
        )));
    }

    /**
     * Method takes a list of pools and filters out all unmapped guest pools.
     *
     * @param pools a list of pools
     * @return a list of pools without unmapped guest pools
     */
    private List<PoolDTO> findNormalPools(List<PoolDTO> pools) {
        return pools.stream()
            .filter(Predicate.not(this::isUnmapped))
            .collect(Collectors.toList());
    }

    private boolean isUnmapped(PoolDTO pool) {
        return pool.getAttributes().stream()
            .filter(PoolAttributes.UnmappedGuestsOnly::isKeyOf)
            .map(AttributeDTO::getValue)
            .anyMatch(Boolean::parseBoolean);
    }

    private GuestIdDTO toGuestId(String guestId) {
        return new GuestIdDTO().guestId(guestId);
    }

}
