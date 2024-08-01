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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the consumer searching functionality provided by the ConsumerCurator class
 */
public class ConsumerCuratorSearchTest extends DatabaseTestFixture {

    private Consumer createConsumer(Owner owner, String name, String uuid, ConsumerType type, String username,
        Map<String, String> facts, String hypervisorId, List<String> environmentIds) {

        HypervisorId hid = null;
        if (hypervisorId != null) {
            hid = new HypervisorId()
                .setOwner(owner)
                .setHypervisorId(hypervisorId);
        }

        Consumer consumer = new Consumer()
            .setOwner(owner)
            .setName(name)
            .setUuid(uuid)
            .setType(type)
            .setUsername(username)
            .setHypervisorId(hid)
            .setFacts(facts)
            .setEnvironmentIds(environmentIds);

        return this.consumerCurator.create(consumer);
    }

    private List<Consumer> createConsumersForQueryTests() {
        List<Owner> owners = Stream.generate(this::createOwner)
            .limit(3)
            .collect(Collectors.toList());

        List<ConsumerType> types = Stream.generate(this::createConsumerType)
            .limit(3)
            .collect(Collectors.toList());

        List<String> usernames = Arrays.asList(null, "username1", "username2", "username3");

        // Testing/Impl note:
        // Map.of doesn't support null values, but that turns out to be kind of acceptable here,
        // since a Hibernate "quirk" with how ElementCollection interacts with maps that contain
        // null values prevents us from persisting any facts with null values anyhow.
        Map<String, String> factsMap1 = Map.of(
            "factkey-1", "value-1",
            "factkey-2", "",
            "factkey-3", "value-3!",
            "factkey-4", "value-4!",
            "factkey-5", "value-*",
            "factkey-6", "value-6",
            "factkey-*", "value-7");

        Map<String, String> factsMap2 = Map.of(
            "factkey-1", "value-1b",
            "factkey-2", "",
            "factkey-3", "value-3b",
            "factkey-4", "value_4!",
            "factkey-5", "value??",
            "factkey_6", "value-6",
            "factkey-?", "value-7");

        Map<String, String> factsMap3 = Map.of(
            "factkey-1", "value-1c",
            "factkey-2", "value-2c",
            "factkey-3", "value-3!",
            "factkey-4", "value%4!",
            "factkey-5", "value\\5",
            "factkey%6", "value-6",
            "factkey\\7", "value-7");

        List<Map<String, String>> facts = Arrays.asList(null, factsMap1, factsMap2, factsMap3);

        List<Consumer> created = new LinkedList<>();
        int count = 0;

        for (Owner owner : owners) {
            Environment environment = createEnvironment(owner);
            for (ConsumerType type : types) {
                for (String username : usernames) {
                    for (Map<String, String> factMap : facts) {
                        ++count;
                        created.add(this.createConsumer(owner, "consumer-" + count, "uuid-" + count, type,
                            username, factMap, null, List.of(environment.getId())));

                        ++count;
                        created.add(this.createConsumer(owner, "consumer-" + count, "uuid-" + count, type,
                            username, factMap, "hypervisor-" + count, List.of(environment.getId())));
                    }
                }
            }
        }

        this.asyncJobCurator.flush();
        return created;
    }

    @Test
    public void testFindConsumersByOwner() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Owner owner = created.stream()
            .map(Consumer::getOwner)
            .filter(Objects::nonNull)
            .findFirst()
            .get();

        long expected = created.stream()
            .filter(consumer -> owner.getId().equals(consumer.getOwnerId()))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setOwner(owner);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertEquals(owner.getId(), consumer.getOwnerId());
        }
    }

    @Test
    public void testFindConsumersByOwnerNoMatch() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Owner owner = this.createOwner();
        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setOwner(owner);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }

    @Test
    public void testFindConsumersByUsername() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String username = created.stream()
            .map(Consumer::getUsername)
            .filter(Objects::nonNull)
            .findFirst()
            .get();

        long expected = created.stream()
            .filter(consumer -> username.equals(consumer.getUsername()))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUsername(username);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertEquals(username, consumer.getUsername());
        }
    }

    @Test
    public void testFindConsumersByUsernameNoMatch() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String username = "bad username";
        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUsername(username);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }

    @Test
    public void testFindConsumersByUuids() {
        List<Consumer> created = this.createConsumersForQueryTests();
        assertTrue(created.size() > 3);

        List<String> expectedUuids = created.subList(1, 3).stream()
            .map(Consumer::getUuid)
            .collect(Collectors.toList());

        long expected = expectedUuids.size();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUuids(expectedUuids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertThat(expectedUuids, hasItem(consumer.getUuid()));
        }
    }

    @Test
    public void testFindConsumersByUuidsWithExtraneous() {
        List<Consumer> created = this.createConsumersForQueryTests();
        assertTrue(created.size() > 3);

        List<String> expectedUuids = created.subList(1, 3).stream()
            .map(Consumer::getUuid)
            .collect(Collectors.toList());
        List<String> extraneousUuids = Arrays.asList("bad_uuid-1", "bad_uuid-2", "bad_uuid-3");

        long expected = expectedUuids.size();
        assertTrue(expected > 0);

        List<String> uuids = new LinkedList<>();
        uuids.addAll(expectedUuids);
        uuids.addAll(extraneousUuids);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUuids(uuids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertThat(expectedUuids, hasItem(consumer.getUuid()));
            assertThat(extraneousUuids, not(hasItem(consumer.getUuid())));
        }
    }

    @Test
    public void testFindConsumersByUuidsNoMatch() {
        List<Consumer> created = this.createConsumersForQueryTests();

        List<String> extraneousUuids = Arrays.asList("bad_uuid-1", "bad_uuid-2", "bad_uuid-3");
        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUuids(extraneousUuids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }

    @Test
    public void testFindConsumersByTypes() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Set<String> typeIds = created.stream()
            .map(Consumer::getTypeId)
            .collect(Collectors.toSet());

        Set<String> expectedTypeIds = typeIds.stream()
            .limit(2)
            .collect(Collectors.toSet());

        Set<ConsumerType> expectedTypes = expectedTypeIds.stream()
            .map(id -> this.consumerTypeCurator.get(id))
            .collect(Collectors.toSet());

        long expected = created.stream()
            .filter(consumer -> expectedTypeIds.contains(consumer.getTypeId()))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setTypes(expectedTypes);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertThat(expectedTypeIds, hasItem(consumer.getTypeId()));
        }
    }

    @Test
    public void testFindConsumersByTypesWithExtraneous() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Set<String> typeIds = created.stream()
            .map(Consumer::getTypeId)
            .collect(Collectors.toSet());

        Set<String> expectedTypeIds = typeIds.stream()
            .limit(2)
            .collect(Collectors.toSet());

        Set<ConsumerType> expectedTypes = expectedTypeIds.stream()
            .map(id -> this.consumerTypeCurator.get(id))
            .collect(Collectors.toSet());

        Set<ConsumerType> extraneousTypes = Stream.generate(this::createConsumerType)
            .limit(3)
            .collect(Collectors.toSet());

        Set<String> extraneousTypeIds = extraneousTypes.stream()
            .map(ConsumerType::getId)
            .collect(Collectors.toSet());

        long expected = created.stream()
            .filter(consumer -> expectedTypeIds.contains(consumer.getTypeId()))
            .count();
        assertTrue(expected > 0);

        List<ConsumerType> searchTypes = new LinkedList<>();
        searchTypes.addAll(expectedTypes);
        searchTypes.addAll(extraneousTypes);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setTypes(searchTypes);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertThat(expectedTypeIds, hasItem(consumer.getTypeId()));
            assertThat(extraneousTypeIds, not(hasItem(consumer.getTypeId())));
        }
    }

    @Test
    public void testFindConsumersByTypesNoMatch() {
        List<Consumer> created = this.createConsumersForQueryTests();

        List<ConsumerType> extraneousTypes = Stream.generate(this::createConsumerType)
            .limit(3)
            .collect(Collectors.toList());

        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setTypes(extraneousTypes);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }

    @Test
    public void testFindConsumersByHypervisorIds() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Set<String> hypervisorIds = created.stream()
            .filter(consumer -> consumer.getHypervisorId() != null)
            .map(consumer -> consumer.getHypervisorId().getHypervisorId())
            .collect(Collectors.toSet());

        Set<String> expectedHids = hypervisorIds.stream()
            .limit(2)
            .collect(Collectors.toSet());

        long expected = expectedHids.size();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setHypervisorIds(expectedHids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer.getHypervisorId());
            assertThat(expectedHids, hasItem(consumer.getHypervisorId().getHypervisorId()));
        }
    }

    @Test
    public void testFindConsumersByHypervisorIdsWithExtraneous() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Set<String> hypervisorIds = created.stream()
            .filter(consumer -> consumer.getHypervisorId() != null)
            .map(consumer -> consumer.getHypervisorId().getHypervisorId())
            .collect(Collectors.toSet());

        Set<String> expectedHids = hypervisorIds.stream()
            .limit(2)
            .collect(Collectors.toSet());

        List<String> extraneousHids = Arrays.asList("bad_hv-1", "bad_hv-2", "bad_hv-3");

        long expected = expectedHids.size();
        assertTrue(expected > 0);

        List<String> hids = new LinkedList<>();
        hids.addAll(expectedHids);
        hids.addAll(extraneousHids);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setHypervisorIds(hids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer.getHypervisorId());

            assertThat(expectedHids, hasItem(consumer.getHypervisorId().getHypervisorId()));
            assertThat(extraneousHids, not(hasItem(consumer.getHypervisorId().getHypervisorId())));
        }
    }

    @Test
    public void testFindConsumersByHypervisorIdsNoMatch() {
        List<Consumer> created = this.createConsumersForQueryTests();

        List<String> extraneousHids = Arrays.asList("bad_hv-1", "bad_hv-2", "bad_hv-3");
        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setHypervisorIds(extraneousHids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }

    @Test
    public void testFindConsumersByHypervisorIdsWithNull() {
        List<Consumer> created = this.createConsumersForQueryTests();

        List<String> expectedHids = Arrays.asList((String) null);
        long expected = created.stream()
            .filter(consumer -> consumer.getHypervisorId() == null)
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setHypervisorIds(expectedHids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNull(consumer.getHypervisorId());
        }
    }

    @Test
    public void testFindConsumersByHypervisorIdsWithNullsAndNonNulls() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Set<String> hypervisorIds = created.stream()
            .filter(consumer -> consumer.getHypervisorId() != null)
            .map(consumer -> consumer.getHypervisorId().getHypervisorId())
            .collect(Collectors.toSet());

        Set<String> expectedHids = hypervisorIds.stream()
            .limit(2)
            .collect(Collectors.toSet());

        expectedHids.add(null);

        long expectedNullHidCount = created.stream()
            .filter(consumer -> consumer.getHypervisorId() == null)
            .count();

        int expectedNonNullHidCount = expectedHids.size() - 1;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setHypervisorIds(expectedHids);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expectedNullHidCount + expectedNonNullHidCount, fetched.size());

        int nullCount = 0;
        int nonNullCount = 0;

        for (Consumer consumer : fetched) {
            if (consumer.getHypervisorId() != null) {
                ++nonNullCount;
                assertThat(expectedHids, hasItem(consumer.getHypervisorId().getHypervisorId()));
            }
            else {
                ++nullCount;
            }
        }

        assertEquals(expectedNullHidCount, nullCount);
        assertEquals(expectedNonNullHidCount, nonNullCount);
    }

    @Test
    public void testFindConsumersByFacts() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-1";
        String expFactValue = "value-1";

        long expected = created.stream()
            .filter(consumer -> expFactValue.equals(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertEquals(expFactValue, consumer.getFact(expFactKey));
        }
    }

    @Test
    public void testFindConsumersByFactsUsesDisjunctionForMultiValueSingleFact() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-1";
        List<String> expFactValues = Arrays.asList("value-1", "value-1c", "extraneous-value");

        long expected = created.stream()
            .filter(consumer -> expFactValues.contains(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments();
        for (String factValue : expFactValues) {
            queryArgs.addFact(expFactKey, factValue);
        }

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertThat(expFactValues, hasItem(consumer.getFact(expFactKey)));
        }
    }

    @Test
    public void testFindConsumersByFactsUsesConjunctionForMultipleFacts() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey1 = "factkey-1";
        String expFactValue1 = "value-1";
        String expFactKey2 = "factkey-3";
        String expFactValue2 = "value-3!";

        long expected = created.stream()
            .filter(consumer -> expFactValue1.equals(consumer.getFact(expFactKey1)))
            .filter(consumer -> expFactValue2.equals(consumer.getFact(expFactKey2)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey1, expFactValue1)
            .addFact(expFactKey2, expFactValue2);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);

            assertTrue(consumer.hasFact(expFactKey1));
            assertTrue(consumer.hasFact(expFactKey2));

            assertEquals(expFactValue1, consumer.getFact(expFactKey1));
            assertEquals(expFactValue2, consumer.getFact(expFactKey2));
        }
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testFindConsumersByFactsProperlyHandlesNullValues() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-2";
        String expFactValue = null;

        long expected = created.stream()
            .filter(consumer -> consumer.hasFact(expFactKey))
            .filter(consumer -> consumer.getFact(expFactKey) == null ||
                consumer.getFact(expFactKey).isEmpty())
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));

            assertThat(Arrays.asList("", null), hasItem(consumer.getFact(expFactKey)));
        }
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testFindConsumersByFactsProperlyHandlesEmptyValues() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-2";
        String expFactValue = "";

        long expected = created.stream()
            .filter(consumer -> consumer.hasFact(expFactKey))
            .filter(consumer -> consumer.getFact(expFactKey) == null ||
                consumer.getFact(expFactKey).isEmpty())
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));

            assertThat(Arrays.asList("", null), hasItem(consumer.getFact(expFactKey)));
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"VALUE-1", "vAlUe-1", "value-1"})
    public void testFindConsumersByFactsMatchesValuesCaseInsensitively(String factValue) {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-1";

        long expected = created.stream()
            .filter(consumer -> factValue.equalsIgnoreCase(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, factValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertThat(consumer.getFact(expFactKey), equalToIgnoringCase(factValue));
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"value-4!", "value_4!", "value%4!"})
    public void testFindConsumersByFactsEscapesLikeWildcardsInValues(String expFactValue) {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-4";

        long expected = created.stream()
            .filter(consumer -> expFactValue.equals(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertEquals(expFactValue, consumer.getFact(expFactKey));
        }
    }

    @Test
    public void testFindConsumersByFactsConvertsShellSingleCharWildcardInValues() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-4";
        String expFactValue = "v?lue?4!";
        String expFactValueRegex = "\\Av.lue.4!\\z";

        long expected = created.stream()
            .filter(consumer -> consumer.getFact(expFactKey) != null)
            .filter(consumer -> Pattern.matches(expFactValueRegex, consumer.getFact(expFactKey)))
            .count();

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertTrue(Pattern.matches(expFactValueRegex, consumer.getFact(expFactKey)));
        }
    }

    @Test
    public void testFindConsumersByFactsConvertsShellMultiCharWildcardInValues() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-1";
        String expFactValue = "val*1*";
        String expFactValueRegex = "\\Aval.*1.*\\z";

        long expected = created.stream()
            .filter(consumer -> consumer.getFact(expFactKey) != null)
            .filter(consumer -> Pattern.matches(expFactValueRegex, consumer.getFact(expFactKey)))
            .count();

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertTrue(Pattern.matches(expFactValueRegex, consumer.getFact(expFactKey)));
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"factkey-6", "factkey_6", "factkey%6"})
    public void testFindConsumersByFactsEscapesLikeWildcardsInKeys(String expFactKey) {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactValue = "value-6";

        long expected = created.stream()
            .filter(consumer -> expFactValue.equals(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertEquals(expFactValue, consumer.getFact(expFactKey));
        }
    }

    public static boolean consumerHasFactMatching(Consumer consumer, String regex, String value) {
        if (consumer == null || consumer.getFacts() == null) {
            return false;
        }

        return consumer.getFacts().entrySet()
            .stream()
            .filter(entry -> Pattern.matches(regex, entry.getKey()))
            .anyMatch(entry -> value.equalsIgnoreCase(entry.getValue()));
    }

    @Test
    public void testFindConsumersByFactsConvertsShellSingleCharWildcardInKeys() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "fa??key-?";
        String expFactKeyRegex = "\\Afa..key-.\\z";
        String expFactValue = "value-5";

        long expected = created.stream()
            .filter(consumer -> consumerHasFactMatching(consumer, expFactKeyRegex, expFactValue))
            .count();

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumerHasFactMatching(consumer, expFactKeyRegex, expFactValue));
        }
    }

    @Test
    public void testFindConsumersByFactsConvertsShellMultiCharWildcardInKeys() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "fact*-*";
        String expFactKeyRegex = "\\Afact.*-.*\\z";
        String expFactValue = "value-5";

        long expected = created.stream()
            .filter(consumer -> consumerHasFactMatching(consumer, expFactKeyRegex, expFactValue))
            .count();

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumerHasFactMatching(consumer, expFactKeyRegex, expFactValue));
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"factkey-\\*", "factkey-\\?", "factkey\\7"})
    public void testFindConsumerByFactsProperlyHandlesEscapesInKeys(String factKey) {
        List<Consumer> created = this.createConsumersForQueryTests();

        String resolvedFactKey = factKey.replaceAll("\\\\([*?])", "$1");
        String expFactValue = "value-7";

        long expected = created.stream()
            .filter(consumer -> expFactValue.equals(consumer.getFact(resolvedFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(factKey, expFactValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(resolvedFactKey));
            assertEquals(expFactValue, consumer.getFact(resolvedFactKey));
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"value-\\*", "value\\?\\?", "value\\5"})
    public void testFindConsumerByFactsProperlyHandlesEscapesInValues(String factValue) {
        List<Consumer> created = this.createConsumersForQueryTests();

        String expFactKey = "factkey-5";
        String resolvedFactValue = factValue.replaceAll("\\\\([*?])", "$1");

        long expected = created.stream()
            .filter(consumer -> resolvedFactValue.equals(consumer.getFact(expFactKey)))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .addFact(expFactKey, factValue);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertTrue(consumer.hasFact(expFactKey));
            assertEquals(resolvedFactValue, consumer.getFact(expFactKey));
        }
    }

    @Test
    public void testFindConsumerAddsSecurityRestrictions() {
        List<Consumer> created = this.createConsumersForQueryTests();

        Owner owner = created.stream()
            .map(Consumer::getOwner)
            .filter(Objects::nonNull)
            .findFirst()
            .get();

        String username = created.stream()
            .map(Consumer::getUsername)
            .filter(Objects::nonNull)
            .findFirst()
            .get();

        // OwnerPermission should trigger an implicit Consumer.owner == owner restriction
        Principal principal = mock(Principal.class);
        Permission permission = new OwnerPermission(owner, Access.ALL);

        doReturn(false).when(principal).hasFullAccess();
        doReturn(Arrays.asList(permission)).when(principal).getPermissions();

        this.setupPrincipal(principal);

        List<Consumer> expected = created.stream()
            .filter(consumer -> owner.equals(consumer.getOwner()))
            .filter(consumer -> username.equals(consumer.getUsername()))
            .collect(Collectors.toList());

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setUsername(username);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected.size(), fetched.size());
        assertEquals(expected.size(), fetchCount);

        for (Consumer consumer : fetched) {
            assertNotNull(consumer);
            assertEquals(owner, consumer.getOwner());
            assertEquals(username, consumer.getUsername());
        }
    }

    @Test
    public void testFindConsumersByEnvironmentId() {
        List<Consumer> created = this.createConsumersForQueryTests();

        String environmentId = created.stream()
            .map(Consumer::getEnvironmentIds)
            .flatMap(List::stream)
            .findFirst()
            .orElse(null);

        long expected = created.stream()
            .filter(consumer -> consumer.getEnvironmentIds().contains(environmentId))
            .count();
        assertTrue(expected > 0);

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setEnvironmentId(environmentId);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());

        for (Consumer consumer : fetched) {
            assertThat(consumer.getEnvironmentIds(), hasItem(environmentId));
        }
    }

    @Test
    public void testFindConsumersByEnvironmentIdNoMatch() {
        this.createConsumersForQueryTests();

        String environmentId = "bad environment id";
        long expected = 0;

        ConsumerQueryArguments queryArgs = new ConsumerQueryArguments()
            .setEnvironmentId(environmentId);

        List<Consumer> fetched = this.consumerCurator.findConsumers(queryArgs);
        long fetchCount = this.consumerCurator.getConsumerCount(queryArgs);

        assertNotNull(fetched);
        assertEquals(fetched.size(), fetchCount);
        assertEquals(expected, fetched.size());
    }
}
