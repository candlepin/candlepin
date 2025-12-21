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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;


@SpecTest
public class OwnerResourceEntitlementListSpecTest {

    private static ApiClient adminClient;
    private static ConsumerDTO consumer;

    @BeforeAll
    public static void setUp() throws ApiException {
        adminClient = ApiClients.admin();

        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        ProductDTO monitoringProduct =
            createProduct(adminClient, owner, ProductAttributes.Variant.withValue("Satellite Starter Pack"));
        ProductDTO virtualProduct = adminClient.ownerProducts().createProduct(
            owner.getKey(), Products.random());

        // entitle owner for the virtual and monitoring products
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(monitoringProduct));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(virtualProduct));

        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);
    }

    @Test
    public void shouldFetchAllEntitlementsOfAnOwner() throws ApiException {
        List<EntitlementDTO> ents = adminClient.consumers()
            .listEntitlements(consumer.getUuid(), null, false, null, null, null, null, null);
        assertEquals(2, ents.size());
    }

    @Test
    public void shouldAllowFilterConsumerEntitlementsByProductAttribute() throws ApiException {
        List<EntitlementDTO> ents = adminClient.consumers().listEntitlements(
            consumer.getUuid(), null, false, List.of("variant:Satellite Starter Pack"),
            null, null, null, null);
        assertThat(ents)
            .isNotNull()
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .isNotNull()
            .extracting(PoolDTO::getProductAttributes, as(collection(AttributeDTO.class)))
            .isNotNull()
            .singleElement()
            .returns("variant", AttributeDTO::getName)
            .returns("Satellite Starter Pack", AttributeDTO::getValue);
    }

    private static ProductDTO createProduct(ApiClient client, OwnerDTO owner, AttributeDTO... attributes)
        throws ApiException {
        return client.ownerProducts().createProduct(
            owner.getKey(), Products.withAttributes(attributes));
    }
}
