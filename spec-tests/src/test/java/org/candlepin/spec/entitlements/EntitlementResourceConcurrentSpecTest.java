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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.resource.client.v1.PoolsApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class EntitlementResourceConcurrentSpecTest {

    private static ApiClient client;
    private static OwnerProductApi ownerProductApi;
    private static PoolsApi poolApi;
    private static OwnerClient ownerClient;
    private OwnerDTO owner;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        ownerProductApi = client.ownerProducts();
        poolApi = client.pools();
    }

    @BeforeEach
    void setUp() throws ApiException {
        owner = ownerClient.createOwner(Owners.random());
    }

    @Test
    public void shouldAllowConcurrentRequests() {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(50L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        String poolId = pool.getId();
        List<Runnable> threads = List.of(
            () -> registerAndConsume(poolId, "system", 5),
            () -> registerAndConsume(poolId, "candlepin", 7),
            () -> registerAndConsume(poolId, "system", 6),
            () -> registerAndConsume(poolId, "candlepin", 11));

        runAllThreads(threads);

        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(29, thePool.getConsumed());
        assertEquals(18, thePool.getExported());
    }

    @Test
    public void shouldNotAllowOverConsumption() {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(3L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        String poolId = pool.getId();
        List<Runnable> threads = List.of(
            () -> registerAndConsume(poolId, "candlepin", 1),
            () -> registerAndConsume(poolId, "system", 1),
            () -> registerAndConsume(poolId, "candlepin", 1),
            () -> registerAndConsume(poolId, "candlepin", 1),
            () -> registerAndConsume(poolId, "candlepin", 1));

        runAllThreads(threads);

        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(3, thePool.getConsumed());
    }

    @Test
    public void shouldEndAtZeroConsumption() {
        ProductDTO product = Products.random();
        product.setAttributes(List.of(new AttributeDTO().name("multi-entitlement").value("yes")));
        product = ownerProductApi.createProductByOwner(owner.getKey(), product);
        PoolDTO pool = Pools.random().productId(product.getId()).quantity(3L);
        pool = ownerClient.createPool(owner.getKey(), pool);

        String poolId = pool.getId();
        List<Runnable> threads = List.of(
            () -> registerConsumeUnregister(poolId, "candlepin", 1),
            () -> registerConsumeUnregister(poolId, "system", 1),
            () -> registerConsumeUnregister(poolId, "candlepin", 1),
            () -> registerConsumeUnregister(poolId, "system", 1),
            () -> registerConsumeUnregister(poolId, "candlepin", 1));

        runAllThreads(threads);

        PoolDTO thePool = poolApi.getPool(pool.getId(), null, null);
        assertEquals(0, thePool.getConsumed());
        assertEquals(0, thePool.getExported());
    }

    private static void runAllThreads(Collection<Runnable> threads) {
        Set<Thread> toRun = threads.stream()
            .map(Thread::new)
            .collect(Collectors.toSet());
        for (Thread t : toRun) {
            t.start();
        }
        for (Thread t : toRun) {
            try {
                t.join();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void registerAndConsume(String poolId, String consumerType, Integer quantity) {
        try {
            doRegisterAndConsume(poolId, consumerType, quantity);
        }
        catch (Exception ae) {
            throw new RuntimeException(ae);
        }
    }

    public String doRegisterAndConsume(String poolId, String consumerType, int quantity)
        throws ApiException {
        ConsumerDTO consumer = client.consumers().createConsumer(
            Consumers.random(owner).type(new ConsumerTypeDTO().label(consumerType)));
        ConsumerClient consumerClient = ApiClients.ssl(consumer).consumers();
        try {
            consumerClient.bindPool(consumer.getUuid(), poolId, quantity);
        }
        catch (ApiException e) {
            // tests will run that try to over consume, this is expected
            // ensure that it's not something else
            assertEquals(403, e.getCode());
        }
        return consumer.getUuid();
    }

    public void registerConsumeUnregister(String poolId, String consumerType, int quantity) {
        try {
            String consumerUuid = doRegisterAndConsume(poolId, consumerType, quantity);
            client.consumers().deleteConsumer(consumerUuid);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
