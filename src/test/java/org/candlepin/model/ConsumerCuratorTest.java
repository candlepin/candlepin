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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.tasks.InactiveConsumerCleanerJob;
import org.candlepin.auth.Access;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.ConsumerPermission;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;
import org.candlepin.util.PropertyValidationException;
import org.candlepin.util.Util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

public class ConsumerCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType ct;
    private Consumer factConsumer;

    @BeforeEach
    public void setUp() throws Exception {
        this.owner = this.createOwner("test-owner", "Test Owner");

        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        config.setProperty(ConfigProperties.INTEGER_FACTS, "system.count, system.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_FACTS, "system.count");

        // Inject this factValidator into the curator
        Field field = ConsumerCurator.class.getDeclaredField("factValidator");
        field.setAccessible(true);
        field.set(this.consumerCurator, new FactValidator(this.config, this.i18nProvider));

        factConsumer = new Consumer()
            .setName("a consumer")
            .setUsername("username")
            .setOwner(owner)
            .setType(ct);
    }

    private List<Consumer> getConsumersDirect() {
        return this.getEntityManager()
            .createQuery("select c from Consumer as c", Consumer.class)
            .getResultList();
    }

    @Test
    public void normalCreate() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);

        List<Consumer> results = this.getConsumersDirect();

        assertEquals(1, results.size());
    }

    @Test
    public void testGetConsumersNoConsumers() {
        List<Consumer> expected = this.getConsumersDirect();
        assertTrue(expected.isEmpty());

        List<String> cids = Arrays.asList("c1", "c2", "c3");
        Collection<Consumer> actual = consumerCurator.getConsumers(cids);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testGetConsumersThreeConsumers() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<Consumer> expected = this.getConsumersDirect();
        assertEquals(3, expected.size());
        assertTrue(expected.contains(c1));
        assertTrue(expected.contains(c2));
        assertTrue(expected.contains(c3));

        List<String> cids = Arrays.asList(c1.getId(), c2.getId(), c3.getId());
        Collection<Consumer> actual = consumerCurator.getConsumers(cids);
        assertEquals(3, actual.size());
        assertTrue(actual.contains(c1));
        assertTrue(actual.contains(c2));
        assertTrue(actual.contains(c3));
    }

    @Test
    public void testGetConsumersFetchThreeOfFiveConsumers() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c4 = new Consumer()
            .setName("c4")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c5 = new Consumer()
            .setName("c5")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.create(c4);
        consumerCurator.create(c5);
        consumerCurator.flush();

        List<Consumer> expected = this.getConsumersDirect();
        assertEquals(5, expected.size());
        assertTrue(expected.contains(c1));
        assertTrue(expected.contains(c2));
        assertTrue(expected.contains(c3));
        assertTrue(expected.contains(c4));
        assertTrue(expected.contains(c5));

        List<String> cids = Arrays.asList(c1.getId(), c3.getId(), c5.getId());
        Collection<Consumer> actual = consumerCurator.getConsumers(cids);
        assertEquals(3, actual.size());
        assertTrue(actual.contains(c1));
        assertTrue(actual.contains(c3));
        assertTrue(actual.contains(c5));
        assertFalse(actual.contains(c2));
        assertFalse(actual.contains(c4));
    }

    @Test
    public void testGetConsumersFetchFewerThanRequested() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<Consumer> expected = this.getConsumersDirect();
        assertEquals(3, expected.size());
        assertTrue(expected.contains(c1));
        assertTrue(expected.contains(c2));
        assertTrue(expected.contains(c3));

        List<String> cids = Arrays.asList(c1.getId(), c2.getId(), c3.getId(), "c4", "c5");
        Collection<Consumer> actual = consumerCurator.getConsumers(cids);
        assertEquals(3, actual.size());
        assertTrue(actual.contains(c1));
        assertTrue(actual.contains(c2));
        assertTrue(actual.contains(c3));
    }

    @Test
    public void testGetConsumersNullInput() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<Consumer> expected = this.getConsumersDirect();
        assertEquals(3, expected.size());
        assertTrue(expected.contains(c1));
        assertTrue(expected.contains(c2));
        assertTrue(expected.contains(c3));

        Collection<Consumer> actual = consumerCurator.getConsumers(null);
        assertEquals(0, actual.size());
    }

    @Test
    public void testGetConsumerInputPartitioning() {
        int partitionBlockSize = 5;

        ConsumerCurator curator = Mockito.spy(consumerCurator);
        when(curator.getInBlockSize()).thenReturn(partitionBlockSize);

        Map<String, Consumer> consumers = new HashMap<>();

        // Create a pile of consumers
        for (int i = 0; i < partitionBlockSize * 5; ++i) {
            Consumer consumer = new Consumer()
                .setUuid("uuid-" + i)
                .setName("consumer-" + i)
                .setUsername("user-" + i)
                .setOwner(owner)
                .setType(ct);

            curator.create(consumer);
            consumers.put(consumer.getUuid(), consumer);
        }

        curator.flush();

        Set<String> input = new HashSet<>();
        Set<Consumer> expected = new HashSet<>();
        for (int i = 0; i < partitionBlockSize * 10; ++i) {
            Consumer consumer = consumers.get("uuid-" + i);

            if (consumer != null) {
                expected.add(consumer);
                input.add(consumer.getId());
            }
            else {
                input.add("id-" + i);
            }
        }

        // Verify we can successfully fetch all of the requested consumers
        Collection<Consumer> output = curator.getConsumers(input);

        assertEquals(output.size(), expected.size());

        for (Consumer consumer : expected) {
            assertTrue(output.contains(consumer));
        }
    }

    @Test
    public void testGetConsumersEmptyInput() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<Consumer> expected = this.getConsumersDirect();
        assertEquals(3, expected.size());
        assertTrue(expected.contains(c1));
        assertTrue(expected.contains(c2));
        assertTrue(expected.contains(c3));

        Collection<Consumer> actual = consumerCurator.getConsumers(new LinkedList<>());
        assertEquals(0, actual.size());
    }

    @Test
    public void testGetDistinctSyspurposeRolesByOwnerReturnsAllRoles() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("role1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("role2");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("common_role");
        Consumer c4 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("common_role");

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.create(c4);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.ROLES);

        // Make sure 'common_role' is not duplicated in the result
        assertEquals(3, result.size());
        assertTrue(result.contains("role1"));
        assertTrue(result.contains("role2"));
        assertTrue(result.contains("common_role"));
    }

    @Test
    public void testGetDistinctSyspurposeRolesByOwnerDoesNotReturnNullOrEmpty() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("role1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole("");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setRole(null);

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.ROLES);

        // Make sure the result does not contain null or empty roles.
        assertEquals(1, result.size());
        assertTrue(result.contains("role1"));
        assertFalse(result.contains(""));
        assertFalse(result.contains(null));
    }

    @Test
    public void testGetDistinctSyspurposeUsageByOwnerReturnsAllUsages() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("usage1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("usage2");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("common_usage");
        Consumer c4 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("common_usage");

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.create(c4);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.USAGE);

        // Make sure 'common_usage' is not duplicated in the result
        assertEquals(3, result.size());
        assertTrue(result.contains("usage1"));
        assertTrue(result.contains("usage2"));
        assertTrue(result.contains("common_usage"));
    }

    @Test
    public void testGetDistinctSyspurposeUsageByOwnerDoesNotReturnNullOrEmpty() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("usage1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage("");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setUsage(null);

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.USAGE);

        // Make sure the result does not contain null or empty usages.
        assertEquals(1, result.size());
        assertTrue(result.contains("usage1"));
        assertFalse(result.contains(""));
        assertFalse(result.contains(null));
    }

    @Test
    public void testGetDistinctSyspurposeServiceLevelByOwnerReturnsAllServiceLevels() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("sla1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("sla2");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("common_sla");
        Consumer c4 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("common_sla");

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.create(c4);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.SERVICE_LEVEL);

        // Make sure 'common_sla' is not duplicated in the result
        assertEquals(3, result.size());
        assertTrue(result.contains("sla1"));
        assertTrue(result.contains("sla2"));
        assertTrue(result.contains("common_sla"));
    }

    @Test
    public void testGetDistinctSyspurposeServiceLevelByOwnerDoesNotReturnNullOrEmpty() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("sla1");
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel("");
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setServiceLevel(null);

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeValuesByOwner(owner,
            SystemPurposeAttributeType.SERVICE_LEVEL);

        // Make sure the result does not contain null or empty SLAs.
        assertEquals(1, result.size());
        assertTrue(result.contains("sla1"));
        assertFalse(result.contains(""));
        assertFalse(result.contains(null));
    }

    @Test
    public void testGetDistinctSyspurposeAddonsByOwnerReturnsAllAddons() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setAddOns(Set.of("addon1"));
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setAddOns(Set.of("addon2", "common_addon"));
        Consumer c3 = new Consumer()
            .setName("c3")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setAddOns(Set.of("addon3", "common_addon"));

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.create(c3);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeAddonsByOwner(owner);

        // Make sure 'common_addon' is not duplicated in the result
        assertEquals(4, result.size());
        assertTrue(result.contains("addon1"));
        assertTrue(result.contains("addon2"));
        assertTrue(result.contains("addon3"));
        assertTrue(result.contains("common_addon"));
    }

    @Test
    public void testGetDistinctSyspurposeAddonsByOwnerDoesNotReturnNullOrEmpty() {
        Consumer c1 = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setAddOns(Set.of("addon1"));
        Consumer c2 = new Consumer()
            .setName("c2")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setAddOns(Arrays.asList("", null));

        consumerCurator.create(c1);
        consumerCurator.create(c2);
        consumerCurator.flush();

        List<String> result = consumerCurator.getDistinctSyspurposeAddonsByOwner(owner);

        // Make sure the result does not contain null or empty addons.
        assertEquals(1, result.size());
        assertTrue(result.contains("addon1"));
        assertFalse(result.contains(""));
        assertFalse(result.contains(null));
    }

    @Test
    public void testGetDistinctSyspurposeAddonsByOwnerReturnEmptyList() {
        List<String> result = consumerCurator.getDistinctSyspurposeAddonsByOwner(null);

        assertThat(result)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void addGuestConsumers() {
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "test-guest-1");
        consumerCurator.create(gConsumer1);
        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "test-guest-2");
        consumerCurator.create(gConsumer2);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertEquals(2, guests.size());
    }

    @Test
    public void addGuestConsumersReversedEndianGuestId() {
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "06F81B41-AAC0-7685-FBE9-79AA4A326511");
        consumerCurator.create(gConsumer1);
        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "4C4C4544-0046-4210-8031-C7C04F445831");
        consumerCurator.create(gConsumer2);
        // Reversed endian, first 3 sections
        consumer.addGuestId(new GuestId("411bf806-c0aa-8576-fbe9-79aa4a326511"));
        // matches a guests facts, case insensitive
        consumer.addGuestId(new GuestId("4c4c4544-0046-4210-8031-c7c04f445831"));
        // Doesn't match a registered guest consumer
        consumer.addGuestId(new GuestId("43e41def-e9ae-4b6b-b8f4-942c8b69a39e"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertEquals(2, guests.size());
    }

    @Test
    public void caseInsensitiveVirtUuidMatching() {
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        List<Consumer> guests = consumerCurator.getGuests(host);
        assertEquals(1, guests.size());
    }

    @Test
    public void caseInsensitiveVirtUuidMatchingDifferentOwners() {
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        owner = new Owner()
            .setKey("test-owner2")
            .setDisplayName("Test Owner2");
        owner = ownerCurator.create(owner);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        List<Consumer> guests = consumerCurator.getGuests(host);
        assertEquals(0, guests.size());
    }

    @Test
    public void addGuestsNotConsumers() {
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertEquals(0, guests.size());
    }

    @Test
    public void getGuestConsumerSharedId() throws Exception {
        Consumer hConsumer1 = new Consumer()
            .setName("hostConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(hConsumer1);
        Consumer hConsumer2 = new Consumer()
            .setName("hostConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(hConsumer2);
        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "shared-guest-id");
        consumerCurator.create(gConsumer1);

        // This can happen so fast the consumers end up with the same created/updated time
        Thread.sleep(500);

        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "shared-guest-id");
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

        assertThat(hGuest1.getUpdated())
            .isNotNull()
            .isBefore(hGuest2.getUpdated());

        assertEquals(0, guests1.size());
        assertEquals(1, guests2.size());
        assertEquals("guestConsumer2", guests2.get(0).getName());
    }

    @Test
    public void noHostRegistered() {
        Consumer host = consumerCurator.getHost("system-uuid-for-guest", owner.getId());
        assertNull(host);
    }

    @Test
    public void oneHostRegistered() {
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        assertEquals(host, guestHost);
    }

    @Test
    public void caseInsensitiveGetHost() {
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost(
            "Daf0fe10-956b-7b4e-b7dc-B383CE681ba8", owner.getId());
        assertEquals(host, guestHost);
    }

    @Test
    public void oneHostRegisteredReverseEndian() {
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = consumerCurator.getHost(
            "10fef0da-6b95-4e7b-b7dc-b383ce681ba8", owner.getId());
        assertEquals(host, guestHost);
    }

    @Test
    public void twoHostsRegisteredPickSecond() throws Exception {
        long base = System.currentTimeMillis() - 10000;

        Consumer host1 = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base));
        consumerCurator.create(host1);

        Consumer host2 = new Consumer()
            .setName("hostConsumer2")
            .setUsername("testUser2")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base + 1000));
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base + 2000))
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
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

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());

        assertThat(host1Guest.getUpdated())
            .isNotNull()
            .isBefore(host2Guest.getUpdated());

        assertEquals(host2.getUuid(), guestHost.getUuid());
    }

    @Test
    public void twoHostsRegisteredPickFirst() throws Exception {
        long base = System.currentTimeMillis() - 10000;

        Consumer host1 = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base));
        consumerCurator.create(host1);

        Consumer host2 = new Consumer()
            .setName("hostConsumer2")
            .setUsername("testUser2")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base + 1000));
        consumerCurator.create(host2);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setCreated(new Date(base + 2000))
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
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

        Consumer guestHost = consumerCurator.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());

        assertThat(host1Guest.getUpdated())
            .isNotNull()
            .isAfter(host2Guest.getUpdated());

        assertEquals(host1.getUuid(), guestHost.getUuid());
    }

    @Test
    public void getHostOnceFromDb() {
        ConsumerCurator spy = Mockito.spy(consumerCurator);
        Consumer host = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(host);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        host.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(host);

        Consumer guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        assertEquals(host, guestHost);
        guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        assertEquals(host, guestHost);
        guestHost = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        assertEquals(host, guestHost);
        verify(spy, times(1)).getEntityManager();
    }

    @Test
    public void getCorrectHostFromCache() {
        ConsumerCurator spy = Mockito.spy(consumerCurator);
        Consumer hostA = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(hostA);
        Consumer hostB = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(hostB);

        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba8");
        consumerCurator.create(gConsumer1);

        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, "daf0fe10-956b-7b4e-b7dc-b383ce681ba9");
        consumerCurator.create(gConsumer2);

        hostA.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA8"));
        consumerCurator.update(hostA);
        hostB.addGuestId(new GuestId("DAF0FE10-956B-7B4E-B7DC-B383CE681BA9"));
        consumerCurator.update(hostB);

        Consumer guestHostA = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        Consumer guestHostB = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba9", owner.getId());
        assertEquals(hostA, guestHostA);
        assertEquals(hostB, guestHostB);
        guestHostA = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba8", owner.getId());
        assertEquals(hostA, guestHostA);
        guestHostB = spy.getHost("daf0fe10-956b-7b4e-b7dc-b383ce681ba9", owner.getId());
        assertEquals(hostB, guestHostB);
        verify(spy, times(2)).getEntityManager();
    }

    @Test
    public void noGuestsRegistered() {
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer = consumerCurator.create(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertEquals(0, guests.size());
    }

    @Test
    public void updateCheckinTime() {
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer = consumerCurator.create(consumer);
        Date dt = Util.yesterday();
        consumerCurator.updateLastCheckin(consumer, dt);
        consumerCurator.refresh(consumer);
        consumer = consumerCurator.get(consumer.getId());

        assertEquals(consumer.getLastCheckin().getTime(), dt.getTime());
    }

    @Test
    public void updateLastCheckIn() throws Exception {
        Date date = new Date();
        Consumer consumer = new Consumer()
            .setName("hostConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer.setLastCheckin(date);
        Thread.sleep(5); // sleep for at 5ms to allow enough time to pass
        consumer = consumerCurator.create(consumer);
        consumerCurator.updateLastCheckin(consumer);
        consumerCurator.refresh(consumer);
        assertTrue(consumer.getLastCheckin().getTime() > date.getTime());
    }

    @Test
    public void delete() {
        Consumer consumer = this.consumerCurator.create(new Consumer()
            .setUuid("test_consumer_uuid")
            .setName("test_consumer")
            .setUsername("test_user")
            .setOwner(owner)
            .setType(ct));

        this.consumerCurator.delete(consumer);

        // We should be able to find a deletion record using the consumer's ID
        assertThat(this.deletedConsumerCurator.findByConsumerId(consumer.getId()))
            .isNotNull()
            .returns(consumer.getId(), DeletedConsumer::getId)
            .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(consumer.getName(), DeletedConsumer::getConsumerName)
            .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId);

        // ...and using their UUID
        assertThat(this.deletedConsumerCurator.findByConsumerUuid(consumer.getUuid()))
            .isNotNull()
            .singleElement()
            .returns(consumer.getId(), DeletedConsumer::getId)
            .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(consumer.getName(), DeletedConsumer::getConsumerName)
            .returns(consumer.getOwner().getId(), DeletedConsumer::getOwnerId);
    }

    @Test
    public void deleteConsumerWithSameUuidTwiceInSameOrg() throws Exception {
        // attempt to create and delete the same consumer uuid twice
        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");

        String uuid = "Doppelganger";

        Consumer consumer1 = this.consumerCurator.create(new Consumer()
            .setUuid(uuid)
            .setName("test_consumer-1")
            .setUsername("test_user")
            .setOwner(owner)
            .setType(this.ct));

        // Delete the consumer, then verify the entry shows up in the deleted consumers table
        this.consumerCurator.delete(consumer1);

        DeletedConsumer deletedConsumer1 = this.deletedConsumerCurator.findByConsumerId(consumer1.getId());
        assertThat(deletedConsumer1)
            .isNotNull()
            .returns(consumer1.getId(), DeletedConsumer::getId)
            .returns(consumer1.getName(), DeletedConsumer::getConsumerName)
            .returns(uuid, DeletedConsumer::getConsumerUuid)
            .returns(owner.getId(), DeletedConsumer::getOwnerId)
            .returns(owner.getKey(), DeletedConsumer::getOwnerKey);

        // Wait a moment to ensure that our second consumer and deletion record have later timestamps.
        Thread.sleep(1000);

        // Create another consumer using the same uuid...
        Consumer consumer2 = this.consumerCurator.create(new Consumer()
            .setUuid(uuid)
            .setName("test_consumer-2")
            .setUsername("test_user")
            .setOwner(owner)
            .setType(this.ct));

        // ...and delete them
        this.consumerCurator.delete(consumer2);

        DeletedConsumer deletedConsumer2 = this.deletedConsumerCurator.findByConsumerId(consumer2.getId());
        assertThat(deletedConsumer2)
            .isNotNull()
            .returns(consumer2.getId(), DeletedConsumer::getId)
            .returns(consumer2.getName(), DeletedConsumer::getConsumerName)
            .returns(uuid, DeletedConsumer::getConsumerUuid)
            .returns(owner.getId(), DeletedConsumer::getOwnerId)
            .returns(owner.getKey(), DeletedConsumer::getOwnerKey);

        assertThat(deletedConsumer1.getCreated()).isBefore(deletedConsumer2.getCreated());
        assertThat(deletedConsumer1.getUpdated()).isBefore(deletedConsumer2.getUpdated());

        // Verify that we have two entries for this uuid, and it's the two we created above.
        List<DeletedConsumer> deletedConsumers = this.deletedConsumerCurator.findByConsumerUuid(uuid);
        assertThat(deletedConsumers)
            .isNotNull()
            .containsExactlyInAnyOrder(deletedConsumer1, deletedConsumer2);
    }

    @Test
    public void deleteConsumerWithSameUuidTwiceInDifferentOrgs() throws Exception {
        // attempt to create and delete the same consumer uuid twice
        Owner owner1 = this.createOwner("test-owner-1", "Test Owner 1");
        Owner owner2 = this.createOwner("test-owner-2", "Test Owner 2");

        String uuid = "Doppelganger";

        Consumer consumer1 = this.consumerCurator.create(new Consumer()
            .setUuid(uuid)
            .setName("test_consumer-1")
            .setUsername("test_user")
            .setOwner(owner1)
            .setType(this.ct));

        // Delete the consumer, then verify the entry shows up in the deleted consumers table
        this.consumerCurator.delete(consumer1);

        DeletedConsumer deletedConsumer1 = this.deletedConsumerCurator.findByConsumerId(consumer1.getId());
        assertThat(deletedConsumer1)
            .isNotNull()
            .returns(consumer1.getId(), DeletedConsumer::getId)
            .returns(consumer1.getName(), DeletedConsumer::getConsumerName)
            .returns(uuid, DeletedConsumer::getConsumerUuid)
            .returns(owner1.getId(), DeletedConsumer::getOwnerId)
            .returns(owner1.getKey(), DeletedConsumer::getOwnerKey);

        // Wait a moment to ensure that our second consumer and deletion record have later timestamps.
        Thread.sleep(1000);

        // Create another consumer using the same uuid, but in a different org...
        Consumer consumer2 = this.consumerCurator.create(new Consumer()
            .setUuid(uuid)
            .setName("test_consumer-2")
            .setUsername("test_user")
            .setOwner(owner2)
            .setType(this.ct));

        // ...and delete them
        this.consumerCurator.delete(consumer2);

        DeletedConsumer deletedConsumer2 = this.deletedConsumerCurator.findByConsumerId(consumer2.getId());
        assertThat(deletedConsumer2)
            .isNotNull()
            .returns(consumer2.getId(), DeletedConsumer::getId)
            .returns(consumer2.getName(), DeletedConsumer::getConsumerName)
            .returns(uuid, DeletedConsumer::getConsumerUuid)
            .returns(owner2.getId(), DeletedConsumer::getOwnerId)
            .returns(owner2.getKey(), DeletedConsumer::getOwnerKey);

        assertThat(deletedConsumer1.getCreated()).isBefore(deletedConsumer2.getCreated());
        assertThat(deletedConsumer1.getUpdated()).isBefore(deletedConsumer2.getUpdated());

        // Verify that we have two entries for this uuid, and it's the two we created above.
        List<DeletedConsumer> deletedConsumers = this.deletedConsumerCurator.findByConsumerUuid(uuid);
        assertThat(deletedConsumers)
            .isNotNull()
            .containsExactlyInAnyOrder(deletedConsumer1, deletedConsumer2);
    }

    @Test
    public void testDeleteRetainsPrincipalName() {
        String principalName = "test_principal_name";
        this.setupAdminPrincipal(principalName);

        Consumer consumer = new Consumer()
            .setName("test_consumer")
            .setUsername("test_user")
            .setOwner(this.owner)
            .setType(this.ct);
        consumer.setUuid("test_consumer_uuid");
        consumer = this.consumerCurator.create(consumer);

        this.consumerCurator.delete(consumer);

        Consumer fetched = this.consumerCurator.get(consumer.getUuid());
        assertNull(fetched);

        DeletedConsumer deletionRecord = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
        assertNotNull(deletionRecord);
        assertEquals(principalName, deletionRecord.getPrincipalName());
    }

    @Test
    public void deletionRecordsSelfHealWhenDeletingTheSameConsumerTwice() {
        // This test verifies the self-healing nature of an explicit delete op. Note that this is different
        // than attempting to create two DeletedConsumer entries directly, which we would expect to fail with
        // a pkey collision.

        Owner owner = this.createOwner("test-owner-1", "Test Owner 1");

        String initName = "test_consumer";
        String updatedName = "updated_consumer";

        Consumer consumer = this.consumerCurator.create(new Consumer()
            .setName(updatedName)
            .setOwner(owner)
            .setType(this.ct));

        // Simulate some date at some point in the past. We don't really care how far back.
        Date created = new Date(System.currentTimeMillis() - 25000);

        // Simulate a pre-existing deletion of this consumer by creating a fake consumer deletion record
        // manually
        this.deletedConsumerCurator.create(new DeletedConsumer()
            .setCreated(created)
            .setUpdated(created)
            .setId(consumer.getId())
            .setConsumerUuid(consumer.getUuid())
            .setConsumerName(initName)
            .setOwnerId(owner.getId())
            .setOwnerKey(owner.getKey())
            .setOwnerDisplayName(owner.getDisplayName()));

        // Verify it exists by querying + validating it
        DeletedConsumer deletedConsumer1 = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
        assertThat(deletedConsumer1)
            .isNotNull()
            .returns(consumer.getId(), DeletedConsumer::getId)
            .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(initName, DeletedConsumer::getConsumerName)
            .returns(owner.getId(), DeletedConsumer::getOwnerId)
            .returns(owner.getKey(), DeletedConsumer::getOwnerKey);

        // Detach this entity to ensure Hibernate doesn't try to get too helpful
        this.deletedConsumerCurator.detach(deletedConsumer1);

        // Delete the consumer. This should result in a warning (untestable) and an update to the existing
        // deletion record
        this.consumerCurator.delete(consumer);
        DeletedConsumer deletedConsumer2 = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
        assertThat(deletedConsumer2)
            .isNotNull()
            .returns(consumer.getId(), DeletedConsumer::getId)
            .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(updatedName, DeletedConsumer::getConsumerName)
            .returns(owner.getId(), DeletedConsumer::getOwnerId)
            .returns(owner.getKey(), DeletedConsumer::getOwnerKey);

        // Impl note: assertj does some very unnecessary subclass checking on .returns, .extracting, and date
        // assertions in general. For whatever reason, doing these tests in assertj will trigger assertion
        // errors if either of our date checks end up with a subclass of date, like java.sql.timestamp. :/
        assertFalse(deletedConsumer2.getCreated().after(deletedConsumer1.getCreated()));
        assertTrue(deletedConsumer2.getUpdated().after(deletedConsumer1.getUpdated()));

        // We should only have one record when fetching by uuid
        List<DeletedConsumer> deletionRecords = this.deletedConsumerCurator
            .findByConsumerUuid(consumer.getUuid());

        assertThat(deletionRecords)
            .isNotNull()
            .singleElement()
            .returns(consumer.getId(), DeletedConsumer::getId);
    }

    @Test
    public void testConsumerFactsVerifySuccess() {
        Map<String, String> facts = new HashMap<>();
        facts.put("system.count", "3");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);

        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
        assertEquals("3", factConsumer.getFact("system.count"));
        assertEquals("-2", factConsumer.getFact("system.multiplier"));
    }

    @Test
    public void testConsumerFactsVerifyBadInt() {
        Map<String, String> facts = new HashMap<>();
        facts.put("system.count", "zzz");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        assertThrows(PropertyValidationException.class, () -> consumerCurator.create(factConsumer));
    }

    @Test
    public void testConsumerFactsVerifyBadPositive() {
        Map<String, String> facts = new HashMap<>();
        facts.put("system.count", "-2");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        assertThrows(PropertyValidationException.class, () -> consumerCurator.create(factConsumer));
    }

    @Test
    public void testConsumerFactsVerifyBadUpdateValue() {
        Map<String, String> facts = new HashMap<>();
        facts.put("system.count", "3");
        facts.put("system.multiplier", "-2");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
        assertEquals("3", factConsumer.getFact("system.count"));
        assertEquals("-2", factConsumer.getFact("system.multiplier"));

        factConsumer.setFact("system.count", "sss");
        assertThrows(PropertyValidationException.class, () -> consumerCurator.update(factConsumer));
    }

    @Test
    public void testSubstringConfigList() {
        Map<String, String> facts = new HashMap<>();
        facts.put("system.cou", "this should not be checked");

        factConsumer.setFacts(facts);
        factConsumer = consumerCurator.create(factConsumer);
        assertEquals(consumerCurator.findByUuid(factConsumer.getUuid()), factConsumer);
    }

    @Test
    public void testFindByUuids() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Consumer consumer2 = new Consumer()
            .setName("testConsumer2")
            .setUsername("testUser2")
            .setOwner(owner)
            .setType(ct);
        consumer2.setUuid("2");
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer()
            .setName("testConsumer3")
            .setUsername("testUser3")
            .setOwner(owner)
            .setType(ct);
        consumer3.setUuid("3");
        consumer3 = consumerCurator.create(consumer3);

        Collection<Consumer> results = consumerCurator.findByUuids(Arrays.asList("1", "2"));
        assertTrue(results.contains(consumer));
        assertTrue(results.contains(consumer2));
        assertFalse(results.contains(consumer3));
    }

    @Test
    public void testFindByUuidsInputPartioning() {
        int partitionBlockSize = 5;

        ConsumerCurator curator = Mockito.spy(consumerCurator);
        when(curator.getInBlockSize()).thenReturn(partitionBlockSize);

        Map<String, Consumer> consumers = new HashMap<>();

        // Create a pile of consumers
        for (int i = 0; i < partitionBlockSize * 5; ++i) {
            Consumer consumer = new Consumer()
                .setUuid("uuid-" + i)
                .setName("consumer-" + i)
                .setUsername("user-" + i)
                .setOwner(owner)
                .setType(ct);

            curator.create(consumer);

            consumers.put(consumer.getUuid(), consumer);
        }

        curator.flush();

        Set<String> input = new HashSet<>();
        Set<Consumer> expected = new HashSet<>();
        for (int i = 0; i < partitionBlockSize * 3 + 1; ++i) {
            Consumer consumer = consumers.get("uuid-" + i);

            input.add(consumer.getUuid());
            expected.add(consumer);
        }

        // Verify we can successfully fetch all of the requested consumers
        Collection<Consumer> output = curator.findByUuids(input);

        assertEquals(output.size(), expected.size());

        for (Consumer consumer : expected) {
            assertTrue(output.contains(consumer));
        }
    }

    @Test
    public void testFindByUuidsAndOwner() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer.setUuid("1");
        consumer = consumerCurator.create(consumer);

        Owner owner2 = new Owner()
            .setKey("test-owner2")
            .setDisplayName("Test Owner2");
        ownerCurator.create(owner2);

        Consumer consumer2 = new Consumer()
            .setName("testConsumer2")
            .setUsername("testUser2")
            .setOwner(owner2)
            .setType(ct);
        consumer2.setUuid("2");
        consumer2 = consumerCurator.create(consumer2);

        Consumer consumer3 = new Consumer()
            .setName("testConsumer3")
            .setUsername("testUser3")
            .setOwner(owner2)
            .setType(ct);
        consumer3.setUuid("3");
        consumer3 = consumerCurator.create(consumer3);

        Collection<Consumer> results = consumerCurator
            .findByUuidsAndOwner(Arrays.asList("2"), owner2.getId());
        assertTrue(results.contains(consumer2));
        assertFalse(results.contains(consumer));
        assertFalse(results.contains(consumer3));
    }

    @Test
    public void testFindByUuidsAndOwnerInputPartioning() {
        int partitionBlockSize = 5;

        ConsumerCurator curator = Mockito.spy(consumerCurator);
        when(curator.getInBlockSize()).thenReturn(partitionBlockSize);

        Map<String, Consumer> consumers = new HashMap<>();

        // Create a pile of consumers
        for (int i = 0; i < partitionBlockSize * 5; ++i) {
            Consumer consumer = new Consumer()
                .setUuid("uuid-" + i)
                .setName("consumer-" + i)
                .setUsername("user-" + i)
                .setOwner(owner)
                .setType(ct);

            curator.create(consumer);

            consumers.put(consumer.getUuid(), consumer);
        }

        curator.flush();

        Set<String> input = new HashSet<>();
        Set<Consumer> expected = new HashSet<>();
        for (int i = 0; i < partitionBlockSize * 3 + 1; ++i) {
            Consumer consumer = consumers.get("uuid-" + i);

            input.add(consumer.getUuid());
            expected.add(consumer);
        }

        // Verify we can successfully fetch all of the requested consumers
        Collection<Consumer> output = curator.findByUuidsAndOwner(input, owner.getId());

        assertEquals(output.size(), expected.size());

        for (Consumer consumer : expected) {
            assertTrue(output.contains(consumer));
        }
    }

    @Test
    public void getGuestConsumerMap() {
        String guestId1 = "06F81B41-AAC0-7685-FBE9-79AA4A326511";
        String guestId1ReverseEndian = "411bf806-c0aa-8576-fbe9-79aa4a326511";
        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, guestId1);
        consumerCurator.create(gConsumer1);

        String guestId2 = "4C4C4544-0046-4210-8031-C7C04F445831";
        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, guestId2);
        consumerCurator.create(gConsumer2);

        Set<String> guestIds = new HashSet<>();
        guestIds.add(guestId1ReverseEndian); // reversed endian match
        guestIds.add(guestId2); // direct match
        VirtConsumerMap guestMap = consumerCurator.getGuestConsumersMap(owner.getId(), guestIds);

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
        Consumer gConsumer1 = new Consumer()
            .setName("guestConsumer1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, guestId1);
        consumerCurator.create(gConsumer1);

        String guestId2 = "4c4c4544-0046-4210-8031-C7C04F445831";
        Consumer gConsumer2 = new Consumer()
            .setName("guestConsumer2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setFact(Consumer.Facts.VIRT_UUID, guestId2);
        consumerCurator.create(gConsumer2);

        Set<String> guestIds = new HashSet<>();
        guestIds.add(guestId1ReverseEndian.toUpperCase()); // reversed endian match
        guestIds.add(guestId2.toUpperCase()); // direct match
        VirtConsumerMap guestMap = consumerCurator.getGuestConsumersMap(owner.getId(), guestIds);

        assertEquals(2, guestMap.size());

        assertEquals(gConsumer1.getId(), guestMap.get(guestId1.toLowerCase()).getId());
        assertEquals(gConsumer1.getId(), guestMap.get(guestId1ReverseEndian).getId());

        assertEquals(gConsumer2.getId(), guestMap.get(guestId2.toLowerCase()).getId());
        assertEquals(gConsumer2.getId(), guestMap.get(Util.transformUuid(guestId2.toLowerCase())).getId());
    }

    @Test
    public void testDoesConsumerExistNo() {
        Consumer consumer = new Consumer()
            .setUuid("1")
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        boolean result = consumerCurator.doesConsumerExist("unknown");
        assertFalse(result);
    }

    @Test
    public void testDoesConsumerExistYes() {
        Consumer consumer = new Consumer()
            .setUuid("1")
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        boolean result = consumerCurator.doesConsumerExist("1");
        assertTrue(result);
    }

    @Test
    public void testFindByUuid() {
        Consumer consumer = new Consumer()
            .setUuid("1")
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
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
        Consumer consumer = new Consumer()
            .setUuid("1")
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer = consumerCurator.create(consumer);

        Consumer result = consumerCurator.verifyAndLookupConsumer("1");
        assertEquals(result, consumer);
    }

    @Test
    public void testVerifyAndLookupConsumerDoesntMatch() {
        assertThrows(NotFoundException.class, () -> consumerCurator.verifyAndLookupConsumer("1"));
    }

    @Test
    public void testExistingConsumerUuidsNullCheck() {
        Set<String> existingIds = consumerCurator.getExistingConsumerUuids(null);
        assertTrue(existingIds.isEmpty());
    }

    @Test
    public void testExistingConsumerUuidsWhenIdsExists() {
        Consumer consumer = new Consumer()
            .setUuid("fooBarPlus")
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer = consumerCurator.create(consumer);
        List<String> ids = Arrays.asList("fooBarPlus", "testId1", "testId2");
        Set<String> existingIds = consumerCurator.getExistingConsumerUuids(ids);
        assertIterableEquals(Arrays.asList("fooBarPlus"), existingIds);
    }

    @Test
    void testExistingConsumerUuidsWhenIdsNotExist() {
        Set<String> existingId = consumerCurator.getExistingConsumerUuids(Arrays
            .asList("thisIdNotExists", "anotherNonExistingId"));
        assertTrue(existingId.isEmpty());
    }

    @Test
    public void testGetHypervisor() {
        Consumer consumer = createHypervisor(owner);

        Consumer result = consumerCurator.getHypervisor(
            consumer.getHypervisorId().getHypervisorId(), owner.getId());

        assertEquals(consumer, result);
    }

    @Test
    public void testGetHypervisorCaseInsensitive() {
        Consumer consumer = createHypervisor(owner, "HYpervisor");

        Consumer result = consumerCurator.getHypervisor(
            consumer.getHypervisorId().getHypervisorId(), owner.getId());

        assertEquals(consumer, result);
    }

    @Test
    public void testGetHypervisorWrongOwner() {
        Owner otherOwner = new Owner()
            .setKey("test-owner-other")
            .setDisplayName("Test Other Owner");
        otherOwner = ownerCurator.create(otherOwner);
        Consumer consumer = createHypervisor(owner);

        Consumer result = consumerCurator.getHypervisor(
            consumer.getHypervisorId().getHypervisorId(), otherOwner.getId());

        assertNull(result);
    }

    @Test
    public void testCountHypervisorsBulk() {
        Consumer hypervisor1 = createHypervisor(owner);
        Consumer hypervisor2 = createHypervisor(owner);
        Consumer hypervisor3 = createHypervisor(owner);

        long count = consumerCurator.countHypervisorsBulk(owner.getId(), List.of(
            hypervisor1.getHypervisorId().getHypervisorId(),
            hypervisor3.getHypervisorId().getHypervisorId()
        ));

        assertEquals(2, count);
    }

    @Test
    public void testGetHypervisorsBulk() {
        Consumer consumer = createHypervisor(owner);
        List<String> hypervisorIds = List.of(
            consumer.getHypervisorId().getHypervisorId(), "not really a hypervisor"
        );

        List<Consumer> results = consumerCurator.getHypervisorsBulk(owner.getId(), hypervisorIds);

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testGetHypervisorsBulkCaseInsensitive() {
        Consumer consumer = createHypervisor(owner, "hYPervisor");
        List<String> hypervisorIds = List.of(
            consumer.getHypervisorId().getHypervisorId(), "NOT really a hypervisor"
        );

        List<Consumer> results = consumerCurator.getHypervisorsBulk(owner.getId(), hypervisorIds);

        assertEquals(1, results.size());
        assertEquals(consumer, results.get(0));
    }

    @Test
    public void testGetHypervisorsBulkEmpty() {
        Consumer consumer = createHypervisor(owner);

        List<Consumer> results = consumerCurator
            .getHypervisorsBulk(owner.getId(), new LinkedList<>());

        assertEquals(0, results.size());
    }

    @Test
    public void testGetHypervisorsByOwner() {
        Owner otherOwner = this.createOwner("other owner");
        Consumer hypervisor1 = createHypervisor(owner);
        Consumer hypervisor2 = createHypervisor(otherOwner);

        Consumer nonHypervisor = new Consumer()
            .setName("testConsumer3")
            .setUsername("testUser3")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(nonHypervisor);

        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getId());
        assertEquals(1, results.size());
        assertEquals(hypervisor1, results.get(0));
    }

    @Test
    public void testGetHypervisorsByOwnerWithPrincipal() {
        Consumer hypervisor1 = createHypervisor(owner);
        // Create hypervisor for another owner
        createHypervisor(this.createOwner());

        User user = new User(TestUtil.randomString(), TestUtil.randomString());
        Set<Permission> perms = new HashSet<>();
        perms.add(new ConsumerPermission(hypervisor1, owner));
        UserPrincipal principal = new UserPrincipal(user.getUsername(), perms, false);
        this.setupPrincipal(principal);

        Consumer nonHypervisor = new Consumer()
            .setName(TestUtil.randomString())
            .setUsername(TestUtil.randomString())
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(nonHypervisor);

        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getId());

        assertThat(results)
            .isNotNull()
            .singleElement()
            .isEqualTo(hypervisor1);
    }

    @Test
    public void testGetHypervisorsByOwnerWithDifferentPrincipal() {
        // Create hypervisor for target owner
        createHypervisor(owner);

        Owner owner2 = this.createOwner();
        Consumer otherHypervisor = createHypervisor(owner2);

        User user = new User(TestUtil.randomString(), TestUtil.randomString());
        Set<Permission> perms = new HashSet<>();
        perms.add(new ConsumerPermission(otherHypervisor, owner));
        UserPrincipal principal = new UserPrincipal(user.getUsername(), perms, false);
        this.setupPrincipal(principal);

        Consumer nonHypervisor = new Consumer()
            .setName(TestUtil.randomString())
            .setUsername(TestUtil.randomString())
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(nonHypervisor);

        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getId());

        assertThat(results)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetHypervisorsByOwnerPaged() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(1);
        pageRequest.setPerPage(1);

        Consumer hypervisor1 = createHypervisor(owner);
        Consumer hypervisor2 = createHypervisor(owner);

        Consumer nonHypervisor = new Consumer()
            .setName("testConsumer3")
            .setUsername("testUser3")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(nonHypervisor);

        List<Consumer> results = consumerCurator.getHypervisorsForOwner(owner.getId(), pageRequest);
        assertEquals(1, results.size());
        assertEquals(hypervisor1, results.get(0));
    }

    @Test
    public void testCountHypervisorsByOwner() {
        createHypervisor(owner);
        createHypervisor(owner);

        long count = consumerCurator.countHypervisorsForOwner(owner.getId());

        assertEquals(2, count);
    }

    private Consumer createHypervisor(Owner owner) {
        String hypervisorId = TestUtil.randomString("hypervisor");
        return createHypervisor(owner, hypervisorId);
    }

    private Consumer createHypervisor(Owner owner, String hypervisorId) {
        HypervisorId hypervisor = new HypervisorId()
            .setOwner(owner)
            .setHypervisorId(hypervisorId);

        Consumer consumer = new Consumer()
            .setName(TestUtil.randomString("testConsumer"))
            .setUsername(TestUtil.randomString("testUser"))
            .setOwner(owner)
            .setType(ct)
            .setHypervisorId(hypervisor);

        return consumerCurator.create(consumer);
    }

    @Test
    public void testGetIdentityCertIdsWithExistingIdentityCerts() {
        Consumer consumerWithIdCert1 = new Consumer()
            .setName("consumerWithIdCert1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setIdCert(createIdCert());
        consumerWithIdCert1 = consumerCurator.create(consumerWithIdCert1);
        Consumer consumerWithIdCert2 = new Consumer()
            .setName("consumerWithIdCert2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setIdCert(createIdCert());
        consumerWithIdCert2 = consumerCurator.create(consumerWithIdCert2);
        Consumer consumerWithNoIdCert = new Consumer()
            .setName("consumerWithNoIdCert")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerWithNoIdCert = consumerCurator.create(consumerWithNoIdCert);

        List<String> actual = consumerCurator.getIdentityCertIds(Arrays
            .asList(consumerWithIdCert1.getId(), consumerWithIdCert2.getId(), consumerWithNoIdCert.getId()));

        assertEquals(2, actual.size());
        assertTrue(actual.contains(consumerWithIdCert1.getIdCert().getId()));
        assertTrue(actual.contains(consumerWithIdCert2.getIdCert().getId()));
    }

    @Test
    public void testGetContentAccessCertIdsWithExistingContentAccessCerts() {
        Consumer consumerWithCACert1 = new Consumer()
            .setName("consumerWithCACert1")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setContentAccessCert(createContentAccessCertificate());
        consumerWithCACert1 = consumerCurator.create(consumerWithCACert1);

        Consumer consumerWithCACert2 = new Consumer()
            .setName("consumerWithCACert2")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct)
            .setContentAccessCert(createContentAccessCertificate());
        consumerWithCACert2 = consumerCurator.create(consumerWithCACert2);

        Consumer consumerWithNoCACert = new Consumer()
            .setName("consumerWithNoCACert")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerWithNoCACert = consumerCurator.create(consumerWithNoCACert);

        List<String> actual = consumerCurator.getContentAccessCertIds(Arrays
            .asList(consumerWithCACert1.getId(), consumerWithCACert2.getId(), consumerWithNoCACert.getId()));

        assertEquals(2, actual.size());
        assertTrue(actual.contains(consumerWithCACert1.getContentAccessCert().getId()));
        assertTrue(actual.contains(consumerWithCACert2.getContentAccessCert().getId()));
    }

    @Test
    public void testGetSerialIdsForCertsWithExistingSerials() {
        SCACertificate caCert1 = createContentAccessCertificate();
        SCACertificate caCert2 = createContentAccessCertificate();
        IdentityCertificate idCert1 = createIdCert();
        IdentityCertificate idCert2 = createIdCert();
        List<String> caCertIds = Arrays.asList(caCert1.getId(), caCert2.getId(), "unknown-id");
        List<String> idCertIds = Arrays.asList(idCert1.getId(), idCert2.getId(), "unknown-id");

        List<Long> actual = consumerCurator.getSerialIdsForCerts(caCertIds, idCertIds);

        assertEquals(4, actual.size());
        assertTrue(actual.contains(caCert1.getSerial().getId()));
        assertTrue(actual.contains(caCert2.getSerial().getId()));
        assertTrue(actual.contains(idCert1.getSerial().getId()));
        assertTrue(actual.contains(idCert2.getSerial().getId()));
    }

    @Test
    public void testGetInactiveConsumersWithNullLastCheckedInRetentionDate() {
        Instant nonCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCurator.getInactiveConsumers(null, nonCheckedInRetention);
        });

        assertEquals("Last checked-in retention date cannot be null.", exception.getMessage());
    }

    @Test
    public void testGetInactiveConsumersWithNullLastUpdatedRetentionDate() {
        Instant lastCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            consumerCurator.getInactiveConsumers(lastCheckedInRetention, null);
        });

        assertEquals("Last updated retention date cannot be null.", exception.getMessage());
    }

    @Test
    public void testGetInactiveConsumersWithExpiredLastCheckedInDate() {
        Instant lastCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);
        Instant nonCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        Consumer inactiveConsumer = new Consumer()
            .setName("inactiveConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        Instant lastCheckedIn = Instant.ofEpochMilli(lastCheckedInRetention.toEpochMilli() - 86400L);
        inactiveConsumer.setLastCheckin(Date.from(lastCheckedIn));
        inactiveConsumer = consumerCurator.create(inactiveConsumer);

        Consumer activeConsumer = new Consumer()
            .setName("activeConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        Instant activeLastCheckedIn = Instant.ofEpochMilli(lastCheckedInRetention.toEpochMilli() + 86400L);
        activeConsumer.setLastCheckin(Date.from(activeLastCheckedIn));
        consumerCurator.create(activeConsumer);

        List<InactiveConsumerRecord> actual = consumerCurator.getInactiveConsumers(lastCheckedInRetention,
            nonCheckedInRetention);

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(inactiveConsumer.getId(), InactiveConsumerRecord::consumerId)
            .returns(inactiveConsumer.getUuid(), InactiveConsumerRecord::consumerUuid)
            .returns(inactiveConsumer.getOwnerKey(), InactiveConsumerRecord::ownerKey)
            .returns(null, InactiveConsumerRecord::isOwnerAnonymous);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(booleans = {true, false})
    public void testGetInactiveConsumersShouldPopulateAnonymousFieldBasedOnOwner(boolean anonymous) {
        Owner anonOwner = TestUtil.createOwner(TestUtil.randomString(), TestUtil.randomString())
            .setId(null)
            .setAnonymous(anonymous);

        anonOwner = this.ownerCurator.create(anonOwner);

        Instant lastCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);
        Instant nonCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        Consumer inactiveConsumer = new Consumer()
            .setName("inactiveConsumer")
            .setUsername("testUser")
            .setOwner(anonOwner)
            .setType(ct);
        Instant lastCheckedIn = Instant.ofEpochMilli(lastCheckedInRetention.toEpochMilli() - 86400L);
        inactiveConsumer.setLastCheckin(Date.from(lastCheckedIn));
        inactiveConsumer = consumerCurator.create(inactiveConsumer);

        List<InactiveConsumerRecord> actual = consumerCurator.getInactiveConsumers(lastCheckedInRetention,
            nonCheckedInRetention);

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(inactiveConsumer.getId(), InactiveConsumerRecord::consumerId)
            .returns(inactiveConsumer.getUuid(), InactiveConsumerRecord::consumerUuid)
            .returns(inactiveConsumer.getOwnerKey(), InactiveConsumerRecord::ownerKey)
            .returns(anonymous, InactiveConsumerRecord::isOwnerAnonymous);
    }

    @Test
    public void testGetInactiveConsumersWithTypeThatHasManifest() {
        Instant lastCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        ConsumerType consumerType = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        consumerType = consumerTypeCurator.create(consumerType);
        Consumer consumer = new Consumer()
            .setName("consumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(consumerType);

        Instant lastCheckedIn = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS + 10,
                ChronoUnit.DAYS);

        consumer.setLastCheckin(Date.from(lastCheckedIn));
        consumerCurator.create(consumer);

        List<InactiveConsumerRecord> actual = this.consumerCurator
            .getInactiveConsumers(lastCheckedInRetention, Instant.now());

        assertEquals(0, actual.size());
    }

    @Test
    public void testGetInactiveConsumersWithConsumerThatHasExistingEntitlements() {
        Instant lastCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_CHECKED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);
        Instant nonCheckedInRetention = Instant.now()
            .minus(InactiveConsumerCleanerJob.DEFAULT_LAST_UPDATED_IN_RETENTION_IN_DAYS, ChronoUnit.DAYS);

        Consumer consumer = new Consumer()
            .setName("consumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumer = consumerCurator.create(consumer);

        Product product = this.createProduct("1", "2");
        Pool pool = createPool(owner, product, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent1 = this.createEntitlement(owner, consumer, pool,
            createEntitlementCertificate("entkey1", "ecert1"));
        ent1.setUpdatedOnStart(false);
        entitlementCurator.create(ent1);

        List<InactiveConsumerRecord> actual = consumerCurator.getInactiveConsumers(lastCheckedInRetention,
            nonCheckedInRetention);

        assertEquals(0, actual.size());
    }

    @Test
    public void testGetConsumerIdsWithStartedEnts() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        Product prod = this.createProduct("1", "2");

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
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        consumerCurator.create(consumer);
        Product prod = this.createProduct("1", "2");

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
        Consumer c = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        c.setContentTags(new HashSet<>(Arrays.asList("t1", "t2")));
        EntityManager em = this.getEntityManager();

        String countQuery = "SELECT COUNT(*) FROM cp_consumer_content_tags";

        consumerCurator.create(c);
        Long i = (Long) em.createNativeQuery(countQuery).getSingleResult();
        assertEquals(2L, i);

        consumerCurator.delete(c);
        i = (Long) em.createNativeQuery(countQuery).getSingleResult();
        assertEquals(0L, i);
    }

    @Test
    public void unlinksIdCerts() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        Consumer consumer2 = new Consumer()
            .setName("testConsumer2")
            .setUsername("testUser2")
            .setOwner(owner)
            .setType(ct);
        IdentityCertificate idCert1 = createIdCert();
        IdentityCertificate idCert2 = createIdCert();
        consumer.setIdCert(idCert1);
        consumer2.setIdCert(idCert2);
        consumerCurator.create(consumer);
        consumerCurator.create(consumer2);

        int unlinkedConsumers = consumerCurator.unlinkIdCertificates(List.of(
            idCert1.getId(),
            idCert2.getId()));
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        assertEquals(2, unlinkedConsumers);
        for (Consumer c : this.consumerCurator.listAll()) {
            assertNull(c.getIdCert());
        }
    }

    @Test
    public void noIdCertsToUnlink() {
        assertEquals(0, consumerCurator.unlinkIdCertificates(null));
        assertEquals(0, consumerCurator.unlinkIdCertificates(List.of()));
        assertEquals(0, consumerCurator.unlinkIdCertificates(List.of("UnknownId")));
    }

    @Test
    public void unlinksContentAccessCerts() {
        Consumer consumer = new Consumer()
            .setName("testConsumer")
            .setUsername("testUser")
            .setOwner(owner)
            .setType(ct);
        Consumer consumer2 = new Consumer()
            .setName("testConsumer2")
            .setUsername("testUser2")
            .setOwner(owner)
            .setType(ct);
        SCACertificate caCert1 = createExpiredContentAccessCert(consumer);
        SCACertificate caCert2 = createExpiredContentAccessCert(consumer2);
        consumer.setContentAccessCert(caCert1);
        consumer2.setContentAccessCert(caCert2);
        consumerCurator.create(consumer);
        consumerCurator.create(consumer2);

        int unlinkedConsumers = consumerCurator.unlinkCaCertificates(List.of(
            caCert1.getId(),
            caCert2.getId()));
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        assertEquals(2, unlinkedConsumers);
        for (Consumer c : this.consumerCurator.listAll()) {
            assertNull(c.getContentAccessCert());
        }
    }

    @Test
    public void noContentAccessCertsToUnlink() {
        assertEquals(0, consumerCurator.unlinkCaCertificates(null));
        assertEquals(0, consumerCurator.unlinkCaCertificates(List.of()));
        assertEquals(0, consumerCurator.unlinkCaCertificates(List.of("UnknownId")));
    }

    @Test
    public void shouldUpdateOwner() {
        Owner newOwner = createOwner();
        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);
        List<String> consumerUuids = List.of(
            consumer1.getUuid(),
            consumer2.getUuid()
        );

        consumerCurator.bulkUpdateOwner(consumerUuids, newOwner);
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        Collection<Consumer> consumers = this.consumerCurator.findByUuids(consumerUuids);
        Assertions.assertThat(consumers)
            .hasSize(2)
            .extracting(Consumer::getOwnerId)
            .containsOnly(newOwner.getId());
    }

    @Test
    public void shouldLockConsumersByIds() {
        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);
        List<String> consumerIds = List.of(
            consumer1.getId(),
            consumer2.getId()
        );

        Collection<String> lockedIds = consumerCurator.lockAndLoadIds(consumerIds);

        Assertions.assertThat(lockedIds)
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(consumerIds);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testLockAndLoadUuidsWithNullOrEmptyUuids(Collection<String> uuids) {
        List<String> actual = this.consumerCurator.lockAndLoadUuids(uuids);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldLockConsumersByUuids() {
        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);
        List<String> consumerUuids = List.of(
            consumer1.getUuid(),
            consumer2.getUuid(),
            TestUtil.randomString("unknown-"),
            consumer1.getUuid()
        );

        List<String> consumerIds = List.of(
            consumer1.getId(),
            consumer2.getId()
        );

        Collection<String> lockedIds = consumerCurator.lockAndLoadUuids(consumerUuids);

        Assertions.assertThat(lockedIds)
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(consumerIds);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testGetSystemConsumerUuidsByOwnerWithInvalidOwnerKey(String ownerKey) {
        List<String> actual = consumerCurator.getSystemConsumerUuidsByOwner(ownerKey);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetSystemConsumerUuidsByOwner() {
        Consumer systemConsumer1 = new Consumer()
            .setName(TestUtil.randomString())
            .setUsername(TestUtil.randomString())
            .setOwner(owner)
            .setType(ct);
        systemConsumer1 = consumerCurator.create(systemConsumer1);

        Consumer systemConsumer2 = new Consumer()
            .setName(TestUtil.randomString())
            .setUsername(TestUtil.randomString())
            .setOwner(owner)
            .setType(ct);
        systemConsumer2 = consumerCurator.create(systemConsumer2);

        ConsumerType manifestType = new ConsumerType(ConsumerTypeEnum.CANDLEPIN);
        manifestType = consumerTypeCurator.create(manifestType);

        Consumer manifestConsumer = new Consumer()
            .setName(TestUtil.randomString())
            .setUsername(TestUtil.randomString())
            .setOwner(owner)
            .setType(manifestType);
        manifestConsumer = consumerCurator.create(manifestConsumer);

        List<String> actual = consumerCurator.getSystemConsumerUuidsByOwner(owner.getOwnerKey());

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrder(systemConsumer1.getUuid(), systemConsumer2.getUuid());
    }

    @Test
    public void testConsumerFindByVirtUuid() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = createConsumer(owner);
        consumer.setFact("virt.uuid", uuid);

        Consumer findedConsumer = consumerCurator.findByVirtUuid(uuid, owner.getId());

        assertEquals(consumer.getId(), findedConsumer.getId());
    }

    @Test
    public void testConsumerFindByVirtUuidToGetNull() {
        assertNull(consumerCurator.findByVirtUuid(null, owner.getId()));
        assertNull(consumerCurator.findByVirtUuid(UUID.randomUUID().toString(), owner.getId()));
    }

    @Test
    public void testFindByUsername() {
        ConsumerType consumerType = createConsumerType(ConsumerTypeEnum.PERSON.getLabel(), true);
        Consumer consumer = createConsumer(owner, consumerType);

        Consumer foundConsumer = consumerCurator.findByUsername(consumer.getUsername());

        assertEquals(consumer.getId(), foundConsumer.getId());
    }

    @Test
    public void testFindByUsernameWithPrincipal() {
        User user = new User(TestUtil.randomString(), TestUtil.randomString());
        Set<Permission> perms = new HashSet<>();
        perms.add(new OwnerPermission(owner, Access.ALL));
        UserPrincipal principal = new UserPrincipal(user.getUsername(), perms, false);
        this.setupPrincipal(principal);

        ConsumerType consumerType = createConsumerType(ConsumerTypeEnum.PERSON.getLabel(), true);
        Consumer expected = createConsumer(owner, consumerType);

        Consumer actual = consumerCurator.findByUsername(expected.getUsername());

        assertEquals(expected, actual);
    }

    @Test
    public void testFindByUsernameWithDifferentPrincipal() {
        User user = new User(TestUtil.randomString(), TestUtil.randomString());
        Owner owner2 = this.createOwner();
        Set<Permission> perms = new HashSet<>();
        perms.add(new OwnerPermission(owner2, Access.ALL));
        UserPrincipal principal = new UserPrincipal(user.getUsername(), perms, false);
        this.setupPrincipal(principal);

        ConsumerType consumerType = createConsumerType(ConsumerTypeEnum.PERSON.getLabel(), true);
        Consumer expected = createConsumer(owner, consumerType);

        Consumer actual = consumerCurator.findByUsername(expected.getUsername());

        assertNull(actual);
    }

    @Test
    public void testFindByUsernameWithNullConsumerType() {
        Consumer consumer = createConsumer(owner);

        Consumer foundConsumer = consumerCurator.findByUsername(consumer.getUsername());

        assertNull(foundConsumer);
    }

    @Test
    public void testFindByUsernameWithNotExistingUsername() {
        createConsumerType(ConsumerTypeEnum.PERSON.getLabel(), true);

        Consumer foundConsumer = consumerCurator.findByUsername(TestUtil.randomString());

        assertNull(foundConsumer);
    }

    @Test
    public void testGetConsumerBySystemUuid() {
        String uuid = UUID.randomUUID().toString();
        Consumer consumer = createConsumer(owner).setFact(Consumer.Facts.DMI_SYSTEM_UUID, uuid);

        Consumer foundConsumer = consumerCurator.getConsumerBySystemUuid(
            owner.getId(), uuid);

        assertEquals(consumer.getId(), foundConsumer.getId());
    }

    @Test
    public void testGetConsumerBySystemUuidWithNonExistingUuid() {
        Consumer foundConsumer = consumerCurator.getConsumerBySystemUuid(
            owner.getId(), UUID.randomUUID().toString());

        assertNull(foundConsumer);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testGetNonExistentConsumerUuidsWithInvalidOwnerKey(String ownerKey) {
        Set<String> actual = consumerCurator.getNonExistentConsumerUuids(ownerKey, List.of("uuid"));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testGetNonExistentConsumerUuidsWithNullOrEmptyConsumerUuids(List<String> consumerUuids) {
        Set<String> actual = consumerCurator.getNonExistentConsumerUuids("key", consumerUuids);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetNonExistentConsumerUuids() {
        Owner owner1 = this.createOwner();
        Consumer c1 = createConsumer(owner1);
        Consumer c2 = createConsumer(owner1);
        createConsumer(owner1);

        Owner owner2 = this.createOwner();
        Consumer c4 = createConsumer(owner2);
        createConsumer(owner2);

        String unknown1 = TestUtil.randomString("unknown-");
        String unknown2 = TestUtil.randomString("unknown-");

        List<String> consumerUuids = List.of(c1.getUuid(), unknown1, c2.getUuid(), unknown2, c4.getUuid());

        Set<String> actual = consumerCurator.getNonExistentConsumerUuids(owner1.getOwnerKey(), consumerUuids);

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrder(unknown1, unknown2, c4.getUuid());
    }

    @ParameterizedTest
    @MethodSource("listOfIdsReturnZero")
    public void testDeleteConsumerReturnZero(List<String> consumerIds) {
        int count = this.consumerCurator.deleteConsumers(consumerIds);

        assertThat(count)
            .isZero();
    }

    static Stream<List<String>> listOfIdsReturnZero() {
        return Stream.of(
            null,
            Collections.emptyList(),
            List.of("unknownId"),
            List.of("unknownId1", "unknownId1")
        );
    }

    @Test
    public void testDeleteConsumer() {
        Owner owner = this.createOwner();
        Consumer consumer = createConsumer(owner);

        int count = this.consumerCurator.deleteConsumers(List.of(consumer.getId()));

        assertThat(count)
            .isEqualTo(1);

        // Verify that specific consumer does not exist
        Consumer result = this.consumerCurator.findByUuid(consumer.getUuid());
        assertThat(result)
            .isNull();
    }

    @Test
    public void testDeleteConsumerCreateCorrespondingRecordInDeleteConsumerTable() {
        Owner owner = this.createOwner();
        Consumer consumer = createConsumer(owner);

        this.consumerCurator.deleteConsumers(List.of(consumer.getId()));

        DeletedConsumer deletedConsumer = this.deletedConsumerCurator.findByConsumerId(consumer.getId());
        assertThat(deletedConsumer)
            .isNotNull()
            .returns(consumer.getId(), DeletedConsumer::getId)
            .returns(consumer.getUuid(), DeletedConsumer::getConsumerUuid)
            .returns(consumer.getName(), DeletedConsumer::getConsumerName)
            .returns(owner.getId(), DeletedConsumer::getOwnerId)
            .returns(owner.getKey(), DeletedConsumer::getOwnerKey);
    }

    private IdentityCertificate createIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(TestUtil.createDateOffset(2, 0, 0));
        return saveCert(idCert);
    }

    private SCACertificate createContentAccessCertificate() {
        SCACertificate certificate = new SCACertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial(Util.addDaysToDt(10)));
        certificate.setCert("cert_1");
        return saveCert(certificate);
    }

    private SCACertificate createExpiredContentAccessCert(Consumer consumer) {
        SCACertificate certificate = new SCACertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial(Util.yesterday()));
        certificate.setCert("cert_1");
        consumer.setContentAccessCert(certificate);
        return saveCert(certificate);
    }

    private IdentityCertificate saveCert(IdentityCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return identityCertificateCurator.create(cert);
    }

    private SCACertificate saveCert(SCACertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return caCertCurator.create(cert);
    }
}
