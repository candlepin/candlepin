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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.data.builder.Pools.PRIMARY_POOL_SUB_KEY;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstanceSpecTest {
    private ApiClient adminClient;
    private OwnerDTO owner;
    private String ownerKey;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
        ownerKey = owner.getKey();
    }

    @Test
    public void shouldAutoSubscribePhysicalSystemsWithQuantity2PerSocketPair() {
        ProductDTO engProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());

        Set<ConsumerInstalledProductDTO> installedProds = Set.of(new ConsumerInstalledProductDTO()
            .productId(engProd.getId())
            .productName(engProd.getName()));

        int cpuSockets = 8;
        ConsumerDTO physicalUser = createPhysicalUser(adminClient, owner, installedProds, cpuSockets);
        ApiClient physicalClient = ApiClients.ssl(physicalUser);

        String systemId = StringUtil.random("id");
        GuestIdDTO guest = new GuestIdDTO();
        guest.setGuestId(systemId);
        physicalUser.setGuestIds(List.of(guest));
        physicalUser.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        physicalClient.consumers().updateConsumer(physicalUser.getUuid(), physicalUser);

        ConsumerDTO guestUser = createGuestUser(adminClient, owner, installedProds, systemId);
        ApiClient guestClient = ApiClients.ssl(guestUser);

        int socketCount = 2;
        // How much the quant increases by per ent
        int socketMultiplier = 2;
        ProductDTO instanceProduct =
            createInstanceProduct(adminClient, ownerKey, socketCount, socketMultiplier, engProd);

        // The pool needs source subscription and upstream pool id in order for the subpool to be created
        int poolQuantity = 20;
        adminClient.owners().createPool(ownerKey, Pools.random(instanceProduct)
            .quantity(Long.valueOf(poolQuantity))
            .subscriptionId(StringUtil.random("sourceSub-"))
            .upstreamPoolId(StringUtil.random("upstream-"))
            .subscriptionSubKey(PRIMARY_POOL_SUB_KEY));

        List<PoolDTO> pools =  adminClient.pools()
            .listPoolsByOwnerAndProduct(owner.getId(), instanceProduct.getId());
        assertThat(pools)
            .hasSize(2)
            .extracting("type")
            .containsExactlyInAnyOrder("NORMAL", "UNMAPPED_GUEST");

        assertThat(guestClient.pools().listPoolsByConsumer(guestUser.getUuid()))
            .isNotNull()
            .hasSize(1);

        physicalClient.consumers().bindProduct(physicalUser.getUuid(), engProd.getId());

        int expectedQuantity = ((int) Math.ceil((double) cpuSockets / socketCount) * socketMultiplier);
        List<EntitlementDTO> ents = physicalClient.consumers().listEntitlements(physicalUser.getUuid());
        assertThat(ents)
            .isNotNull()
            .singleElement()
            .returns(expectedQuantity, EntitlementDTO::getQuantity);

        // Guest should now see additional sub-pool
        assertThat(guestClient.pools().listPoolsByConsumer(guestUser.getUuid()))
            .isNotNull()
            .hasSize(2);
    }

    @Test
    public void shouldAutoSubscribeGuestSystemsWithQuantity1() {
        ProductDTO engProd = adminClient.ownerProducts()
            .createProductByOwner(ownerKey, Products.randomEng());

        Set<ConsumerInstalledProductDTO> installedProds = Set.of(new ConsumerInstalledProductDTO()
            .productId(engProd.getId())
            .productName(engProd.getName()));

        ConsumerDTO physicalUser = createPhysicalUser(adminClient, owner, installedProds, 8);
        ApiClient physicalClient = ApiClients.ssl(physicalUser);

        String systemId = StringUtil.random("id");
        GuestIdDTO guest = new GuestIdDTO();
        guest.setGuestId(systemId);
        physicalUser.setGuestIds(List.of(guest));
        physicalUser.setReleaseVer(new ReleaseVerDTO().releaseVer(""));
        physicalClient.consumers().updateConsumer(physicalUser.getUuid(), physicalUser);

        ConsumerDTO guestUser = createGuestUser(adminClient, owner, installedProds, systemId);
        ApiClient guestClient = ApiClients.ssl(guestUser);

        ProductDTO instanceProduct = createInstanceProduct(adminClient, ownerKey, 2, 2, engProd);

        // The pool needs source subscription and upstream pool id in order for the subpool to be created
        adminClient.owners().createPool(ownerKey, Pools.random(instanceProduct)
            .quantity(10L)
            .subscriptionId(StringUtil.random("sourceSub-"))
            .upstreamPoolId(StringUtil.random("upstream-"))
            .subscriptionSubKey(PRIMARY_POOL_SUB_KEY));

        List<PoolDTO> pools =  adminClient.pools()
            .listPoolsByOwnerAndProduct(owner.getId(), instanceProduct.getId());
        assertThat(pools)
            .hasSize(2)
            .extracting("type")
            .containsExactlyInAnyOrder("NORMAL", "UNMAPPED_GUEST");

        guestClient.consumers().bindProduct(guestUser.getUuid(), engProd.getId());

        List<EntitlementDTO> ents =  guestClient.consumers().listEntitlements(guestUser.getUuid());
        assertThat(ents)
            .isNotNull()
            .singleElement()
            .returns(1, EntitlementDTO::getQuantity);
    }

    private static ConsumerDTO createPhysicalUser(ApiClient client, OwnerDTO owner,
        Set<ConsumerInstalledProductDTO> installedProds, int cpuSockets) {
        ConsumerDTO physicalUser = Consumers.random(owner, ConsumerTypes.System);
        physicalUser.installedProducts(installedProds);
        physicalUser.facts(Map.of("cpu.cpu_socket(s)", String.valueOf(cpuSockets)));
        String username = StringUtil.random("user-");
        physicalUser.setUsername(username);

        return client.consumers().createConsumer(physicalUser, username, owner.getKey(), null, true);
    }

    private static ConsumerDTO createGuestUser(ApiClient client, OwnerDTO owner,
        Set<ConsumerInstalledProductDTO> installedProds, String systemId) {
        ConsumerDTO guestUser = Consumers.random(owner, ConsumerTypes.System);
        guestUser.installedProducts(installedProds);
        guestUser.facts(Map.of("virt.uuid", systemId, "virt.is_guest", "true"));
        String username = StringUtil.random("user-");
        guestUser.setUsername(username);

        return client.consumers().createConsumer(guestUser, username, owner.getKey(), null, true);
    }

    private static ProductDTO createInstanceProduct(ApiClient client, String ownerKey, int socketCount,
        int socketMultiplier, ProductDTO providedProduct) {
        ProductDTO instanceProduct = Products.random();
        instanceProduct.attributes(List.of(
            ProductAttributes.InstanceMultiplier.withValue(String.valueOf(socketMultiplier)),
            ProductAttributes.VirtualLimit.withValue("1"),
            ProductAttributes.StackingId.withValue(StringUtil.random("stack")),
            ProductAttributes.Sockets.withValue(String.valueOf(socketCount)),
            ProductAttributes.HostLimited.withValue("true"),
            ProductAttributes.MultiEntitlement.withValue("yes")));
        instanceProduct.providedProducts(Set.of(providedProduct));

        return client.ownerProducts().createProductByOwner(ownerKey, instanceProduct);
    }

}
