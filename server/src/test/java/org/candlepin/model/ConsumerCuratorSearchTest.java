/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;



/**
 * ConsumerCuratorSearchTest
 */
public class ConsumerCuratorSearchTest extends DatabaseTestFixture {

    @Inject private Configuration config;

    private Owner owner;
    private ConsumerType ct;

    @BeforeEach
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        config.setProperty(ConfigProperties.INTEGER_FACTS, "system.count, system.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_FACTS, "system.count");
    }

    @Test
    public void testSearchOwnerConsumersNoMatches() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("notAKey", "notAVal"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(0, results.size());
    }

    private KeyValueParamDTO getE(String key, String val) {
        return new KeyValueParamDTO()
            .key(key)
            .value(val);
    }

    @Test
    public void testSearchConsumersNoOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("testkey", "testval"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            null, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchConsumersUuids() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key", "val");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key", "val");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key", "val"));
        List<String> uuids = new LinkedList<>();
        uuids.add(consumer.getUuid());
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            null, null, null, uuids, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchHypervisorIdsCaseInsensitive() {
        String hypervisorid = "HyPuUiD";
        String hypervisorid2 = "HyPuUiD2";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        HypervisorId hypervisorId = new HypervisorId(hypervisorid);
        hypervisorId.setOwner(owner);
        consumer.setHypervisorId(hypervisorId);
        consumer = consumerCurator.create(consumer);

        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", owner, ct);
        HypervisorId hypervisorId2 = new HypervisorId(hypervisorid2);
        hypervisorId2.setOwner(owner);
        consumer2.setHypervisorId(hypervisorId2);
        consumer2 = consumerCurator.create(consumer2);

        List<String> hypervisorIds = new ArrayList<>();
        hypervisorIds.add(hypervisorid.toUpperCase());

        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            null, null, null, null, hypervisorIds, null, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchConsumersUuidsAndOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key", "val");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key", "val");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        Owner otherOwner = new Owner("test-owner1", "Test Owner1");
        otherOwner = ownerCurator.create(otherOwner);
        Consumer otherOwnCons = new Consumer("testConsumer3", "testUser3", otherOwner, ct);
        Map<String, String> otherOwnFacts = new HashMap<>();
        otherOwnFacts.put("key", "val");
        otherOwnCons.setFacts(otherOwnFacts);
        otherOwnCons = consumerCurator.create(otherOwnCons);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key", "val"));
        List<String> uuids = new LinkedList<>();
        uuids.add(consumer.getUuid());
        uuids.add(otherOwnCons.getUuid());
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, uuids, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumers() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("testkey", "testval"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersEscaping() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("a", "\"')");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("a", "\"')"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersKeyEscaping() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("%", "'); SELECT id from cp_owners");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("%", "'); SELECT id from cp_owners"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersMoreEscapingWithWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("%", "'); SELECT * from cp_owners");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("%", "'); SELECT * from cp_owners"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersInsensitiveValue() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("testkey", "teSTVal"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersSensitiveKey() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("Testkey", "testval"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(0, results.size());
    }

    @Test
    public void testSearchOwnerConsumersKeyWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("testkey", "testval");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("otherconsumerkey123", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("*key*", "testval"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchOwnerConsumersValueWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key", "testingval");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key", "testvaltwo");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key", "*val*"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchOwnerConsumersValueWildcardMiddle() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key", "testingvaltest");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key", "testvaltwotest");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key", "test*test"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchOwnerConsumersValueAnd() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key1", "value1");
        otherFacts.put("key2", "value3notsame");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key1", "value1"));
        factFilters.add(getE("key2", "value2"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchOwnerConsumersValueAndOr() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key1", "value1");
        otherFacts.put("key2", "value3");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key1", "value1"));
        factFilters.add(getE("key2", "value2"));
        factFilters.add(getE("key2", "value3"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(2, results.size());
    }

    @Test
    public void testSearchOwnerConsumersNoMatch() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<>();
        otherFacts.put("key1", "value2");
        otherFacts.put("key2", "value1");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParamDTO> factFilters = new LinkedList<>();
        factFilters.add(getE("key1", "value2"));
        factFilters.add(getE("key2", "value1"));
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null).list();
        assertEquals(1, results.size());
        assertEquals(otherConsumer, results.get(0));
    }

    @Test
    public void testSearchBySubscriptionId() {
        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Product p = TestUtil.createProduct("SKU1", "Product 1");
        productCurator.create(p);

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(p)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDate(2010, 1, 1))
            .setEndDate(TestUtil.createDate(2030, 1, 1))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789");

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(p)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDate(2010, 1, 1))
            .setEndDate(TestUtil.createDate(2030, 1, 1))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789");

        String source1 = Util.generateDbUUID();
        String source2 = Util.generateDbUUID();
        pool.setSourceSubscription(new SourceSubscription(source1, "master"));
        pool2.setSourceSubscription(new SourceSubscription(source2, "master2"));
        poolCurator.create(pool);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> subscriptionIds = new ArrayList<>();
        subscriptionIds.add(source1);
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, subscriptionIds, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchByContractNumber() {
        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Product product = this.createProduct("SKU1", "Product 1");

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        poolCurator.create(pool1);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> contracts = new ArrayList<>();
        contracts.add("CONTRACT_123");
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts).list();

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchBySku() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product p = TestUtil.createProduct("SKU1", "Product 1");
        p.setAttribute(Product.Attributes.TYPE, "MKT");

        Product p2 = TestUtil.createProduct("SVC_ID", "Product 2");
        p2.setAttribute(Product.Attributes.TYPE, "SVC");

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(p)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(p2)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        poolCurator.create(pool1);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> skus = new ArrayList<>();
        skus.add("SKU1");
        List<Consumer> results = consumerCurator
            .searchOwnerConsumers(owner, null, null, null, null, null, skus, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));

        // MKT_ID should not appear since it is a marketing product
        skus.clear();
        skus.add("SVC_ID");
        results = consumerCurator
            .searchOwnerConsumers(owner, null, null, null, null, null, skus, null, null)
            .list();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearchBySkuIsConjunction() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product product1 = TestUtil.createProduct("SKU1", "Product 1");
        product1.setAttribute(Product.Attributes.TYPE, "MKT");

        Product product2 = TestUtil.createProduct("SKU2", "Product 2");
        product2.setAttribute(Product.Attributes.TYPE, "MKT");

        productCurator.create(product1);
        productCurator.create(product2);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(product1)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(product2)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        poolCurator.create(pool1);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e3);

        List<String> skus = new ArrayList<>();
        skus.add("SKU1");
        skus.add("SKU2");
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));

        skus.clear();
        skus.add("SKU2");
        results = consumerCurator.searchOwnerConsumers(owner, null, null, null, null, null, skus, null, null)
            .list();
        assertEquals(2, results.size());

        skus.clear();
        skus.add("SKU1");
        results = consumerCurator.searchOwnerConsumers(owner, null, null, null, null, null, skus, null, null)
            .list();

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchBySkuIsWithinOneOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Owner owner2 = new Owner("test-owner2", "Test Owner2");
        owner2 = ownerCurator.create(owner2);
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner2, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        // Two owners, two different products, but with the same SKU
        Product product1 = TestUtil.createProduct("SKU1", "Product 1");
        product1.setAttribute(Product.Attributes.TYPE, "MKT");

        Product product2 = TestUtil.createProduct("SKU1", "Product 1");
        product2.setAttribute(Product.Attributes.TYPE, "MKT");

        productCurator.create(product1);
        productCurator.create(product2);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(product1)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner2)
            .setProduct(product2)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        poolCurator.create(pool1);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner2, otherConsumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> skus = new ArrayList<>();
        skus.add("SKU1");
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null).list();

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));

        results = consumerCurator.searchOwnerConsumers(owner2, null, null, null, null, null, skus, null, null)
            .list();

        assertEquals(1, results.size());
        assertEquals(otherConsumer, results.get(0));

        // Searching with no owner cuts across the whole data set
        results = consumerCurator.searchOwnerConsumers(null, null, null, null, null, null, skus, null, null)
            .list();

        assertEquals(2, results.size());
    }

    @Test
    public void testSearchByContractNumberIsConjunction() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product product = TestUtil.createProduct("SKU1", "Product 1");
        product.setAttribute(Product.Attributes.TYPE, "MKT");

        productCurator.create(product);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(10L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        poolCurator.create(pool1);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e3);

        List<String> contracts = new ArrayList<>();
        contracts.add("CONTRACT_123");
        contracts.add("CONTRACT_XXX");
        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts).list();

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));

        contracts.clear();
        contracts.add("CONTRACT_XXX");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts).list();
        assertEquals(2, results.size());

        contracts.clear();
        contracts.add("CONTRACT_123");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testSearchByContractAndSku() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", owner, ct);
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer("testConsumer3", "testUser3", owner, ct);
        consumer3 = consumerCurator.create(consumer3);

        Product p = TestUtil.createProduct("SKU1", "Product 1");
        p.setAttribute(Product.Attributes.TYPE, "MKT");

        Product p2 = TestUtil.createProduct("SKU2", "Product 2");
        p2.setAttribute(Product.Attributes.TYPE, "MKT");

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool1 = new Pool()
            .setOwner(owner)
            .setProduct(p)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-3, 0, 0))
            .setEndDate(TestUtil.createDateOffset(3, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        Pool pool2 = new Pool()
            .setOwner(owner)
            .setProduct(p)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-3, 0, 0))
            .setEndDate(TestUtil.createDateOffset(3, 0, 0))
            .setContractNumber("CONTRACT_XXX")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        // Same contract as 1 but different SKU
        Pool pool3 = new Pool()
            .setOwner(owner)
            .setProduct(p2)
            .setQuantity(1L)
            .setStartDate(TestUtil.createDateOffset(-3, 0, 0))
            .setEndDate(TestUtil.createDateOffset(3, 0, 0))
            .setContractNumber("CONTRACT_123")
            .setAccountNumber("ACCOUNT_456")
            .setOrderNumber("ORDER_789")
            .setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));

        this.poolCurator.create(pool1);
        this.poolCurator.create(pool2);
        this.poolCurator.create(pool3);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool1, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer2, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, consumer3, pool3, cert);
        entitlementCurator.create(e3);

        List<String> skus = new ArrayList<>();
        skus.add("SKU1");

        List<String> contracts = new ArrayList<>();
        contracts.add("CONTRACT_123");

        List<Consumer> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, contracts).list();

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }
}
