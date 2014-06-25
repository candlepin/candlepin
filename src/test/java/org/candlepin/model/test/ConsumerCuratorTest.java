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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.HypervisorId;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

/**
 * ConsumerCuratorTest JUnit tests for Consumer database code
 */
public class ConsumerCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;
    private Consumer factConsumer;

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

        factConsumer = new Consumer("a consumer", "username", owner, ct);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);

        List<Product> results = entityManager().createQuery(
                "select c from Consumer as c").getResultList();
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
        gConsumer1.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer1);

        // This can happen so fast the consumers end up with the same update time,
        // it's 5 milliseconds, deal with it.
        Thread.sleep(5);

        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer2);

        GuestId hGuest1 = new GuestId("test-guest-1");
        hConsumer1.addGuestId(hGuest1);
        consumerCurator.update(hConsumer1);

        // Uppercase the guest ID reported by host 2 just to make sure the casing is
        // working properly here too:
        GuestId hGuest2 = new GuestId("TEST-GUEST-1");
        hConsumer2.addGuestId(hGuest2);
        consumerCurator.update(hConsumer2);

        List<Consumer> guests1 = consumerCurator.getGuests(hConsumer1);
        List<Consumer> guests2 = consumerCurator.getGuests(hConsumer2);
        assertTrue(hGuest1.getUpdated().before(hGuest2.getUpdated()));
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

        Consumer guestHost = consumerCurator.getHost(
            "daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertEquals(host, guestHost);
    }

    @Test
    public void twoHostsRegisteredPickSecond() {
        Consumer host1 = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host1);

        Consumer host2 = new Consumer("hostConsumer2", "testUser2", owner, ct);
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        GuestId host1Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host1.addGuestId(host1Guest);
        consumerCurator.update(host1);

        GuestId host2Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host2.addGuestId(host2Guest);
        consumerCurator.update(host2);

        Consumer guestHost = consumerCurator.getHost(
            "daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertTrue(host1Guest.getUpdated().before(host2Guest.getUpdated()));
        assertEquals(host2.getUuid(), guestHost.getUuid());
    }

    @Test
    public void twoHostsRegisteredPickFirst() {
        Consumer host1 = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(host1);

        Consumer host2 = new Consumer("hostConsumer2", "testUser2", owner, ct);
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        GuestId host2Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host2.addGuestId(host2Guest);
        consumerCurator.update(host2);

        GuestId host1Guest = new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8");
        host1.addGuestId(host1Guest);
        consumerCurator.update(host1);

        Consumer guestHost = consumerCurator.getHost(
            "daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner);
        assertTrue(host1Guest.getUpdated().after(host2Guest.getUpdated()));
        assertEquals(host1.getUuid(), guestHost.getUuid());
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

        assertEquals(consumer.getLastCheckin(), dt);
    }
    @Test
    public void updatelastCheckin() throws Exception {
        Date date = new Date();
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumer.setLastCheckin(date);
        Thread.sleep(5); // sleep for at 5ms to allow enough time to pass
        consumer = consumerCurator.create(consumer);
        consumerCurator.updateLastCheckin(consumer);
        assertTrue(consumer.getLastCheckin().after(date));
    }

    @Test
    public void delete() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);
        String cid = consumer.getUuid();

        consumerCurator.delete(consumer);
        DeletedConsumerCurator dcc = injector.getInstance(DeletedConsumerCurator.class);
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
        DeletedConsumerCurator dcc = injector.getInstance(DeletedConsumerCurator.class);
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

    @Test
    public void testConsumerFactsVerifyBadInt() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "zzz");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(factConsumer.getFact("system.count"), null);
        assertEquals(factConsumer.getFact("system.multiplier"), "-2");
    }

    @Test
    public void testConsumerFactsVerifyBadPositive() {
        Map<String, String> facts = new HashMap<String, String>();
        facts.put("system.count", "-2");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(factConsumer.getFact("system.count"), null);
        assertEquals(factConsumer.getFact("system.multiplier"), "-2");
    }

    @Test
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
        assertEquals(factConsumer.getFact("system.count"), null);
        assertEquals(factConsumer.getFact("system.multiplier"), "-2");
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

        List<Consumer> results = consumerCurator.findByUuids(
            Arrays.asList(new String[] {"1", "2"}));
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

        List<Consumer> results = consumerCurator.findByUuidsAndOwner(
            Arrays.asList(new String[] {"2"}), owner2);
        assertTrue(results.contains(consumer2));
        assertFalse(results.contains(consumer));
        assertFalse(results.contains(consumer3));
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
    public void testGetHypervisorsBulk() {
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        List<String> hypervisorIds = new LinkedList<String>();
        hypervisorIds.add(hypervisorid);
        hypervisorIds.add("not really a hypervisor");
        List<Consumer> results = consumerCurator.getHypervisorsBulk(
            hypervisorIds, owner.getKey());
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testGetHypervisorsBulkEmpty() {
        String hypervisorid = "hypervisor";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId(hypervisorid));
        consumer = consumerCurator.create(consumer);
        List<Consumer> results = consumerCurator.getHypervisorsBulk(
            new LinkedList<String>(), owner.getKey());
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
        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getKey());
        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testisHypervisorIdUsed() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId("hypervisor"));
        consumer = consumerCurator.create(consumer);
        Owner otherOwner = ownerCurator.create(new Owner("other owner"));
        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", otherOwner, ct);
        consumer2.setHypervisorId(new HypervisorId("hypervisor"));
        consumer2 = consumerCurator.create(consumer2);

        // Also test the lookup is case insensitive
        boolean result = consumerCurator.isHypervisorIdUsed("hyperVIsor");
        assertTrue(result);
    }


    @Test
    public void testisHypervisorIdUsedNoMatches() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setHypervisorId(new HypervisorId("hypervisor"));
        consumer = consumerCurator.create(consumer);
        Owner otherOwner = ownerCurator.create(new Owner("other owner"));
        Consumer consumer2 = new Consumer("testConsumer2", "testUser2", otherOwner, ct);
        consumer2.setHypervisorId(new HypervisorId("hypervisor"));
        consumer2 = consumerCurator.create(consumer2);

        // Should return zero
        boolean result = consumerCurator.isHypervisorIdUsed("different id");
        assertFalse(result);
    }
}
