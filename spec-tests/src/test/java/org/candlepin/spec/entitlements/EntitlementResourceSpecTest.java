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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.EntitlementsApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.JobsClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.util.List;


@SpecTest
class EntitlementResourceSpecTest {

    private static ApiClient client;
    private static OwnerProductApi ownerProductApi;
    private static EntitlementsApi entitlementsApi;
    private static JobsClient jobsClient;
    private static OwnerClient ownerClient;
    private OwnerDTO owner;
    private ConsumerDTO consumer;
    private ConsumerClient consumerClient;
    private ProductDTO monitoring;
    private ProductDTO virtual;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        jobsClient = client.jobs();
        ownerProductApi = client.ownerProducts();
        entitlementsApi = client.entitlements();
    }

    @BeforeEach
    void setUp() throws ApiException {
        owner = ownerClient.createOwner(Owners.random());

        monitoring = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("monitoring"))
            .attributes(List.of(new AttributeDTO().name("variant").value("Satellite Starter Pack"))));
        virtual = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .name(StringUtil.random("virtualization")));

        consumer = client.consumers().createConsumer(Consumers.random(owner));
        consumerClient = ApiClients.ssl(consumer).consumers();
    }

    @Test
    public void shouldNotCalculateAttributes() {
        ownerClient.createPool(owner.getKey(), Pools.random(monitoring));
        ownerClient.createPool(owner.getKey(), Pools.random(virtual));
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(
            consumer.getUuid(), "virtualization", null, null, null, null, null);

        assertThat(ents).hasSize(1)
            .map(EntitlementDTO::getPool)
            .map(PoolDTO::getCalculatedAttributes)
            .containsOnlyNulls();
    }

    @Test
    public void shouldAllowEntCertRegenOnProductId() {
        ownerClient.createPool(owner.getKey(), Pools.random(monitoring));
        ownerClient.createPool(owner.getKey(), Pools.random(virtual));
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        CertificateDTO oldCert = consumerClient.fetchCertificates(consumer.getUuid()).get(0);
        AsyncJobStatusDTO job = entitlementsApi.regenerateEntitlementCertificatesForProduct(
            monitoring.getId(), false);
        job = jobsClient.waitForJob(job);
        assertEquals("FINISHED", job.getState());

        CertificateDTO newCert = consumerClient.fetchCertificates(consumer.getUuid()).get(0);
        assertNotEquals(oldCert.getSerial(), newCert.getSerial());
    }

    @Test
    public void shouldAllowsListCertsBySerialNumbers() {
        ownerClient.createPool(owner.getKey(), Pools.random(monitoring));
        ownerClient.createPool(owner.getKey(), Pools.random(virtual));
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());

        assertThat(consumerClient.getEntitlementCertificateSerials(consumer.getUuid()))
            .hasSize(1);
    }

    @Test
    public void shouldAllowConsumerToChangeQuantity() {
        ProductDTO product = ownerProductApi.createProduct(owner.getKey(), Products.random()
            .attributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes"))));
        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));

        JsonNode ent = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 3).get(0);
        String entCertSer = ent.get("certificates").get(0).get("serial").get("id").asText();
        JsonNode ent2 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 2).get(0);
        assertEquals(2, ent2.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(owner.getKey()).get(0);
        assertEquals(5, thePool.getConsumed());

        // change it higher
        client.entitlements().updateEntitlement(ent.get("id").asText(), new EntitlementDTO().quantity(5));
        EntitlementDTO adjustedEnt = client.entitlements().getEntitlement(ent.get("id").asText());
        String adjustedCertSer1 = adjustedEnt.getCertificates().iterator().next().getId();
        PoolDTO adjustedPool = ownerClient.listOwnerPools(owner.getKey()).get(0);
        assertEquals(5, adjustedEnt.getQuantity());
        assertEquals(7, adjustedPool.getConsumed());
        assertNotEquals(entCertSer, adjustedCertSer1);

        // change it lower
        client.entitlements().updateEntitlement(ent.get("id").asText(), new EntitlementDTO().quantity(2));
        adjustedEnt = client.entitlements().getEntitlement(ent.get("id").asText());
        String adjustedCertSer2 = adjustedEnt.getCertificates().iterator().next().getId();
        adjustedPool = ownerClient.listOwnerPools(owner.getKey()).get(0);
        assertEquals(2, adjustedEnt.getQuantity());
        assertEquals(4, adjustedPool.getConsumed());
        assertNotEquals(adjustedCertSer1, adjustedCertSer2);
    }

    @Test
    public void shouldNotAllowConsumerToChangeToExcessQuantity() {
        ProductDTO product = Products.random()
            .attributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProduct(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(10L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        JsonNode ent1 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 3).get(0);
        JsonNode ent2 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 2).get(0);
        assertEquals(3, ent1.get("quantity").asInt());
        assertEquals(2, ent2.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(owner.getKey()).get(0);
        assertEquals(5, thePool.getConsumed());

        assertForbidden(() ->
            client.entitlements().updateEntitlement(ent1.get("id").asText(),
                new EntitlementDTO().quantity(9)));
        assertBadRequest(() ->
            client.entitlements().updateEntitlement(ent1.get("id").asText(),
                new EntitlementDTO().quantity(-1)));
    }

    @Test
    public void shouldNotAllowConsumerToChangeQuantityNonMulti() {
        ProductDTO product = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO pool = ownerClient.createPool(owner.getKey(), Pools.random(product));

        JsonNode ent1 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 1).get(0);
        assertEquals(1, ent1.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(owner.getKey()).get(0);
        assertEquals(1, thePool.getConsumed());

        assertForbidden(() -> client.entitlements()
            .updateEntitlement(ent1.get("id").asText(), new EntitlementDTO().quantity(2)));
    }

}
