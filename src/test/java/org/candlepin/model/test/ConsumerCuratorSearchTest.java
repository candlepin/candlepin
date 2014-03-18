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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.paging.Page;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerCuratorSearchTest
 */
public class ConsumerCuratorSearchTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        CandlepinCommonTestConfig config =
            (CandlepinCommonTestConfig) injector.getInstance(Config.class);
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
            owner, null, null, null, null, factFilters, null);
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
            null, null, null, null, null, factFilters, null);
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
            null, null, null, uuids, null, factFilters, null);
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
            owner, null, null, uuids, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
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
            owner, null, null, null, null, factFilters, null);
        List<Consumer> resultList = results.getPageData();
        assertEquals(2, resultList.size());
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
