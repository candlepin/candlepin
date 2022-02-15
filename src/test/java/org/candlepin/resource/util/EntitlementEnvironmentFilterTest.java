/**
 * Copyright (c) 2021 - 2021 Red Hat, Inc.
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
package org.candlepin.resource.util;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
public class EntitlementEnvironmentFilterTest {
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EnvironmentContentCurator environmentContentCurator;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        this.consumer = createConsumer(1);
    }

    @ParameterizedTest
    @MethodSource("addedDetectChange")
    public void shouldDetectEntitlementOfAddedEnvironment(List<String> currentEnvs,
        List<String> updatedEnvs) {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(this.entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2")),
            entry("envC", List.of("c1"))
        );
        when(this.environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), currentEnvs, updatedEnvs);
        Set<String> filteredEnt1 = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);

        assertEquals(filteredEnt1.size(), 1);
        assertTrue(filteredEnt1.contains("ent1"));
    }

    public static Stream<Arguments> addedDetectChange() {
        return Stream.of(
            // EnvB added with Higher priority
            // EnvA & EnvB both have different content
            Arguments.of(List.of("envA"), List.of("envB", "envA")),
            // EnvB added with Lower priority
            // EnvA & EnvB both have different content
            Arguments.of(List.of("envA"), List.of("envA", "envB")),
            // EnvC is added with Higher priority
            // EnvA (low) & EnvC (high) both have same content
            Arguments.of(List.of("envA"), List.of("envC", "envA"))
        );
    }

    @Test
    public void shouldSkipEntitlementOfAddedEnvironmentWithSameContent() {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(this.entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2")),
            entry("envC", List.of("c1"))
        );
        when(this.environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        // EnvC is added with lower priority
        // EnvA (high) & EnvC (low) both have same content
        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), List.of("envA"), List.of("envA", "envC"));

        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 0);

    }

    @ParameterizedTest
    @MethodSource("removedDetectChange")
    public void shouldDetectEntitlementOfRemovedEnvironment(List<String> currentEnvs,
        List<String> updatedEnvs) {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2")),
            entry("envC", List.of("c1"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), currentEnvs, updatedEnvs);
        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);

        assertEquals(filteredEnt.size(), 1);
        assertTrue(filteredEnt.contains("ent1"));
    }

    public static Stream<Arguments> removedDetectChange() {
        return Stream.of(
            // EnvA of higher priority is being removed
            // envA & envB have different content
            Arguments.of(List.of("envA", "envB"), List.of("envB")),
            // EnvB of lower priority is being removed
            // envA & envB have different content
            Arguments.of(List.of("envA", "envB"), List.of("envA")),
            // EnvC of higher priority is being removed
            // EnvC provides content same as envA
            Arguments.of(List.of("envC", "envA"), List.of("envA"))
        );
    }

    @ParameterizedTest
    @MethodSource("removedIgnoreChange")
    public void shouldSkipEntitlementOfRemovedEnvironmentWithSameContent(
        List<String> currentEnvs, List<String> updatedEnvs) {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2")),
            entry("envC", List.of("c1"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), currentEnvs, updatedEnvs);

        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 0);
    }

    public static Stream<Arguments> removedIgnoreChange() {
        return Stream.of(
            // EnvC of lower priority is being removed
            // EnvC provides content same as envA
            Arguments.of(List.of("envA", "envB", "envC"), List.of("envA", "envB")),
            // EnvC of Lower priority is being removed
            // EnvC provides content same as envA
            Arguments.of(List.of("envA", "envC"), List.of("envA"))
        );
    }

    @Test
    public void testWhenLowerPriorityEnvsReorderedProvidingSameContent() {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c1")),
            entry("envC", List.of("c1"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(),
            List.of("envA", "envB", "envC"), List.of("envA", "envC", "envB"));

        // Reordering lower priority (envB & envC) environments
        // providing same contents
        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 0);
    }

    @Test
    public void testWhenEnvsAreReorderedProvidingDifferentContent() {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2", "c3"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), List.of("envA", "envB"), List.of("envB", "envA"));

        // Priority of envB & envA are reversed
        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 1);
        assertTrue(filteredEnt.contains("ent1"));
    }

    @Test
    public void testWhenHigherPriorityEnvironmentIsDeleted() {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1", "c2")),
            entry("envB", List.of("c1", "c2"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), List.of("envA", "envB"), List.of("envB"));

        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 1);
        assertTrue(filteredEnt.contains("ent1"));
    }

    @Test
    public void testEnvBeingAddedHasNoContent() {
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = Map.ofEntries(
            entry("envA", List.of("c1", "c2")),
            entry("envB", Collections.emptyList())
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable())).thenReturn(id);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), List.of("envA"), List.of("envB", "envA"));

        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);

        assertEquals(filteredEnt.size(), 0);
    }

    @Test
    public void shouldHandleUpdateOfMultipleConsumersAtOnce() {
        Consumer consumer2 = createConsumer(2);
        Map<String, Set<String>> contentUUIDsConsumerIds = Map.ofEntries(
            entry("ent1", Set.of("c1", "c2")),
            entry("ent2", Set.of("c2", "c3")),
            entry("ent3", Set.of("c4"))
        );
        when(entitlementCurator.getEntitlementContentUUIDs(anyCollection()))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> envContent = Map.ofEntries(
            entry("envA", List.of("c1")),
            entry("envB", List.of("c2")),
            entry("envC", List.of("c3", "c4"))
        );
        when(environmentContentCurator.getEnvironmentContentUUIDs(anyIterable()))
            .thenReturn(envContent);

        EnvironmentUpdates environmentUpdate = new EnvironmentUpdates();
        environmentUpdate.put(consumer.getId(), List.of("envA", "envB"), List.of("envB"));
        environmentUpdate.put(consumer2.getId(), List.of("envA", "envB"), List.of("envA"));
        environmentUpdate.put(consumer2.getId(), List.of("envC"), List.of("envA"));

        Set<String> filteredEnt = createEntitlementFilter()
            .filterEntitlements(environmentUpdate);
        assertEquals(filteredEnt.size(), 3);
    }

    private EntitlementEnvironmentFilter createEntitlementFilter() {
        return new EntitlementEnvironmentFilter(this.entitlementCurator,
            this.environmentContentCurator);
    }

    private Consumer createConsumer(int id) {
        Consumer consumer = new Consumer();
        consumer.setUuid("test_consumer_" + id);
        consumer.setId("test_consumer_" + id);
        return consumer;
    }

}
