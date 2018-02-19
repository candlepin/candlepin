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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Product.Attributes;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;
import org.candlepin.util.PropertyValidationException;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.EntityManager;



/**
 * ConsumerCuratorTest JUnit tests for Consumer database code
 */
public class ConsumerCuratorTest extends DatabaseTestFixture {

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Inject private Configuration config;
    @Inject private DeletedConsumerCurator dcc;
    @Inject private EntityManager em;

    private Owner owner;
    private ConsumerType ct;
    private Consumer factConsumer;
    private Set<String> typeLabels;
    private List<String> skus;
    private List<String> subscriptionIds;
    private List<String> contracts;

    @Before
    public void setUp() throws Exception {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        config.setProperty(ConfigProperties.INTEGER_FACTS, "system.count, system.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_FACTS, "system.count");

        // Inject this factValidator into the curator
        Field field = ConsumerCurator.class.getDeclaredField("factValidator");
        field.setAccessible(true);
        field.set(this.consumerCurator, new FactValidator(this.config, this.i18n));

        factConsumer = new Consumer("a consumer", "username", owner, ct);

        typeLabels = null;
        skus = null;
        subscriptionIds = null;
        contracts = null;
    }

    private Product createConsumerWithBindingToMKTProduct(Owner owner) {
        Consumer c = createConsumer(owner);
        return createMktProductAndBindItToConsumer(owner, c);
    }

    private Product createMktProductAndBindItToConsumer(Owner owner, Consumer c) {
        String id = String.valueOf(TestUtil.randomInt());
        Product product = createTransientMarketingProduct(id);
        product = createProduct(product, owner);
        Pool pool = createPool(owner, product);
        createEntitlement(owner, c, pool, null);
        return product;
    }

    private Product createTransientMarketingProduct(String productId) {
        Product p = new Product(productId, "test-product-" + productId);
        p.setAttribute(Attributes.TYPE, "MKT");

        return p;
    }

    private Product createProductAndBindItToConsumer(Owner owner, Consumer c) {
        Product product = createProduct(owner);
        Pool pool = createPool(owner, product);
        createEntitlement(owner, c, pool, null);
        return product;
    }

    private void createProductAndBindItToConsumer(Owner owner, Consumer consumer,
        String contractName) {
        Product p1 = createProduct(owner);
        Pool pool1 = createPool(
            owner, p1, 1L,
            Util.yesterday(),
            Util.tomorrow(),
            contractName);
        createEntitlement(owner, consumer, pool1, null);
    }

    private Pool createPoolAndBindItToConsumer(Owner owner, Consumer c) {
        Product product = createProduct(owner);
        Pool pool = createPool(owner, product);
        createEntitlement(owner, c, pool, null);
        return pool;
    }

    private Pool createConsumerWithBindingToProduct(Owner owner, Product product) {
        Pool pool = createPool(owner, product);
        createConsumerAndEntitlement(owner, pool);
        return pool;
    }

    private Pool createConsumerWithBindingToProduct(Owner owner, Product product, String contract) {
        Pool pool = createPool(
            owner, product, 1L,
            Util.yesterday(),
            Util.tomorrow(),
            contract);
        createConsumerAndEntitlement(owner, pool);
        return pool;
    }

    private void createConsumerAndEntitlement(Owner owner, Pool pool) {
        Consumer c = createConsumer(owner);
        createEntitlement(owner, c, pool, null);
    }

    private Consumer createConsumerAndBindItToProduct(Owner owner, Product p) {
        Consumer c = createConsumer(owner);
        Pool pool1 = createPool(owner, p);
        createEntitlement(owner, c, pool1, null);
        return c;
    }

    private Consumer createConsumerAndBindItToProduct(Owner o, Product product, String subId) {
        Consumer c = createConsumer(o);
        Pool p = createPool(o, product, 1L, subId,
            "subsSubKey", Util.yesterday(), Util.tomorrow());
        createEntitlement(o, c, p, null);
        return c;
    }

    private Consumer createConsumerAndBindItToProduct(Owner o, Product product, String subId,
        String contract) {
        Consumer cSkuAndSubIdAndContract = createConsumer(o);
        Pool p = createPool(o, product, 1L, Util.yesterday(), Util.tomorrow(), contract, subId);
        createEntitlement(o, cSkuAndSubIdAndContract, p, null);
        return cSkuAndSubIdAndContract;
    }

    @Test
    public void normalCreate() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);

        List<Consumer> results = this.getEntityManager()
            .createQuery("select c from Consumer as c", Consumer.class)
            .getResultList();

        assertEquals(1, results.size());
    }

    @Test
    public void addGuestConsumers() {
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer1);
        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "test-guest-2");
        consumerCurator.create(gConsumer2);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertEquals(2, guests.size());
    }

    @Test
    public void addGuestConsumersReversedEndianGuestId() {
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "06F81B41-AAC0-7685-FBE9-79AA4A326511");
        consumerCurator.create(gConsumer1);
        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "4C4C4544-0046-4210-8031-C7C04F445831");
        consumerCurator.create(gConsumer2);
        // Reversed endian, first 3 sections
        consumer.addGuestId(new GuestId("411bf806-c0aa-8576-fbe9-79aa4a326511"));
        // matches a guests facts, case insensitive
        consumer.addGuestId(new GuestId("4c4c4544-0046-4210-8031-c7c04f445831"));
        // Doesn't match a registered guest consumer
        consumer.addGuestId(new GuestId("43e41def-e9ae-4b6b-b8f4-942c8b69a39e"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 2);
    }

    @Test
    public void caseInsensitiveVirtUuidMatching() {
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        List<Consumer> guests = consumerCurator.getGuests(host);
        assertTrue(guests.size() == 1);
    }

    @Test
    public void caseInsensitiveVirtUuidMatchingDifferentOwners() {
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        owner = new Owner("test-owner2", "Test Owner2");
        owner = ownerCurator.create(owner);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        List<Consumer> guests = consumerCurator.getGuests(host);
        assertTrue(guests.size() == 0);
    }

    @Test
    public void addGuestsNotConsumers() {
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 0);
    }

    @Test
    public void getGuestConsumerSharedId() throws Exception {
        Consumer hConsumer1 = new Consumer("hostConsumer1", "testUser", owner, ct);
        consumerCurator.create(hConsumer1);
        Consumer hConsumer2 = new Consumer("hostConsumer2", "testUser", owner, ct);
        consumerCurator.create(hConsumer2);
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "shared-guest-id");
        consumerCurator.create(gConsumer1);

        // This can happen so fast the consumers end up with the same created/updated time
        Thread.sleep(500);

        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "shared-guest-id");
        consumerCurator.create(gConsumer2);

        GuestId hGuest1 = new GuestId("shared-guest-id");
        hConsumer1.addGuestId(hGuest1);
        consumerCurator.update(hConsumer1);

        // This can happen so fast the consumers end up with the same created/updated time
        Thread.sleep(500);

        // Uppercase the guest ID reported by host 2 just to make sure the casing is
        // working properly here too:
        GuestId hGuest2 = new GuestId("SHARED-GUEST-ID");
        hConsumer2.addGuestId(hGuest2);
        consumerCurator.update(hConsumer2);

        List<Consumer> guests1 = consumerCurator.getGuests(hConsumer1);
        List<Consumer> guests2 = consumerCurator.getGuests(hConsumer2);
        assertTrue("Expected " + hGuest1.getUpdated() + " to be before " + hGuest2.getUpdated(),
            hGuest1.getUpdated().before(hGuest2.getUpdated()));
        assertEquals(0, guests1.size());
        assertEquals(1, guests2.size());
        assertEquals("guestConsumer2", guests2.get(0).getName());
    }

    @Test
    public void noHostRegistered() {
        Consumer host = consumerCurator.getHost("system-uuid-for-guest", owner);
        assertTrue(host == null);
    }

    @Test
    public void oneHostRegistered() {
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
    }

    @Test
    public void caseInsensitiveGetHost() {
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost(
            "Daf0fe10-956b-7b4e-b7dc-B383CE681ba8", owner);
        assertEquals(host, guestHost);
    }

    @Test
    public void oneHostRegisteredReverseEndian() {
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost(
            "10fef0da-6b95-4e7b-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
    }

    @Test
    public void twoHostsRegisteredPickSecond() throws Exception {
        long base = System.currentTimeMillis() - 10000;

        Consumer host1 = new Consumer("hostConsumer", "testUser", owner, ct);
        host1.setCreated(new Date(base));
        consumerCurator.create(host1);

        Consumer host2 = new Consumer("hostConsumer2", "testUser2", owner, ct);
        host2.setCreated(new Date(base + 1000));
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.setCreated(new Date(base + 2000));
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        GuestId host1Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host1Guest.setCreated(new Date(base + 3000));
        host1.addGuestId(host1Guest);
        consumerCurator.update(host1);

        Thread.sleep(1000);

        GuestId host2Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host2Guest.setCreated(new Date(base + 4000));
        host2.addGuestId(host2Guest);
        consumerCurator.update(host2);

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);

        assertTrue(host1Guest.getUpdated().before(host2Guest.getUpdated()));
        assertEquals(host2.getUuid(), guestHost.getUuid());
    }

    @Test
    public void twoHostsRegisteredPickFirst() throws Exception {
        long base = System.currentTimeMillis() - 10000;

        Consumer host1 = new Consumer("hostConsumer", "testUser", owner, ct);
        host1.setCreated(new Date(base));
        consumerCurator.create(host1);

        Consumer host2 = new Consumer("hostConsumer2", "testUser2", owner, ct);
        host2.setCreated(new Date(base + 1000));
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.setCreated(new Date(base + 2000));
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        GuestId host2Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host2Guest.setCreated(new Date(base + 3000));
        host2.addGuestId(host2Guest);
        consumerCurator.update(host2);

        Thread.sleep(1000);

        GuestId host1Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host1Guest.setCreated(new Date(base + 4000));
        host1.addGuestId(host1Guest);
        consumerCurator.update(host1);

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertTrue(host1Guest.getUpdated().after(host2Guest.getUpdated()));
        assertEquals(host1.getUuid(), guestHost.getUuid());
    }

    @Test
    public void getHostOnceFromDb() {
        ConsumerCurator spy = Mockito.spy(consumerCurator);
        Consumer host = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
        guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
        guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
        verify(spy, times(1)).currentSession();
    }

    @Test
    public void getCorrectHostFromCache() {
        ConsumerCurator spy = Mockito.spy(consumerCurator);
        Consumer hostA = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(hostA);
        Consumer hostB = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(hostB);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba9");
        consumerCurator.create(gConsumer2);

        hostA.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(hostA);
        hostB.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA9"));
        consumerCurator.update(hostB);

        Consumer guestHostA = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        Consumer guestHostB = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba9", owner);
        assertEquals(hostA, guestHostA);
        assertEquals(hostB, guestHostB);
        guestHostA = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(hostA, guestHostA);
        guestHostB = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba9", owner);
        assertEquals(hostB, guestHostB);
        verify(spy, times(2)).currentSession();
    }

    @Test
    public void noGuestsRegistered() {
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 0);
    }

    @Test
    public void updateCheckinTime() {
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);
        Date dt = ResourceDateParser.parseDateString("2011-09-26T18:10:50.184081+00:00");
        consumerCurator.updateLastCheckin(consumer, dt);
        consumerCurator.refresh(consumer);
        consumer = consumerCurator.find(consumer.getId());

        assertEquals(consumer.getLastCheckin().getTime(), dt.getTime());
    }
    @Test
    public void updatelastCheckin() throws Exception {
        Date date = new Date();
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumer.setLastCheckin(date);
        Thread.sleep(5); // sleep for at 5ms to allow enough time to pass
        consumer = consumerCurator.create(consumer);
        consumerCurator.updateLastCheckin(consumer);
        consumerCurator.refresh(consumer);
        assertTrue(consumer.getLastCheckin().getTime() > date.getTime());
    }

    @Test
    public void delete() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);
        String cid = consumer.getUuid();

        consumerCurator.delete(consumer);
        assertEquals(1, dcc.countByConsumerUuid(cid));
        DeletedConsumer dc = dcc.findByConsumerUuid(cid);

        assertEquals(cid, dc.getConsumerUuid());
        assertEquals(owner.getId(), dc.getOwnerId());
    }

    @Test
    public void deleteTwice() {
        // attempt to create and delete the same consumer uuid twice
        Owner altOwner = new Owner("test-owner2", "Test Owner2");

        altOwner = ownerCurator.create(altOwner);

        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("Doppelganger");
        consumer = consumerCurator.create(consumer);

        consumerCurator.delete(consumer);
        DeletedConsumer dc = dcc.findByConsumerUuid("Doppelganger");
        Date deletionDate1 = dc.getUpdated();

        consumer = new Consumer("testConsumer", "testUser", altOwner, ct);
        consumer.setUuid("Doppelganger");
        consumer = consumerCurator.create(consumer);
        consumerCurator.delete(consumer);
        dc = dcc.findByConsumerUuid("Doppelganger");
        Date deletionDate2 = dc.getUpdated();
        assertEquals(-1, deletionDate1.compareTo(deletionDate2));
        assertEquals(altOwner.getId(), dc.getOwnerId());
    }

    @Test
    public void testConsumerFactsVerifySuccess() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "3");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);

        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
        assertEquals(factConsumer.getFact("system.count"), "3");
        assertEquals(factConsumer.getFact("system.multiplier"), "-2");
    }

    @Test(expected = PropertyValidationException.class)
    public void testConsumerFactsVerifyBadInt() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "zzz");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
    }

    @Test(expected = PropertyValidationException.class)
    public void testConsumerFactsVerifyBadPositive() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "-2");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
    }

    @Test(expected = PropertyValidationException.class)
    public void testConsumerFactsVerifyBadUpdateValue() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "3");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
        assertEquals(factConsumer.getFact("system.count"), "3");
        assertEquals(factConsumer.getFact("system.multiplier"), "-2");

        factConsumer.setFact("system.count", "sss");
        factConsumer = consumerCurator.update(factConsumer);
    }

    @Test
    public void testSubstringConfigList() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.cou", "this should not be checked");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
    }

    @Test
    public void testFindByUuids() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", owner, ct);
        consumer2.setUuid("2");
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer("testConsumer3", "testUser3", owner, ct);
        consumer3.setUuid("3");
        consumer3 = consumerCurator.create(consumer3);

        List<Consumer> results = consumerCurator.findByUuids(Arrays.asList("1", "2")).list();
        assertTrue(results.contains(consumer));
        assertTrue(results.contains(consumer2));
        assertFalse(results.contains(consumer3));
    }

    @Test
    public void testFindByUuidsAndOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Owner owner2 = new Owner("test-owner2", "Test Owner2");
        ownerCurator.create(owner2);

        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", owner2, ct);
        consumer2.setUuid("2");
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer("testConsumer3", "testUser3", owner2, ct);
        consumer3.setUuid("3");
        consumer3 = consumerCurator.create(consumer3);

        List<Consumer> results = consumerCurator.findByUuidsAndOwner(Arrays.asList("2"), owner2).list();
        assertTrue(results.contains(consumer2));
        assertFalse(results.contains(consumer));
        assertFalse(results.contains(consumer3));
    }

    @Test
    public void getGuestConsumerMap() {
        String guestId1 = "06F81B41-AAC0-7685-FBE9-79AA4A326511";
        String guestId1ReverseEndian = "411bf806-c0aa-8576-fbe9-79aa4a326511";
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", guestId1);
        consumerCurator.create(gConsumer1);

        String guestId2 = "4C4C4544-0046-4210-8031-C7C04F445831";
        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", guestId2);
        consumerCurator.create(gConsumer2);

        Set<String> guestIds = new HashSet<String>();
        guestIds.add(guestId1ReverseEndian); // reversed endian match
        guestIds.add(guestId2); // direct match
        VirtConsumerMap guestMap = consumerCurator.getGuestConsumersMap(owner, guestIds);

        assertEquals(2, guestMap.size());

        assertEquals(gConsumer1.getId(), guestMap.get(guestId1.toLowerCase()).getId());
        assertEquals(gConsumer1.getId(), guestMap.get(guestId1ReverseEndian).getId());

        assertEquals(gConsumer2.getId(), guestMap.get(guestId2.toLowerCase()).getId());
        assertEquals(gConsumer2.getId(), guestMap.get(Util.transformUuid(guestId2.toLowerCase())).getId());
    }

    @Test
    public void getGuestConsumerMapCaseInsensitive() {
        String guestId1 = "06f81b41-AAC0-7685-FBE9-79AA4A326511";
        String guestId1ReverseEndian = "411bf806-c0aa-8576-fbe9-79aa4a326511";
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", guestId1);
        consumerCurator.create(gConsumer1);

        String guestId2 = "4c4c4544-0046-4210-8031-C7C04F445831";
        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", guestId2);
        consumerCurator.create(gConsumer2);

        Set<String> guestIds = new HashSet<String>();
        guestIds.add(guestId1ReverseEndian.toUpperCase()); // reversed endian match
        guestIds.add(guestId2.toUpperCase()); // direct match
        VirtConsumerMap guestMap = consumerCurator.getGuestConsumersMap(owner, guestIds);

        assertEquals(2, guestMap.size());

        assertEquals(gConsumer1.getId(), guestMap.get(guestId1.toLowerCase()).getId());
        assertEquals(gConsumer1.getId(), guestMap.get(guestId1ReverseEndian).getId());

        assertEquals(gConsumer2.getId(), guestMap.get(guestId2.toLowerCase()).getId());
        assertEquals(gConsumer2.getId(), guestMap.get(Util.transformUuid(guestId2.toLowerCase())).getId());
    }

    @Test
    public void testDoesConsumerExistNo() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);
        boolean result = consumerCurator.doesConsumerExist("unknown");
        assertFalse(result);
    }

    @Test
    public void testDoesConsumerExistYes() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);
        boolean result = consumerCurator.doesConsumerExist("1");
        assertTrue(result);
    }

    @Test
    public void testFindByUuid() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Consumer result = consumerCurator.findByUuid("1");
        assertEquals(result, consumer);
    }

    @Test
    public void testFindByUuidDoesntMatch() {
        Consumer result = consumerCurator.findByUuid("1");
        assertNull(result);
    }

    @Test
    public void testVerifyAndLookupConsumer() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Consumer result = consumerCurator.verifyAndLookupConsumer("1");
        assertEquals(result, consumer);
    }

    @Test(expected = NotFoundException.class)
    public void testVerifyAndLookupConsumerDoesntMatch() {
        Consumer result = consumerCurator.verifyAndLookupConsumer("1");
        assertNull(result);
    }

    @Test
    public void testGetHypervisor() {
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        Consumer result = consumerCurator.getHypervisor(hypervisorid, owner);
        assertEquals(consumer, result);
    }

    @Test
    public void testGetHypervisorCaseInsensitive() {
        String hypervisorid = "HYpervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        Consumer result = consumerCurator.getHypervisor(hypervisorid.toUpperCase(), owner);
        assertEquals(consumer, result);
    }

    @Test
    public void testGetHypervisorWrongOwner() {
        Owner otherOwner = new Owner("test-owner-other", "Test Other Owner");
        otherOwner = ownerCurator.create(otherOwner);
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        Consumer result = consumerCurator.getHypervisor(hypervisorid, otherOwner);
        assertNull(result);
    }

    @Test
    public void testGetHypervisorConsumerMap() {
        String hypervisorId1 = "Hypervisor";
        Consumer consumer1 = new Consumer("testConsumer", "testUser", owner, ct);
        consumer1.setHypervisorId(new HypervisorId(hypervisorId1));
        consumer1 = consumerCurator.create(consumer1);

        String hypervisorId2 = "hyPERvisor2";
        Consumer consumer2 = new Consumer("testConsumer", "testUser", owner, ct);
        consumer2.setHypervisorId(new HypervisorId(hypervisorId2));
        consumer2 = consumerCurator.create(consumer2);

        Set<String> hypervisorIds = new HashSet<String>();
        hypervisorIds.add(hypervisorId1);
        hypervisorIds.add(hypervisorId2);
        hypervisorIds.add("not really a hypervisor");

        VirtConsumerMap hypervisorMap = consumerCurator.getHostConsumersMap(owner, hypervisorIds);
        assertEquals(2, hypervisorMap.size());
        assertEquals(consumer1.getId(), hypervisorMap.get(hypervisorId1).getId());
        assertEquals(consumer2.getId(), hypervisorMap.get(hypervisorId2).getId());
    }

    @Test
    public void testGetHypervisorsBulk() {
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        List<String> hypervisorIds = new LinkedList<String>();
        hypervisorIds.add(hypervisorid);
        hypervisorIds.add("not really a hypervisor");
        List<Consumer> results = consumerCurator.getHypervisorsBulk(hypervisorIds, owner.getKey()).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testGetHypervisorsBulkCaseInsensitive() {
        String hypervisorid = "hYPervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        List<String> hypervisorIds = new LinkedList<String>();
        hypervisorIds.add(hypervisorid.toUpperCase());
        hypervisorIds.add("NOT really a hypervisor");
        List<Consumer> results = consumerCurator.getHypervisorsBulk(hypervisorIds, owner.getKey()).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }


    @Test
    public void testGetHypervisorsBulkEmpty() {
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        List<Consumer> results = consumerCurator
            .getHypervisorsBulk(new LinkedList<String>(), owner.getKey())
            .list();

        assertEquals(0, results.size());
    }

    @Test
    public void testGetHypervisorsByOwner() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId("hypervisor"));
        consumer = consumerCurator.create(consumer);
        Owner otherOwner = ownerCurator.create(new Owner("other owner"));
        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", otherOwner, ct);
        consumer2.setHypervisorId(new HypervisorId("hypervisortwo"));
        consumer2 = consumerCurator.create(consumer2);
        Consumer nonHypervisor = new Consumer("testConsumer3", "testUser3", owner, ct);
        nonHypervisor = consumerCurator.create(nonHypervisor);
        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getKey()).list();
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testGetConsumerIdsWithStartedEnts() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        Product prod = this.createProduct("1", "2", owner);

        Pool p = createPool(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this.createEntitlement(owner, consumer, p,
            createEntitlementCertificate("entkey", "ecert"));
        ent.setUpdatedOnStart(false);
        entitlementCurator.create(ent);

        List<String> results = consumerCurator.getConsumerIdsWithStartedEnts();
        assertEquals(1, results.size());
        assertEquals(consumer.getId(), results.get(0));
    }

    @Test
    public void testGetConsumerIdsWithStartedEntsAlreadyDone() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        Product prod = this.createProduct("1", "2", owner);

        Pool p = createPool(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this.createEntitlement(owner, consumer, p,
            createEntitlementCertificate("entkey", "ecert"));
        // Already taken care of
        ent.setUpdatedOnStart(true);
        entitlementCurator.create(ent);

        List<String> results = consumerCurator.getConsumerIdsWithStartedEnts();
        assertTrue(results.isEmpty());
    }

    @Test
    public void testConsumerDeleteCascadesToContentTag() {
        Consumer c = new Consumer("testConsumer", "testUser", owner, ct);
        c.setContentTags(new HashSet<String>(Arrays.asList(new String[] {"t1", "t2"})));

        String countQuery = "SELECT COUNT(*) FROM cp_consumer_content_tags";

        consumerCurator.create(c);
        BigInteger i = (BigInteger) em.createNativeQuery(countQuery).getSingleResult();
        assertEquals(new BigInteger("2"), i);

        consumerCurator.delete(c);
        i = (BigInteger) em.createNativeQuery(countQuery).getSingleResult();
        assertEquals(new BigInteger("0"), i);
    }

    // select by owner
    @Test
    public void nullCollectionsShouldCountAllExistingConsumers() throws Exception {
        createConsumer(owner);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void emptyCollectionsShouldCountAllExistingConsumers() throws Exception {
        createConsumer(owner);
        typeLabels = new HashSet<String>();
        skus = new LinkedList<String>();
        subscriptionIds = new LinkedList<String>();
        contracts = new LinkedList<String>();

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldThrowExceptionIfOwnerKeyIsNull() throws Exception {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Owner key can't be null or empty");

        String ownerKey = null;
        consumerCurator.countConsumers(ownerKey, typeLabels, skus,
            subscriptionIds, contracts);
    }

    @Test
    public void countShouldThrowExceptionIfOwnerKeyIsEmpty() throws Exception {
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("Owner key can't be null or empty");

        String ownerKey = "";

        consumerCurator.countConsumers(ownerKey, typeLabels, skus,
            subscriptionIds, contracts);
    }

    @Test
    public void countShouldReturnZeroIfOwnerHasNotAnyConsumers() throws Exception {
        createConsumer(owner);
        Owner ownerWithoutConsumers = createOwner();

        int count = consumerCurator.countConsumers(ownerWithoutConsumers.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(0, count);
    }

    @Test
    public void shouldCountConsumersOnlyOfGivenOwner() {
        createConsumer(owner);
        Owner otherOwner = createOwner();
        createConsumer(otherOwner);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldReturnZeroIfUnkownOwner() throws Exception {
        createConsumer(owner);

        int count = consumerCurator.countConsumers("unknown-key", typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(0, count);
    }

    //select by TypeLabels
    @Test
    public void shouldCountOwnerConsumersOnlyWithTypeLabels() {
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);
        String l1 = c1.getType().getLabel();
        String l2 = c2.getType().getLabel();
        assertNotEquals(l1, l2);
        HashSet<String> typeLabels = new HashSet<String>(1);
        typeLabels.add(l1);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void testDisjunctionInCountingWithTypeLabels() {
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);
        String l1 = c1.getType().getLabel();
        String l2 = c2.getType().getLabel();

        assertNotEquals(l1, l2);
        HashSet<String> typeLabels = new HashSet<String>(1);
        typeLabels.add(l1);
        typeLabels.add(l2);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(2, count);
    }

    @Test
    public void countShouldReturnZeroWhenUnknownLabel() throws Exception {
        createConsumer(owner);
        Set<String> labels = new HashSet<String>();
        labels.add("unknown-label");

        int count = consumerCurator.countConsumers(owner.getKey(), labels,
            skus, subscriptionIds, contracts);

        assertEquals(0, count);
    }

    // select by SKUs
    @Test
    public void countWithSkuShouldWorkOnlyWithMarketingProducts() {
        Consumer c = createConsumer(owner);
        Product mktProduct = createMktProductAndBindItToConsumer(owner, c);
        Product notMktproduct = createProductAndBindItToConsumer(owner, c);
        List<String> skus = new ArrayList<String>();
        skus.add(notMktproduct.getId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(0, count);

        skus.clear();
        skus.add(mktProduct.getId());

        count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void shouldCountConsumersOnlyWithSkus() {
        Product p1 = createConsumerWithBindingToMKTProduct(owner);
        Product p2 = createConsumerWithBindingToMKTProduct(owner);

        assertNotEquals(p1.getId(), p2.getId());

        List<String> skus = new ArrayList<String>();
        skus.add(p1.getId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void testDisjunctionInCountingWithSkus() {
        Product p1 = createConsumerWithBindingToMKTProduct(owner);
        Product p2 = createConsumerWithBindingToMKTProduct(owner);
        List<String> skus = new ArrayList<String>();
        skus.add(p1.getId());
        skus.add(p2.getId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(2, count);
    }

    @Test
    public void countConsumerOnlyOnceIfMoreSkusMatchToSameConsumer() {
        Consumer c = createConsumer(owner);
        Product p1 = createMktProductAndBindItToConsumer(owner, c);
        Product p2 = createMktProductAndBindItToConsumer(owner, c);
        assertNotEquals(p1.getId(), p2.getId());

        List<String> skus = new ArrayList<String>();
        skus.add(p1.getId());
        skus.add(p2.getId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldReturnZeroIfUnknownSku() throws Exception {
        createConsumer(owner);
        List<String> skus = new ArrayList<String>();
        skus.add("unknown-sku");

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(0, count);
    }

    // select by subscriptionIds
    @Test
    public void shouldCountConsumersOnlyWithSubscriptionIds() throws Exception {
        Product product = createProduct(owner);
        Pool p1 = createConsumerWithBindingToProduct(owner, product);
        Pool p2 = createConsumerWithBindingToProduct(owner, product);
        assertNotEquals(p1.getSubscriptionId(), p2.getSubscriptionId());

        List<String> ids = new ArrayList<String>();
        ids.add(p1.getSubscriptionId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, ids, contracts);

        assertEquals(1, count);
    }

    @Test
    public void testDisjunctionInCountingWithSubscriptionIds() throws Exception {
        Product product = createProduct(owner);
        Pool p1 = createConsumerWithBindingToProduct(owner, product);
        Pool p2 = createConsumerWithBindingToProduct(owner, product);
        assertNotEquals(p1.getSubscriptionId(), p2.getSubscriptionId());

        List<String> ids = new ArrayList<String>();
        ids.add(p1.getSubscriptionId());
        ids.add(p2.getSubscriptionId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, ids, contracts);

        assertEquals(2, count);
    }

    @Test
    public void countConsumerOnlyOnceIfMoreSubIdsMatchToSameConsumer() throws Exception {
        Consumer c = createConsumer(owner);
        Pool p1 = createPoolAndBindItToConsumer(owner, c);
        Pool p2 = createPoolAndBindItToConsumer(owner, c);
        assertNotEquals(p1.getSubscriptionId(), p2.getSubscriptionId());

        List<String> ids = new ArrayList<String>();
        ids.add(p1.getSubscriptionId());
        ids.add(p2.getSubscriptionId());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, ids, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldReturnZeroIfUnknownSubscriptionId() throws Exception {
        createConsumer(owner);
        List<String> ids = new ArrayList<String>();
        ids.add("unknown-subId");

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, ids, contracts);

        assertEquals(0, count);
    }

    // select by contract number
    @Test
    public void shouldCountConsumersOnlyWithContractNums() throws Exception {
        Product product = createProduct(owner);
        Pool p1 = createConsumerWithBindingToProduct(owner, product, "contract-1");
        createConsumerAndEntitlement(owner, p1);
        createConsumerWithBindingToProduct(owner, product, "contract-2");
        List<String> c = new ArrayList<String>();
        c.add(p1.getContractNumber());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, c);

        assertEquals(2, count);
    }

    @Test
    public void testDisjunctionInCountingWithContractNums() throws Exception {
        Product product = createProduct(owner);
        Pool p1 = createConsumerWithBindingToProduct(owner, product, "contract-1");
        Pool p2 = createConsumerWithBindingToProduct(owner, product, "contract-2");
        List<String> c = new ArrayList<String>();
        c.add(p1.getContractNumber());
        c.add(p2.getContractNumber());

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, c);

        assertEquals(2, count);
    }

    @Test
    public void countConsumerOnlyOnceIfMoreContractsMatchToSameConsumer() throws Exception {
        Consumer c = createConsumer(owner);
        String contr1 = "contract-1";
        String contr2 = "contract-2";
        createProductAndBindItToConsumer(owner, c, contr1);
        createProductAndBindItToConsumer(owner, c, contr2);
        List<String> contracts = new ArrayList<String>();
        contracts.add(contr1);
        contracts.add(contr2);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldReturnZeroIfUnknownContractNumber() throws Exception {
        createConsumer(owner);
        List<String> c = new ArrayList<String>();
        c.add("unknown-contract");

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, c);

        assertEquals(0, count);
    }

    @Test
    public void countShouldUseConjuctionBetweenAllParameters() throws Exception {
        String sku = "sku";
        String subscriptionId = "subscriptionId";
        String poolContract = "contract";

        Product skuProduct = createTransientMarketingProduct(sku);
        skuProduct = createProduct(skuProduct, owner);

        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumerAndBindItToProduct(owner, skuProduct);
        Consumer c3 = createConsumerAndBindItToProduct(owner, skuProduct, subscriptionId);
        Consumer c4 = createConsumerAndBindItToProduct(owner, skuProduct, subscriptionId, poolContract);

        Set<String> labels = new HashSet<String>();
        List<String> skus = new ArrayList<String>();
        List<String> subIds = new ArrayList<String>();
        List<String> contracts = new ArrayList<String>();
        labels.add(c1.getType().getLabel());
        labels.add(c2.getType().getLabel());
        labels.add(c3.getType().getLabel());
        labels.add(c4.getType().getLabel());
        skus.add(sku);
        subIds.add(subscriptionId);
        contracts.add(poolContract);

        int count = consumerCurator.countConsumers(owner.getKey(), typeLabels, skus,
            subIds, contracts);

        assertEquals(1, count);
    }

    @Test
    public void countShouldBeIdempotent() throws Exception {
        int n = 5;
        int expectedCount = 0;
        countConsumersAndAssertResultManyTimes(n, expectedCount);

        for (int i = 0; i < n; i++) {
            createConsumer(owner);
        }

        expectedCount = n;
        countConsumersAndAssertResultManyTimes(n, expectedCount);
    }

    private void countConsumersAndAssertResultManyTimes(int many, int expectedCount) {
        for (int i = 0; i < many; i++) {
            int count = consumerCurator.countConsumers(owner.getKey(), typeLabels, skus,
                subscriptionIds, contracts);
            assertEquals(expectedCount, count);
        }
    }

}
