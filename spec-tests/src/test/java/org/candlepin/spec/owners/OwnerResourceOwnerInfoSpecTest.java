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
package org.candlepin.spec.owners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerInfo;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;


@SpecTest
public class OwnerResourceOwnerInfoSpecTest {

    @Test
    @SuppressWarnings("indentation")
    public void shouldAllowSystemsUserFilterConsumerCountsInOwnerInfo()
        throws ApiException {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = createOwner(adminClient);
        ApiClient userClient = createUserClient(UserUtil.createUser(adminClient, owner));

        ProductDTO tmpProduct = Products.withAttributes(
            ProductAttributes.Version.withValue("6.4"),
            ProductAttributes.Sockets.withValue("2"),
            ProductAttributes.MultiEntitlement.withValue("true")
        );
        ProductDTO product = adminClient.ownerProducts().createProduct(owner.getKey(), tmpProduct);
        adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        createConsumer(userClient, Consumers.random(owner));
        createConsumer(userClient, Consumers.random(owner).facts(Map.of("virt.is_guest", "true")));

        ApiClient userClientWithPerms = createUserClient(
            UserUtil.createWith(adminClient, Permissions.USERNAME_CONSUMERS.readOnly(owner)));

        PoolDTO pool = adminClient.owners().listOwnerPools(owner.getKey(), null, null,
            product.getId(), null, null, null, null, null,
            null, null, null, null, null, null, null, null).get(0);
        assertNotNull(pool);

        // Create a physical system with valid status
        ConsumerDTO consumer1 = createConsumer(
            userClientWithPerms, Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("cpu.cpu_socket(s)", "4"))
            .installedProducts(Set.of(createInstalledProduct(product.getId(), product.getName()))));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        JsonNode ents = consumerClient1.consumers().bindPool(consumer1.getUuid(), pool.getId(), 1);
        assertNotNull(ents);

        // Create a guest system with a partial status
        ConsumerDTO consumer2 = createConsumer(
            userClientWithPerms, Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "true"))
            .installedProducts(Set.of(createInstalledProduct(product.getId(), product.getName()))));

        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        ents = consumerClient2.consumers().bindPool(consumer2.getUuid(), pool.getId(), 1);
        assertNotNull(ents);

        // Create a guest system with invalid status
        createConsumer(userClientWithPerms, Consumers.random(owner, ConsumerTypes.System)
            .facts(Map.of("virt.is_guest", "true"))
            .installedProducts(Set.of(createInstalledProduct(product.getId(), product.getName()))));

        OwnerInfo info = userClient.owners().getOwnerInfo(owner.getKey());
        assertThat(info.getConsumerCounts())
            .isNotNull()
            .containsEntry("system", 5);

        OwnerInfo systemOwnerInfo = userClientWithPerms.owners().getOwnerInfo(owner.getKey());
        assertThat(systemOwnerInfo.getConsumerCounts())
            .isNotNull()
            .containsEntry("system", 3);
        assertThat(systemOwnerInfo.getConsumerGuestCounts())
            .isNotNull()
            .containsEntry("physical", 1)
            .containsEntry("guest", 2);

        assertThat(systemOwnerInfo.getEntitlementsConsumedByType())
            .isNotNull()
            .containsEntry("system", 2)
            .containsEntry("person", 0);

        assertThat(systemOwnerInfo.getConsumerCountsByComplianceStatus())
            .isNotNull()
            .containsEntry("valid", 1)
            .containsEntry("partial", 1)
            .containsEntry("invalid", 1);
    }

    private static ConsumerDTO createConsumer(ApiClient client, ConsumerDTO consumer) throws ApiException {
        return client.consumers().createConsumer(consumer);
    }

    private static ApiClient createUserClient(UserDTO user) {
        return ApiClients.basic(user.getUsername(), user.getPassword());
    }

    private static OwnerDTO createOwner(ApiClient adminClient) throws ApiException {
        return adminClient.owners().createOwner(Owners.random());
    }

    private ConsumerInstalledProductDTO createInstalledProduct(String productId, String productName) {
        return new ConsumerInstalledProductDTO()
            .productId(productId)
            .productName(productName);
    }
}
