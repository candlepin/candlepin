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

import static org.junit.Assert.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestDateUtil;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * ConsumerCuratorSearchTest
 */
public class ConsumerCuratorSearchTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private Configuration config;

    private Owner owner;
    private ConsumerType ct;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        config.setProperty(ConfigProperties.INTEGER_FACTS,
            "system.count, system.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_FACTS, "system.count");
    }

    @Test
    public void testSearchOwnerConsumersNoMatches() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("notAKey", "notAVal"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(0, resultList.size());
    }

    @Test
    public void testSearchConsumersNoOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("testkey", "testval"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            null, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchConsumersUuids() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key", "val");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key", "val");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key", "val"));
        List<String> uuids = new LinkedList<String>();
        uuids.add(consumer.getUuid());
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            null, null, null, uuids, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchConsumersUuidsAndOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key", "val");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key", "val");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        Owner otherOwner = new Owner("test-owner1", "Test Owner1");
        otherOwner = ownerCurator.create(otherOwner);
        Consumer otherOwnCons = new Consumer("testConsumer3", "testUser3", otherOwner, ct);
        Map<String, String> otherOwnFacts = new HashMap<String, String>();
        otherOwnFacts.put("key", "val");
        otherOwnCons.setFacts(otherOwnFacts);
        otherOwnCons = consumerCurator.create(otherOwnCons);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key", "val"));
        List<String> uuids = new LinkedList<String>();
        uuids.add(consumer.getUuid());
        uuids.add(otherOwnCons.getUuid());
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, uuids, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumers() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("testkey", "testval"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersEscaping() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("a", "\"')");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("a", "\"')"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersKeyEscaping() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("%", "'); SELECT id from cp_owners");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("%", "'); SELECT id from cp_owners"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersMoreEscapingWithWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("%", "'); SELECT * from cp_owners");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("%", "'); SELECT * from cp_owners"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersInsensitiveValue() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("testkey", "teSTVal"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersSensitiveKey() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("Testkey", "testval"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(0, resultList.size());
    }

    @Test
    public void testSearchOwnerConsumersKeyWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("testkey", "testval");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("otherconsumerkey123", "testval");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("*key*", "testval"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(2, resultList.size());
    }

    @Test
    public void testSearchOwnerConsumersValueWildcard() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key", "testingval");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key", "testvaltwo");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key", "*val*"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(2, resultList.size());
    }

    @Test
    public void testSearchOwnerConsumersValueWildcardMiddle() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key", "testingvaltest");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key", "testvaltwotest");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key", "test*test"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(2, resultList.size());
    }

    @Test
    public void testSearchOwnerConsumersValueAnd() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key1", "value1");
        otherFacts.put("key2", "value3notsame");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key1", "value1"));
        factFilters.add(new TestingKeyValueParameter("key2", "value2"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchOwnerConsumersValueAndOr() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key1", "value1");
        otherFacts.put("key2", "value3");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key1", "value1"));
        factFilters.add(new TestingKeyValueParameter("key2", "value2"));
        factFilters.add(new TestingKeyValueParameter("key2", "value3"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(2, resultList.size());
    }

    @Test
    public void testSearchOwnerConsumersNoMatch() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("key1", "value1");
        facts.put("key2", "value2");
        //facts.put("otherkey", "otherval");
        consumer.setFacts(facts);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retreiving everyhting
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        Map<String, String> otherFacts = new HashMap<String, String>();
        otherFacts.put("key1", "value2");
        otherFacts.put("key2", "value1");
        otherConsumer.setFacts(otherFacts);
        otherConsumer = consumerCurator.create(otherConsumer);

        List<KeyValueParameter> factFilters = new LinkedList<KeyValueParameter>();
        factFilters.add(new TestingKeyValueParameter("key1", "value2"));
        factFilters.add(new TestingKeyValueParameter("key2", "value1"));
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, factFilters, null, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(otherConsumer, resultList.get(0));
    }

    @Test
    public void testSearchBySubscriptionId() {
        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Product p = new Product("SKU1", "Product 1", owner);
        productCurator.create(p);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

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

        List<String> subscriptionIds = new ArrayList<String>();
        subscriptionIds.add(source1);
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, subscriptionIds, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchByContractNumber() {
        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Product p = new Product("SKU1", "Product 1", owner);
        productCurator.create(p);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_XXX",
            "ACCOUNT_456",
            "ORDER_789"
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        pool2.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> contracts = new ArrayList<String>();
        contracts.add("CONTRACT_123");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchBySku() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        // Create another consumer to make sure we're not just retrieving everything
        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product p = new Product("SKU1", "Product 1", owner);
        p.addAttribute(new ProductAttribute("type", "MKT"));

        Product p2 = new Product("SVC_ID", "Product 2", owner);
        p2.addAttribute(new ProductAttribute("type", "SVC"));

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p2,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_XXX",
            "ACCOUNT_456",
            "ORDER_789"
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        pool2.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> skus = new ArrayList<String>();
        skus.add("SKU1");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));

        skus.clear();
        // MKT_ID should not appear since it is a marketing product
        skus.add("SVC_ID");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        resultList = results.getPageData();
        assertTrue(resultList.isEmpty());
    }

    @Test
    public void testSearchBySkuIsConjunction() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product p = new Product("SKU1", "Product 1", owner);
        p.addAttribute(new ProductAttribute("type", "MKT"));

        Product p2 = new Product("SKU2", "Product 2", owner);
        p2.addAttribute(new ProductAttribute("type", "MKT"));

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p2,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_XXX",
            "ACCOUNT_456",
            "ORDER_789"
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        pool2.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e3);

        List<String> skus = new ArrayList<String>();
        skus.add("SKU1");
        skus.add("SKU2");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));

        skus.clear();
        skus.add("SKU2");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        resultList = results.getPageData();
        assertEquals(2, resultList.size());

        skus.clear();
        skus.add("SKU1");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
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
        Product p = new Product("SKU1", "Product 1", owner);
        p.addAttribute(new ProductAttribute("type", "MKT"));

        Product p2 = new Product("SKU1", "Product 1", owner2);
        p2.addAttribute(new ProductAttribute("type", "MKT"));

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner2,
            p2,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool);

        pool2.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner2, otherConsumer, pool2, cert);
        entitlementCurator.create(e2);

        List<String> skus = new ArrayList<String>();
        skus.add("SKU1");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, null, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));

        results = consumerCurator.searchOwnerConsumers(
            owner2, null, null, null, null, null, skus, null, null, null);
        resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(otherConsumer, resultList.get(0));

        // Searching with no owner cuts across the whole data set
        results = consumerCurator.searchOwnerConsumers(
                null, null, null, null, null, null, skus, null, null, null);
        resultList = results.getPageData();
        assertEquals(2, resultList.size());
    }

    @Test
    public void testSearchByContractNumberIsConjunction() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer otherConsumer = new Consumer("testConsumer2", "testUser2", owner, ct);
        otherConsumer = consumerCurator.create(otherConsumer);

        Product p = new Product("SKU1", "Product 1", owner);
        p.addAttribute(new ProductAttribute("type", "MKT"));

        productCurator.create(p);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            10L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_XXX",
            "ACCOUNT_456",
            "ORDER_789"
        );

        pool.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        pool2.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
        poolCurator.create(pool);
        poolCurator.create(pool2);

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "entcert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, otherConsumer, pool2, cert);
        entitlementCurator.create(e3);

        List<String> contracts = new ArrayList<String>();
        contracts.add("CONTRACT_123");
        contracts.add("CONTRACT_XXX");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));

        contracts.clear();
        contracts.add("CONTRACT_XXX");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts, null);
        resultList = results.getPageData();
        assertEquals(2, resultList.size());

        contracts.clear();
        contracts.add("CONTRACT_123");
        results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, null, null, contracts, null);
        resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    @Test
    public void testSearchByContractAndSku() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", owner, ct);
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer("testConsumer3", "testUser3", owner, ct);
        consumer3 = consumerCurator.create(consumer3);

        Product p = new Product("SKU1", "Product 1", owner);
        p.addAttribute(new ProductAttribute("type", "MKT"));

        Product p2 = new Product("SKU2", "Product 2", owner);
        p2.addAttribute(new ProductAttribute("type", "MKT"));

        productCurator.create(p);
        productCurator.create(p2);

        Pool pool = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        Pool pool2 = new Pool(
            owner,
            p,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_XXX",
            "ACCOUNT_456",
            "ORDER_789"
        );

        // Same contract as 1 but different SKU
        Pool pool3 = new Pool(
            owner,
            p2,
            new HashSet<Product>(),
            1L,
            TestDateUtil.date(2010, 1, 1),
            TestDateUtil.date(2030, 1, 1),
            "CONTRACT_123",
            "ACCOUNT_456",
            "ORDER_789"
        );

        for (Pool x : new Pool[] { pool, pool2, pool3 }) {
            x.setSourceSubscription(new SourceSubscription(Util.generateDbUUID(), "master"));
            poolCurator.create(x);
        }

        EntitlementCertificate cert = createEntitlementCertificate("entkey", "ecert");

        Entitlement e = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(e);

        Entitlement e2 = createEntitlement(owner, consumer2, pool2, cert);
        entitlementCurator.create(e2);

        Entitlement e3 = createEntitlement(owner, consumer3, pool3, cert);
        entitlementCurator.create(e3);

        List<String> skus = new ArrayList<String>();
        skus.add("SKU1");
        List<String> contracts = new ArrayList<String>();
        contracts.add("CONTRACT_123");
        Page<List<Consumer>> results = consumerCurator.searchOwnerConsumers(
            owner, null, null, null, null, null, skus, null, contracts, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(1, resultList.size());
        assertEquals(consumer, resultList.get(0));
    }

    private class TestingKeyValueParameter extends KeyValueParameter {

        /**
         * @param queryParamName
         * @param queryParameterValue
         */
        public TestingKeyValueParameter(String key,
            String value) {
            super(key, value);
        }

        @Override
        public String key() {
            return this.paramName;
        }

        @Override
        public String value() {
            return this.paramValue;
        }
    }
}
