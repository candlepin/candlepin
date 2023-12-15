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

import org.candlepin.dto.api.client.v1.CertificateSerialDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

@SpecTest
public class UnbindSpecTest {

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
    public void shouldRemoveASingleEntitlement() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO monitoring =  ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool = ownerApi.createPool(owner.getKey(), Pools.random(monitoring));

        EntitlementDTO ent = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent.getId());
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid())).isEmpty();
    }

    @Test
    public void shouldRemoveEntitlementsByPoolIdWithoutTouchingOtherEntitlements() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(prod1));
        ProductDTO prod2 = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(prod2));

        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1).get(0),
            EntitlementDTO.class);

        consumerClient.consumers().unbindByPool(consumer.getUuid(), pool1.getId());
        List<EntitlementDTO> entitlements = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(entitlements).isNotNull()
            .singleElement()
            .returns(ent2.getUpdated(), EntitlementDTO::getUpdated)
            .returns(ent2.getId(), EntitlementDTO::getId);
    }

    @Test
    public void shouldAddUnboundEntitlementsBackToThePool() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO prod = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool = ownerApi.createPool(owner.getKey(), Pools.random(prod));
        EntitlementDTO ent = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);
        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent.getId());
        assertThat(consumerClient.pools().getPool(pool.getId(), consumer.getUuid(), null))
            .returns(10L, PoolDTO::getQuantity);
    }


    @Test
    public void shouldRevokeAnEntitlementsCertificate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO prod = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool = ownerApi.createPool(owner.getKey(), Pools.random(prod));
        EntitlementDTO ent = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1).get(0),
            EntitlementDTO.class);

        List<Long> serials = consumerClient.consumers().getEntitlementCertificateSerials(
            consumer.getUuid()).stream()
            .map(x -> x.getSerial()).collect(Collectors.toList());
        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent.getId());

        assertThat(client.crl().getCurrentCrl()).containsAll(serials);
    }

    @Test
    public void shouldLeaveOtherEntitlementsIntact() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool1 = ownerApi.createPool(owner.getKey(), Pools.random(prod1));
        ProductDTO prod2 = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool2 = ownerApi.createPool(owner.getKey(), Pools.random(prod2));

        EntitlementDTO ent1 = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1).get(0),
            EntitlementDTO.class);
        EntitlementDTO ent2 = ApiClient.MAPPER.convertValue(
            consumerClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1).get(0),
            EntitlementDTO.class);

        CertificateSerialDTO certSerial1 = ent1.getCertificates().iterator().next().getSerial();
        CertificateSerialDTO certSerial2 = ent2.getCertificates().iterator().next().getSerial();

        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent2.getId());

        assertThat(client.crl().getCurrentCrl())
            .contains(certSerial2.getSerial())
            .doesNotContain(certSerial1.getSerial());
    }
}
