/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.config.CandlepinCommonTestConfig;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInstalledProduct;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.GuestId;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resource.ConsumerResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.PersistenceException;

public class ConsumerTest extends DatabaseTestFixture {

    private Owner owner;
    private Product rhel;
    private Product jboss;
    private Consumer consumer;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";
    private static final String CONSUMER_NAME = "Test Consumer";
    private static final String USER_NAME = "user33908";

    @Before
    public void setUpTestObjects() {
        unitOfWork.beginWork();

        owner = new Owner("Example Corporation");
        rhel = new Product("rhel", "Red Hat Enterprise Linux");
        jboss = new Product("jboss", "JBoss");

        ownerCurator.create(owner);
        productCurator.create(rhel);
        productCurator.create(jboss);

        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        consumerTypeCurator.create(consumerType);
        consumer = new Consumer(CONSUMER_NAME, USER_NAME, owner, consumerType);
        consumer.setFact("foo", "bar");
        consumer.setFact("foo1", "bar1");

        consumerCurator.create(consumer);

        unitOfWork.endWork();
    }

    @Test(expected = PersistenceException.class)
    public void testConsumerTypeRequired() {
        Consumer newConsumer = new Consumer();
        newConsumer.setName("cname");
        newConsumer.setOwner(owner);

        consumerCurator.create(newConsumer);
    }

    @Test(expected = BadRequestException.class)
    public void testConsumerNameLengthCreate() {
        String name = "";
        for (int x = 0; x < 300; x++) {
            name += "x";
        }
        Consumer newConsumer = new Consumer();
        newConsumer.setName(name);
        newConsumer.setOwner(owner);

        consumerCurator.create(newConsumer);
    }

    @Test(expected = BadRequestException.class)
    public void testConsumerNameLengthUpdate() {
        String name = "";
        for (int x = 0; x < 300; x++) {
            name += "x";
        }
        Consumer newConsumer = new Consumer();
        newConsumer.setName(name);
        newConsumer.setOwner(owner);

        consumerCurator.update(newConsumer);
    }

    @Test
    public void testLookup() throws Exception {
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());
        assertEquals(consumer.getType().getLabel(), lookedUp.getType()
                .getLabel());
        assertNotNull(consumer.getUuid());
    }

    @Test
    public void testSetInitialization() throws Exception {
        Consumer noFacts = new Consumer(CONSUMER_NAME, USER_NAME, owner, consumerType);
        consumerCurator.create(noFacts);
        noFacts = consumerCurator.find(noFacts.getId());
        assertNotNull(noFacts.getFacts());
        assertNotNull(noFacts.getInstalledProducts());
        assertNotNull(noFacts.getGuestIds());
    }

    @Test
    public void testInfo() {
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        Map<String, String> metadata = lookedUp.getFacts();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getFacts().get("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getFacts().get("foo1"));
    }

    @Test
    public void ensureUpdatedDateChangesOnUpdate() {
        Date beforeUpdateDate = consumer.getUpdated();

        ConsumerResource consumerResource = injector.getInstance(ConsumerResource.class);
        consumer.setFact("FACT", "FACT_VALUE");
        consumerResource.updateConsumer(consumer.getUuid(), consumer);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        Date lookedUpDate = lookedUp.getUpdated();
        assertEquals("FACT_VALUE", lookedUp.getFact("FACT"));

        assertTrue("Last updated date was not changed.",
            beforeUpdateDate.before(lookedUpDate));
    }

    @Test
    public void testMetadataInfo() {
        Consumer consumer2 = new Consumer("consumer2", USER_NAME, owner, consumerType);
        consumer2.setFact("foo", "bar2");
        consumerCurator.create(consumer2);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        Map<String, String> metadata = lookedUp.getFacts();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getFacts().get("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getFacts().get("foo1"));

        Consumer lookedUp2 = consumerCurator.find(consumer2.getId());
        metadata = lookedUp2.getFacts();
        assertEquals(1, metadata.keySet().size());
        assertEquals("bar2", metadata.get("foo"));
    }

    @Test
    public void testModifyMetadata() {
        consumer.setFact("foo", "notbar");
        consumerCurator.merge(consumer);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals("notbar", lookedUp.getFact("foo"));
    }

    @Test
    public void testRemoveConsumedProducts() {
        consumerCurator.delete(consumerCurator.find(consumer.getId()));
        assertNull(consumerCurator.find(consumer.getId()));
    }

    @Test
    public void testLookupByUuidNonExistent() {
        consumerCurator.findByUuid("this is not a uuid!");
    }

    @Test
    public void testLookupByUuid() {
        Consumer consumer2 = new Consumer("consumer2", USER_NAME, owner, consumerType);
        consumerCurator.create(consumer2);

        Consumer lookedUp = consumerCurator.findByUuid(consumer2.getUuid());
        assertEquals(lookedUp.getUuid(), consumer2.getUuid());
    }

    @Test
    public void testAddEntitlements() {
        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);
        Pool pool = createPoolAndSub(createOwner(), newProduct,
            1000L, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        entityManager().persist(pool.getOwner());
        entityManager().persist(pool);

        Entitlement e1 = createEntitlement(pool, consumer);
        Entitlement e2 = createEntitlement(pool, consumer);
        Entitlement e3 = createEntitlement(pool, consumer);
        entityManager().persist(e1);
        entityManager().persist(e2);
        entityManager().persist(e3);

        consumer.addEntitlement(e1);
        consumer.addEntitlement(e2);
        consumer.addEntitlement(e3);
        consumerCurator.merge(consumer);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(3, lookedUp.getEntitlements().size());
    }

    private Entitlement createEntitlement(Pool pool, Consumer c) {
        Entitlement e = new Entitlement(pool, c, pool.getStartDate(), pool.getEndDate(), 1);
        return e;
    }

    @Test
    public void testNullType() {
        Consumer c = new Consumer("name", USER_NAME, owner, null);
        assertNotNull(c);
    }

    @Test
    public void canDeleteSelf() {
        setupPrincipal(new ConsumerPrincipal(consumer));

        consumerCurator.delete(consumer);

        assertNull(consumerCurator.find(consumer.getId()));
    }

    @Test
    public void factsEqual() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "two");
        second.setFact("key3", "3");

        assertTrue(first.factsAreEqual(second));
    }

    @Test
    public void defaultFactsEqual() {
        assertTrue(new Consumer().factsAreEqual(new Consumer()));
    }

    @Test
    public void factsDifferentValues() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "2");
        second.setFact("key3", "3");

        assertFalse(first.factsAreEqual(second));
    }

    @Test
    public void factsSecondMissing() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", "two");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");

        assertFalse(first.factsAreEqual(second));
    }

    @Test
    public void factsFirstMissing() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key3", "3");

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "2");
        second.setFact("key3", "3");

        assertFalse(first.factsAreEqual(second));
    }

    @Test
    public void factsEqualNull() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", null);

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", null);

        assertTrue(first.factsAreEqual(second));
    }

    @Test
    public void factsFirstNull() {
        Consumer first = new Consumer();

        first.setFact("key1", "1");
        first.setFact("key2", null);

        Consumer second = new Consumer();

        second.setFact("key1", "1");
        second.setFact("key2", "two");

        assertFalse(first.factsAreEqual(second));
    }

    @Test
    public void factsSecondNull() {
        Consumer first = new Consumer();
        first.setFact("key1", "1");

        Consumer second = new Consumer();
        second.setFact("key1", null);

        assertFalse(first.factsAreEqual(second));
    }

    @Test
    public void testLookupUsersConsumer() {
        String newUsername = "newusername";

        // Need to make sure another consumer already exists, different type:
        Consumer existing = new Consumer("existing consumer", newUsername, owner,
            consumerType);
        consumerCurator.create(existing);

        ConsumerType personType = new ConsumerType(ConsumerTypeEnum.PERSON);
        consumerTypeCurator.create(personType);

        User user = new User(newUsername, "password");
        userCurator.create(user);

        Role adminRole = createAdminRole(owner);
        adminRole.addUser(user);
        roleCurator.create(adminRole);

        assertNull(consumerCurator.findByUser(user));

        consumer = new Consumer(CONSUMER_NAME, newUsername, owner, personType);
        consumerCurator.create(consumer);
        assertEquals(consumer, consumerCurator.findByUser(user));
    }

    @Test
    public void testConsumerFactsFilter() {
        CandlepinCommonTestConfig config =
            (CandlepinCommonTestConfig) injector.getInstance(Config.class);
        String oldValue = config.getString(ConfigProperties.CONSUMER_FACTS_MATCHER);
        config.setProperty(ConfigProperties.CONSUMER_FACTS_MATCHER, "^goodkey.*");

        Consumer consumer = new Consumer("a consumer", "username", owner, consumerType);

        Map<String, String> facts = new HashMap<String, String>();
        facts.put("badkey.something", "zaz");
        facts.put("goodkey.something", "foobar");

        consumer.setFacts(facts);

        consumer = consumerCurator.create(consumer);

        assertNull(consumer.getFact("badkey.something"));
        assertEquals("foobar", consumer.getFact("goodkey.something"));

        consumer.setFact("anotherbadkey", "zippy");

        consumer = consumerCurator.update(consumer);

        assertNull(consumer.getFact("anotherbadkey"));

        config.setProperty(ConfigProperties.CONSUMER_FACTS_MATCHER, oldValue);
    }

    @Test
    public void testInstalledProducts() throws Exception {
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        lookedUp.addInstalledProduct(new ConsumerInstalledProduct("someproduct",
            "someproductname"));
        lookedUp.addInstalledProduct(new ConsumerInstalledProduct("someproduct2",
            "someproductname2"));
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(2, lookedUp.getInstalledProducts().size());
        ConsumerInstalledProduct installed = lookedUp.getInstalledProducts().
            iterator().next();
        lookedUp.getInstalledProducts().remove(installed);
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(1, lookedUp.getInstalledProducts().size());
    }

    @Test
    public void testGuests() throws Exception {
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        lookedUp.addGuestId(new GuestId("guest1"));
        lookedUp.addGuestId(new GuestId("guest2"));
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(2, lookedUp.getGuestIds().size());
        GuestId installed = lookedUp.getGuestIds().
            iterator().next();
        lookedUp.getGuestIds().remove(installed);
        consumerCurator.update(lookedUp);
        lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(1, lookedUp.getGuestIds().size());
    }

}
