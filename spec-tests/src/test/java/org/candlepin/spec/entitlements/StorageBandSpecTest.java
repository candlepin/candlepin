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
package org.candlepin.spec.entitlements;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class StorageBandSpecTest {

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
    public void shouldPoolHaveTheCorrectQuantityBasedOffOfTheProductMultiplier() {
        // sub.quantity * multiplier
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO cephProduct = createCephProductAndPool(owner);

        assertThat(userClient.pools().listPoolsByOwnerAndProduct(owner.getId(), cephProduct.getId()))
            .singleElement()
            .returns(512L, PoolDTO::getQuantity);
    }

    @Test
    public void shouldStorageBandEntitlementsFromSameSubscriptionCanBeStackedToCoverEntireSystem() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO cephProduct = createCephProductAndPool(owner);

        ConsumerDTO system = userClient.consumers().createConsumer(
            Consumers.random(owner)
            .type(ConsumerTypes.System.value())
            .facts(Map.of("band.storage.usage", "256"))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(cephProduct.getId()).productName(cephProduct.getName()))));
        ApiClient systemClient = ApiClients.ssl(system);

        PoolDTO cephPool = userClient.pools().listPoolsByOwnerAndProduct(owner.getId(),
            cephProduct.getId()).get(0);

        // Partial stack
        systemClient.consumers().bindPool(system.getUuid(), cephPool.getId(), 128);

        // Complete the stack
        systemClient.consumers().bindPool(system.getUuid(), cephPool.getId(), 128);

        List<EntitlementDTO> entitilements = userClient.consumers().listEntitlements(system.getUuid());
        assertThat(entitilements)
            .hasSize(2)
            .filteredOn(x -> x.getQuantity() == 128)
            .hasSize(2);
    }

    private ProductDTO createCephProductAndPool(OwnerDTO owner) {
        ProductDTO cephProduct = ownerProductApi.createProduct(owner.getKey(),
            Products.random()
            .multiplier(256L)
            .addAttributesItem(new AttributeDTO().name("version").value("6.4"))
            // storage_band will always be defined as 1, or not set.
            .addAttributesItem(new AttributeDTO().name("storage_band").value("1"))
            .addAttributesItem(new AttributeDTO().name("warning_period").value("15"))
            .addAttributesItem(new AttributeDTO().name("stacking_id").value("ceph-node"))
            .addAttributesItem(new AttributeDTO().name("multi-entitlement").value("yes"))
            .addAttributesItem(new AttributeDTO().name("support_level").value("standard"))
            .addAttributesItem(new AttributeDTO().name("support_type").value("excellent")));
        ownerApi.createPool(owner.getKey(), Pools.random(cephProduct).quantity(2L));
        return cephProduct;
    }
}
