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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.v1.AttributeDTO;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.EntitlementDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PoolDTO;
import org.candlepin.dto.api.v1.ProductDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.resource.ConsumerApi;
import org.candlepin.resource.EntitlementsApi;
import org.candlepin.resource.OwnerProductApi;
import org.candlepin.resource.PoolsApi;
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
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@SpecTest
class EntitlementResourceSpecTest {

    private static ApiClient client;
    ApiClient userClient;
    ConsumerClient consumerClient;
    private static OwnerDTO owner;
    private static OwnerDTO qowner;
    private static UserDTO user;
    private static ConsumerDTO consumer;
    private ProductDTO monitoring;
    private ProductDTO virtual;
    private OwnerClient ownerClient;
    private OwnerProductApi ownerProductApi;
    private ConsumerApi consumerApi;
    private PoolsApi poolApi;
    private EntitlementsApi entitlementsApi;
    private JobsClient jobsClient;


    @BeforeEach
    public void beforeEach() throws ApiException {
        client = ApiClients.admin();
        ownerClient = client.owners();
        jobsClient = client.jobs();
        ownerProductApi = client.ownerProducts();
        consumerApi = client.consumers();
        entitlementsApi = client.entitlements();
        poolApi = client.pools();
        owner = ownerClient.createOwner(Owners.random());
        // separate for quantity adjust tests - here for cleanup
        qowner = ownerClient.createOwner(Owners.random());
        user = UserUtil.createUser(client, owner);
        userClient = ApiClients.trustedUser(user.getUsername());

        monitoring = Products.random()
            .name("monitoring")
            .attributes(List.of(new AttributeDTO().name("variant").value("Satellite Starter Pack")));
        monitoring = ownerProductApi.createProductByOwner(owner.getKey(), monitoring);
        virtual = Products.random()
            .name("virtualization");
        virtual = ownerProductApi.createProductByOwner(owner.getKey(), virtual);

        // entitle owner for the virt and monitoring products
        PoolDTO monitoringPool = Pools.random(monitoring).quantity(6L);
        monitoringPool = ownerClient.createPool(owner.getKey(), monitoringPool);
        PoolDTO virtPool = Pools.random(virtual).quantity(6L);
        virtPool = ownerClient.createPool(owner.getKey(), virtPool);

        consumer = client.consumers().register(Consumers.random(owner));
        consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
    }

    @Test
    @DisplayName("can filter all entitlements by using matches param")
    public void canFilterEntitlementsWithMatches() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(
            consumer.getUuid(), "virtualization", null, null, null, null, null);
        assertThat(ents).hasSize(1);
    }

    @Test
    @DisplayName("should not re-calculate attributes when fetching entitlements")
    public void shouldNotCalculateAttributes() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(
            consumer.getUuid(), "virtualization", null, null, null, null, null);
        assertThat(ents).hasSize(1);
        assertNull(ents.get(0).getPool().getCalculatedAttributes());
    }

    @Test
    @DisplayName("can filter all entitlements by product attribute")
    public void canFilterEntitlementsByProductAttribute() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = entitlementsApi.listAllForConsumer(
            consumer.getUuid(), null, List.of("variant:Satellite Starter Pack"), null, null, null, null);
        assertThat(ents).hasSize(1);
        assertEquals("Satellite Starter Pack",
            ents.get(0).getPool().getProductAttributes().get(0).getValue());
    }

    @Test
    @DisplayName("can filter consumer entitlements by product attribute")
    public void canFilterConsumerEntitlementsByProductAttribute() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = consumerClient.listEntitlements(
            consumer.getUuid(), null, null, null, null, null, null, null);
        assertThat(ents).hasSize(2);

        ents = consumerClient.listEntitlements(consumer.getUuid(),
            null, null, List.of("variant:Satellite Starter Pack"), null, null, null, null);
        assertThat(ents).hasSize(1);

        assertEquals("Satellite Starter Pack",
            ents.get(0).getPool().getProductAttributes().get(0).getValue());
    }

    @Test
    @DisplayName("should allow entitlement certificate regeneration based on product id")
    public void shouldAllowEntCertRegenOnProductId() throws Exception {
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
    @DisplayName("allows filtering certificates by serial number")
    public void allowFilterCertsBySerial() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        consumerClient.bindProduct(consumer.getUuid(), virtual.getId());

        List<EntitlementDTO> ents = consumerClient.listEntitlements(
            consumer.getUuid(), monitoring.getId(), null, null, null, null, null, null);
        assertThat(ents).hasSize(1);

        List<CertificateSerialDTO> certSerials = ents.stream()
            .flatMap(e -> e.getCertificates().stream().map(CertificateDTO :: getSerial))
            .collect(Collectors.toList());
        assertThat(certSerials).hasSize(1);

        assertThat(consumerClient.fetchCertificates(
            consumer.getUuid(), certSerials.get(0).getSerial().toString())).hasSize(1);
    }

    @Test
    @DisplayName("allows listing certificates by serial numbers")
    public void allowsListCertsBySerialNumbers() throws Exception {
        consumerClient.bindProduct(consumer.getUuid(), monitoring.getId());
        assertThat(consumerClient.getEntitlementCertificateSerials(consumer.getUuid()))
            .hasSize(1);
    }

    @Test
    @DisplayName("should allow consumer to change entitlement quantity")
    public void shouldAllowConsumerToChangeQuantity() throws Exception {
        consumer = client.consumers().register(Consumers.random(qowner));
        consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(qowner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(10L);
        pool = ownerClient.createPool(qowner.getKey(), pool);

        JsonNode ent = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 3).get(0);
        String entCertSer = ent.get("certificates").get(0).get("serial").get("id").asText();
        JsonNode ent2 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 2).get(0);
        assertEquals(2, ent2.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(qowner.getKey()).get(0);
        assertEquals(5, thePool.getConsumed());

        // change it higher
        client.entitlements().updateEntitlement(ent.get("id").asText(), new EntitlementDTO().quantity(5));
        EntitlementDTO adjustedEnt = client.entitlements().getEntitlement(ent.get("id").asText());
        String adjustedCertSer1 = adjustedEnt.getCertificates().iterator().next().getId();
        PoolDTO adjustedPool = ownerClient.listOwnerPools(qowner.getKey()).get(0);
        assertEquals(5, adjustedEnt.getQuantity());
        assertEquals(7, adjustedPool.getConsumed());
        assertNotEquals(entCertSer, adjustedCertSer1);

        // change it lower
        client.entitlements().updateEntitlement(ent.get("id").asText(), new EntitlementDTO().quantity(2));
        adjustedEnt = client.entitlements().getEntitlement(ent.get("id").asText());
        String adjustedCertSer2 = adjustedEnt.getCertificates().iterator().next().getId();
        adjustedPool = ownerClient.listOwnerPools(qowner.getKey()).get(0);
        assertEquals(2, adjustedEnt.getQuantity());
        assertEquals(4, adjustedPool.getConsumed());
        assertNotEquals(adjustedCertSer1, adjustedCertSer2);
    }

    @Test
    @DisplayName("should not allow consumer to change entitlement quantity out of bounds")
    public void shouldNotAllowConsumerToChangeToExcessQuantity() throws Exception {
        consumer = client.consumers().register(Consumers.random(qowner));
        consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(qowner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(10L);
        pool = ownerClient.createPool(qowner.getKey(), pool);

        JsonNode ent1 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 3).get(0);
        JsonNode ent2 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 2).get(0);
        assertEquals(3, ent1.get("quantity").asInt());
        assertEquals(2, ent2.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(qowner.getKey()).get(0);
        assertEquals(5, thePool.getConsumed());

        assertForbidden(() ->
            client.entitlements().updateEntitlement(ent1.get("id").asText(),
                new EntitlementDTO().quantity(9)));
        assertBadRequest(() ->
            client.entitlements().updateEntitlement(ent1.get("id").asText(),
                new EntitlementDTO().quantity(-1)));
    }

    @Test
    @DisplayName("should not allow consumer to change entitlement quantity non-multi")
    public void shouldNotAllowConsumerToChangeQuantityNonMulti() throws Exception {
        consumer = client.consumers().register(Consumers.random(qowner));
        consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
        ProductDTO product = Products.random();
        product = ownerProductApi.createProductByOwner(qowner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(10L);
        pool = ownerClient.createPool(qowner.getKey(), pool);

        JsonNode ent1 = consumerClient.bindPool(consumer.getUuid(), pool.getId(), 1).get(0);
        assertEquals(1, ent1.get("quantity").asInt());
        PoolDTO thePool = ownerClient.listOwnerPools(qowner.getKey()).get(0);
        assertEquals(1, thePool.getConsumed());

        assertForbidden(() ->
            client.entitlements().updateEntitlement(ent1.get("id").asText(),
                new EntitlementDTO().quantity(2)));
    }

    @Test
    @DisplayName("should handle concurrent requests to pool and maintain quanities")
    public void shouldAllowConcurrentRequests() throws Exception {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(50L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        List<Thread> threads = new ArrayList<>();
        List<Map.Entry<String, Integer>> data = List.of(
            Map.entry("system", 5),
            Map.entry("candlepin", 7),
            Map.entry("system", 6),
            Map.entry("candlepin", 11));
        for (Map.Entry<String, Integer> entry : data) {
            final String poolId = pool.getId();
            Thread t = new Thread(() -> {
                try {
                    registerAndConsume(poolId, entry.getKey(), entry.getValue());
                }
                catch (Exception ae) {
                    throw new RuntimeException(ae);
                }
            });
            threads.add(t);
        }
        // Run all the threads
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(29, thePool.getConsumed());
        assertEquals(18, thePool.getExported());
    }

    @Test
    @DisplayName("should not allow over consumption in pool")
    public void shouldNotAllowOverConsumption() throws Exception {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(3L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        List<Thread> threads = new ArrayList<>();

        List<Map.Entry<String, Integer>> data = List.of(
            Map.entry("candlepin", 1),
            Map.entry("system", 1),
            Map.entry("candlepin", 1),
            Map.entry("candlepin", 1),
            Map.entry("candlepin", 1));
        for (Map.Entry<String, Integer> entry : data) {
            final String poolId = pool.getId();
            Thread t = new Thread(() -> {
                try {
                    registerAndConsume(poolId, entry.getKey(), entry.getValue());
                }
                catch (Exception ae) {
                    throw new RuntimeException(ae);
                }
            });
            threads.add(t);
        }
        // Run all the threads
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(3, thePool.getConsumed());
    }

    public void registerAndConsume(String poolId, String consumerType, int quantity)
        throws JsonProcessingException, ApiException {
        ConsumerDTO consumer = client.consumers().register(
            Consumers.random(owner).type(new ConsumerTypeDTO().label(consumerType)));
        ConsumerClient consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
        try {
            consumerClient.bindPool(consumer.getUuid(), poolId, quantity);
        }
        catch (ApiException e) {
            // tests will run that try to over consume, this is expected
            // ensure that it's not something else
            assertEquals(403, e.getCode());
        }
    }

    @Test
    @DisplayName("should end at zero quantity consumed when all consumers are unregistered")
    public void shouldEndAtZeroConsumption() throws Exception {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(3L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        List<Thread> threads = new ArrayList<>();
        List<Map.Entry<String, Integer>> data = List.of(
            Map.entry("candlepin", 1),
            Map.entry("system", 1),
            Map.entry("candlepin", 1),
            Map.entry("system", 1),
            Map.entry("candlepin", 1));
        for (Map.Entry<String, Integer> entry : data) {
            final String poolId = pool.getId();
            Thread t = new Thread(() -> {
                try {
                    registerConsumeUnregister(poolId, entry.getKey(), entry.getValue());
                }
                catch (Exception ae) {
                    throw new RuntimeException(ae);
                }
            });
            threads.add(t);
        }
        // Run all the threads
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(0, thePool.getConsumed());
        assertEquals(0, thePool.getExported());
    }

    public void registerConsumeUnregister(String poolId, String consumerType, int quantity)
        throws JsonProcessingException, ApiException {
        ConsumerDTO consumer = client.consumers().register(
            Consumers.random(owner).type(new ConsumerTypeDTO().label(consumerType)));
        ConsumerClient consumerClient = ApiClients.trustedConsumer(consumer.getUuid()).consumers();
        try {
            consumerClient.bindPool(consumer.getUuid(), poolId, quantity);
        }
        catch (ApiException e) {
            // tests will run that try to over consume, this is expected
            // ensure that it's not something else
            assertEquals(403, e.getCode());
        }
        client.consumers().deleteConsumer(consumer.getUuid());
    }
}
