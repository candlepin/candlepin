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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ConsumerActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.resource.client.v1.ActivationKeyApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.NotWithMySql;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.ContentOverrides;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;



@SpecTest
public class ConsumerResourceActivationKeySpecTest {

    static ApiClient client;
    static OwnerClient ownerClient;
    static ConsumerClient consumerClient;
    static OwnerProductApi ownerProductApi;
    static ActivationKeyApi activationKeyApi;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        ownerProductApi = client.ownerProducts();
        consumerClient = client.consumers();
        activationKeyApi = client.activationKeys();
    }

    @Test
    public void shouldAllowConsumerToRegisterWithActivationKeys() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool1 = ownerClient.listOwnerPools(owner.getKey()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), 3L);
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        ConsumerDTO consumer = consumerClient.createConsumer(Consumers.random(owner), null, owner.getKey(),
            key1.getName() + "," + key2.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(3L, PoolDTO::getConsumed);
    }

    @Test
    @NotWithMySql
    public void shouldAllowConcurrentReregistrations() throws Exception {
        int keyCount = 3;
        int poolCount = 21;
        int threadCount = 10;

        OwnerDTO owner = this.ownerClient.createOwner(Owners.random());

        // Create some activation keys and pools
        ActivationKeyDTO[] keys = new ActivationKeyDTO[keyCount];
        PoolDTO[] pools = new PoolDTO[poolCount];
        for (int i = 0; i < keyCount; ++i) {
            keys[i] = this.ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        }

        for (int i = 0; i < poolCount; ++i) {
            ProductDTO product = this.ownerProductApi.createProduct(owner.getKey(), Products.random());
            pools[i] = this.ownerClient.createPool(owner.getKey(), Pools.random(product));
        }

        // Attach the pools to the keys evenly, but random-ish-ly
        Random rnd = new Random();

        // We can use a set seed if this test becomes inconsistent in the future, but so far the order just
        // needs to be non-sequential and every iteration attempted has triggered the deadlock as expected
        // long seed = -1753978213090389925L;
        long seed = rnd.nextLong();
        rnd.setSeed(seed);

        for (int i = pools.length - 1; i > 0; --i) {
            int idx = rnd.nextInt(i + 1);

            PoolDTO tmp = pools[i];
            pools[i] = pools[idx];
            pools[idx] = tmp;

            this.activationKeyApi
                .addPoolToKey(keys[i % keyCount].getId(), pools[i].getId(), null);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Runnable task = () -> {
            ConsumerDTO consumer = Consumers.random(owner)
                .uuid(UUID.randomUUID().toString());

            // Do a register->unregister loop for each rotation of our activation keys
            for (int i = 0; i < keyCount; ++i) {
                // Build our key string...
                String[] keyNames = new String[keyCount];
                for (int k = 0; k < keyCount; ++k) {
                    keyNames[k] = keys[(i + k) % keyCount].getName();
                }

                this.consumerClient
                    .createConsumer(consumer, null, owner.getKey(), String.join(",", keyNames), true);

                // Do we care about these?
                List<EntitlementDTO> ents = this.consumerClient.listEntitlements(consumer.getUuid());
                this.consumerClient.deleteConsumer(consumer.getUuid());
            }
        };

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; ++i) {
            futures.add(executor.submit(task));
        }

        // Wait for our tasks to finish...
        try {
            executor.shutdown();

            // Clunky way of propagating any exceptions from our registration tasks
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }

            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow();
                fail("Failed to complete tasks in the allotted time...");
            }
        }
        catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();

            fail("Test interrupted while waiting for tasks to complete...");
        }
    }

    @Test
    public void shouldAllowConsumerToRegisterWithActivationKeyAuth() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool1 = ownerClient.listOwnerPools(owner.getKey()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), 3L);
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        ApiClient noAuth = ApiClients.noAuth();
        ConsumerDTO consumer = noAuth.consumers().createConsumer(Consumers.random(owner), null,
            owner.getKey(), key1.getName() + "," + key2.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(3L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAllowPhysicalConsumerToRegisterWithActivationKeys() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool1 = ownerClient.listOwnerPools(owner.getKey()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), 3L);
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("dmi.system.uuid", "test-uuid", "virt.is_guest", "false"));
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(),
            key1.getName() + "," + key2.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(3L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAllowPhysicalConsumerToRegisterWithAnActivationKeyWithAutoAttach() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        // create extra product/pool to show selectivity
        ProductDTO prod1 = Products.randomEng();
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ProductDTO prod2 = Products.randomEng();
        prod2 = ownerProductApi.createProduct(owner.getKey(), prod2);
        PoolDTO pool1 = ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool2 = ownerClient.createPool(owner.getKey(), Pools.random(prod2));

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.updateActivationKey(key1.getId(), new ActivationKeyDTO().autoAttach(true));
        key1 = activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), null);
        key1 = activationKeyApi.addProductIdToKey(key1.getId(), prod1.getId());

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(1L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAutoAttachAConsumerWithAutoHealTrueWithAnActivationKeyWithAnAutoAttach() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        // create extra product/pool to show selectivity
        ProductDTO prod1 = Products.randomEng();
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ProductDTO prod2 = Products.randomEng();
        prod2 = ownerProductApi.createProduct(owner.getKey(), prod2);
        PoolDTO pool1 = ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool2 = ownerClient.createPool(owner.getKey(), Pools.random(prod2));

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.updateActivationKey(key1.getId(), new ActivationKeyDTO().autoAttach(true));
        key1 = activationKeyApi.addProductIdToKey(key1.getId(), prod1.getId());

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productName(prod1.getName())
            .productId(prod1.getId());
        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of(installed))
            .autoheal(true);
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);

        assertThat(consumer)
            .doesNotReturn(null, ConsumerDTO::getUuid)
            .returns("valid", ConsumerDTO::getEntitlementStatus);
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(1L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldNotAutoAttachAConsumerWithAutoHealFalseWithAnActivationKeyWithAnAutoAttach() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        // create extra product/pool to show selectivity
        ProductDTO prod1 = Products.randomEng();
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ProductDTO prod2 = Products.randomEng();
        prod2 = ownerProductApi.createProduct(owner.getKey(), prod2);
        PoolDTO pool1 = ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool2 = ownerClient.createPool(owner.getKey(), Pools.random(prod2));

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.updateActivationKey(key1.getId(), new ActivationKeyDTO().autoAttach(true));
        key1 = activationKeyApi.addProductIdToKey(key1.getId(), prod1.getId());

        ConsumerInstalledProductDTO installed = new ConsumerInstalledProductDTO()
            .productName(prod1.getName())
            .productId(prod1.getId());
        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of(installed))
            .autoheal(false);
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);

        assertThat(consumer)
            .doesNotReturn(null, ConsumerDTO::getUuid)
            .returns("invalid", ConsumerDTO::getEntitlementStatus);
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(0L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithActivationKeysWithContentOverrides() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        ContentOverrideDTO override = ContentOverrides.random();
        activationKeyApi.addActivationKeyContentOverrides(key1.getId(), List.of(override));

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumerClient.listConsumerContentOverrides(consumer.getUuid()))
            .singleElement()
            .returns(override.getName(), ContentOverrideDTO::getName)
            .returns(override.getValue(), ContentOverrideDTO::getValue)
            .returns(override.getContentLabel(), ContentOverrideDTO::getContentLabel);
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithActivationKeysWithRelease() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO().releaseVer("Registration Release");
        key1 = activationKeyApi.updateActivationKey(key1.getId(),
            new ActivationKeyDTO().releaseVer(releaseVer));

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumer)
            .returns("Registration Release", x -> x.getReleaseVer().getReleaseVer());
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithActivationKeysWithServiceLevel() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("VIP"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        PoolDTO pool1 = ownerClient.createPool(owner.getKey(), Pools.random(prod1));

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner).serviceLevel("VIP"));
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner).releaseVer(null));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO().releaseVer("Registration Release");
        key1 = activationKeyApi.updateActivationKey(key1.getId(),
            new ActivationKeyDTO().releaseVer(releaseVer));

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(),
            key1.getName() + "," + key2.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumer)
            .returns("Registration Release", x -> x.getReleaseVer().getReleaseVer())
            .returns("VIP", ConsumerDTO::getServiceLevel);
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithActivationKeysWithSyspurposeAtts() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));

        ActivationKeyDTO key1 = ActivationKeys.random(owner)
            .usage(StringUtil.random("usage"))
            .role(StringUtil.random("role"))
            .addAddOnsItem(("addon1"))
            .addAddOnsItem(("addon2"));
        key1 = ownerClient.createActivationKey(owner.getKey(), key1);

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumer)
            .returns(key1.getUsage(), ConsumerDTO::getUsage)
            .returns(key1.getRole(), ConsumerDTO::getRole)
            .extracting(ConsumerDTO::getAddOns)
            .returns(2, Set::size)
            .returns(true, x -> x.containsAll(List.of("addon1", "addon2")));
    }

    @Test
    public void shouldOverrideConsumerSyspurposeAttsWithAttsOnActivationKeys() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));

        ActivationKeyDTO key1 = ActivationKeys.random(owner)
            .usage("ak-usage")
            .role("ak-role")
            .addAddOnsItem(("ak-addon1"))
            .addAddOnsItem(("ak-addon2"));
        key1 = ownerClient.createActivationKey(owner.getKey(), key1);

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of())
            .serviceLevel("client-sla")
            .role("client-role")
            .usage("client-usage");
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumer)
            .returns("ak-usage", ConsumerDTO::getUsage)
            .returns("ak-role", ConsumerDTO::getRole)
            .returns("client-sla", ConsumerDTO::getServiceLevel)
            .extracting(ConsumerDTO::getAddOns)
            .returns(2, Set::size)
            .returns(true, x -> x.containsAll(List.of("ak-addon1", "ak-addon2")));
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithSyspurposeAttsOnActivationKeyAndConsumer() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.randomEng());
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));

        ActivationKeyDTO key1 = ActivationKeys.random(owner)
            .usage("ak-usage")
            .role("ak-role")
            .addAddOnsItem(("ak-addon1"))
            .addAddOnsItem(("ak-addon2"));
        key1 = ownerClient.createActivationKey(owner.getKey(), key1);

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of())
            .serviceLevel("consumer-service-level")
            .role("consumer-role")
            .usage("consumer-usage")
            .addAddOnsItem("consumer_addon-1")
            .addAddOnsItem("consumer_addon-2")
            .addAddOnsItem("consumer_addon-3");
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(), key1.getName(), true);
        assertThat(consumer.getUuid()).isNotNull();

        assertThat(consumer)
            .returns("consumer-service-level", ConsumerDTO::getServiceLevel)
            .returns("ak-role", ConsumerDTO::getRole)
            .returns("ak-usage", ConsumerDTO::getUsage)
            .extracting(ConsumerDTO::getAddOns)
            .returns(2, Set::size)
            .returns(true, x -> x.containsAll(List.of("ak-addon1", "ak-addon2")));
    }

    @Test
    public void shouldAllowConsumerToRegisterWithMultipleActivationKeysAndRetainOrder() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        String contentName = StringUtil.random("name");
        String contentLabel = StringUtil.random("label");
        ContentOverrideDTO override1 = ContentOverrides.random()
            .name(contentName)
            .value("value1")
            .contentLabel(contentLabel);
        ContentOverrideDTO override2 = ContentOverrides.random()
            .name(contentName)
            .value("value2")
            .contentLabel(contentLabel);
        ActivationKeyDTO key1 = ActivationKeys.random(owner).contentOverrides(Set.of(override1));
        key1 = ownerClient.createActivationKey(owner.getKey(), key1);
        ActivationKeyDTO key2 = ActivationKeys.random(owner).contentOverrides(Set.of(override2));
        key2 = ownerClient.createActivationKey(owner.getKey(), key2);

        ConsumerDTO consumer1 = consumerClient.createConsumer(Consumers.random(owner), null,
            owner.getKey(), key1.getName() + "," + key2.getName(), true);
        assertThat(consumerClient.listConsumerContentOverrides(consumer1.getUuid()))
            .singleElement()
            .returns("value2", ContentOverrideDTO::getValue);

        ConsumerDTO consumer2 = consumerClient.createConsumer(Consumers.random(owner), null,
            owner.getKey(), key2.getName() + "," + key1.getName(), true);
        assertThat(consumerClient.listConsumerContentOverrides(consumer2.getUuid()))
            .singleElement()
            .returns("value1", ContentOverrideDTO::getValue);
    }

    @Test
    public void shouldAllowConsumerToRegisterWithActivationKeysWithNullQuantity() throws Exception {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(StringUtil.random("stack")))
            .addAttributesItem(ProductAttributes.Sockets.withValue("1"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool1 = ownerClient.listOwnerPools(owner.getKey()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        // null quantity
        activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), null);
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "4"));
        consumer = consumerClient.createConsumer(consumer, null, owner.getKey(),
            key1.getName() + "," + key2.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.pools().getPool(pool1.getId(), consumer.getUuid(), null))
            .returns(4L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAllowAConsumerToRegisterWithAnActivationKeyWithAnAutoAttachAcrossPools() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        // create extra product/pool to show selectivity
        ProductDTO prod1 = ownerProductApi.createProduct(owner.getKey(), Products.random());
        ownerClient.createPool(owner.getKey(), Pools.random(prod1).quantity(1L));
        ownerClient.createPool(owner.getKey(), Pools.random(prod1).quantity(1L));
        ownerClient.createPool(owner.getKey(), Pools.random(prod1).quantity(2L));
        List<PoolDTO> pools = ownerClient.listOwnerPools(owner.getKey());

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.updateActivationKey(key1.getId(), new ActivationKeyDTO().autoAttach(true));
        final String keyId = key1.getId();
        pools.stream().forEach(pool -> activationKeyApi.addPoolToKey(keyId, pool.getId(), 1L));
        key1 = activationKeyApi.addProductIdToKey(keyId, prod1.getId());

        final String keyName = key1.getName();
        IntStream.range(0, 4).forEach(entry -> {
            ConsumerDTO consumer = Consumers.random(owner).installedProducts(Set.of());
            consumer = consumerClient.createConsumer(consumer, null,
                owner.getKey(), keyName, true);
            assertThat(consumer.getUuid()).isNotNull();
            assertThat(client.consumers().listEntitlements(consumer.getUuid()))
                .singleElement();
        });

        pools.stream().forEach(pool -> {
            PoolDTO getPool = client.pools().getPool(pool.getId(), null, null);
            assertEquals(getPool.getQuantity(), getPool.getConsumed());
        });

        // None left
        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null,
            owner.getKey(), keyName, true);
        assertThat(consumer.getUuid()).isNotNull();
        assertThat(client.consumers().listEntitlements(consumer.getUuid())).isEmpty();
    }

    @Test
    public void shouldBindToSubsWhenServiceLevelVaries() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO rhelProduct = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("VIP"));
        rhelProduct = ownerProductApi.createProduct(owner.getKey(), rhelProduct);
        ProductDTO product = ownerProductApi.createProduct(owner.getKey(), Products.random());
        PoolDTO rhelPool = ownerClient.createPool(owner.getKey(), Pools.random(rhelProduct).quantity(37L));
        PoolDTO productPool = ownerClient.createPool(owner.getKey(), Pools.random(product).quantity(33L));

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner).serviceLevel("VIP"));

        PoolDTO retrievedRhelPool = ownerClient
            .listOwnerPoolsByProduct(owner.getKey(), rhelProduct.getId()).get(0);
        assertThat(retrievedRhelPool)
            .isNotNull()
            .returns(rhelPool.getId(), PoolDTO::getId);

        PoolDTO retrievedProductPool = ownerClient
            .listOwnerPoolsByProduct(owner.getKey(), product.getId()).get(0);
        assertThat(retrievedProductPool)
            .isNotNull()
            .returns(productPool.getId(), PoolDTO::getId);

        activationKeyApi.addPoolToKey(key1.getId(), retrievedRhelPool.getId(), 1L);
        activationKeyApi.addPoolToKey(key1.getId(), retrievedProductPool.getId(), 1L);

        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null,
            owner.getKey(), key1.getName(), true);

        assertThat(ownerClient.listOwnerPools(owner.getKey())).hasSize(2);
        assertThat(consumerClient.listEntitlements(consumer.getUuid())).hasSize(2);
    }

    @Test
    public void shouldNotAllowACConsumerToRegisterWithNoAvailabilityPool() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1).quantity(0L));
        PoolDTO pool1 = ownerClient.listOwnerPoolsByProduct(owner.getKey(), prod1.getId()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner));
        activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), 1L);

        assertBadRequest(() -> consumerClient.createConsumer(Consumers.random(owner), null,
            owner.getKey(), key1.getName(), true));
    }

    @Test
    public void shouldStoreActivationKeysNameAndIDIfConsumerIsRegisteredViaActivationKeys() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner));
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(),
            ActivationKeys.random(owner));

        ConsumerDTO consumer = Consumers.random(owner).installedProducts(Set.of());
        consumer = consumerClient.createConsumer(consumer, null,
            owner.getKey(), key1.getName() + "," + key2.getName() + ",unknown_key", true);

        ConsumerDTO consumerRegWithoutAK = consumerClient.createConsumer(Consumers.random(owner));
        assertThat(consumerRegWithoutAK)
            .doesNotReturn(null, ConsumerDTO::getUuid)
            .returns(Set.of(), ConsumerDTO::getActivationKeys);

        ConsumerActivationKeyDTO caKey1 = new ConsumerActivationKeyDTO()
            .activationKeyId(key1.getId())
            .activationKeyName(key1.getName());
        ConsumerActivationKeyDTO caKey2 = new ConsumerActivationKeyDTO()
            .activationKeyId(key2.getId())
            .activationKeyName(key2.getName());
        assertThat(consumer)
            .doesNotReturn(null, ConsumerDTO::getUuid)
            .extracting(ConsumerDTO::getActivationKeys)
            .returns(2, Set::size)
            .returns(true, x -> x.containsAll(Set.of(caKey1, caKey2)));
    }

    @Test
    public void shouldAllowConsumerToRegisterWithActivationKeyAuthWithoutConsumedSubscriptionInSCA() {
        OwnerDTO owner = ownerClient.createOwner(Owners.randomSca());
        ProductDTO prod1 = Products.random()
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        prod1 = ownerProductApi.createProduct(owner.getKey(), prod1);
        ownerClient.createPool(owner.getKey(), Pools.random(prod1));
        PoolDTO pool1 = ownerClient.listOwnerPools(owner.getKey()).get(0);

        ActivationKeyDTO key1 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        key1 = activationKeyApi.addPoolToKey(key1.getId(), pool1.getId(), 3L);
        ActivationKeyDTO key2 = ownerClient.createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        ApiClient noAuth = ApiClients.noAuth();
        ConsumerDTO consumer = noAuth.consumers().createConsumer(Consumers.random(owner), null,
            owner.getKey(), key1.getName() + "," + key2.getName(), true);

        assertThat(consumer.getUuid()).isNotNull();
        List<EntitlementDTO> entitlementDTOS = client.entitlements().listAllForConsumer(consumer.getUuid());

        assertThat(entitlementDTOS)
            .isEmpty();
    }
}
