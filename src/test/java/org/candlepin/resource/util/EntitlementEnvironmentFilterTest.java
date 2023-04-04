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
package org.candlepin.resource.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementEnvironmentFilterTest {
    private EntitlementCurator mockEntitlementCurator;
    private EnvironmentContentCurator mockEnvironmentContentCurator;

    private final Map<String, Set<String>> consumerEntitlementIdMap = new HashMap<>();
    private final Map<String, Set<String>> entitlementContentIdMap = new HashMap<>();
    private final Map<String, Set<String>> environmentContentIdMap = new HashMap<>();

    @BeforeEach
    public void setUp() {
        this.consumerEntitlementIdMap.clear();
        this.entitlementContentIdMap.clear();
        this.environmentContentIdMap.clear();

        this.mockEntitlementCurator = mock(EntitlementCurator.class);
        this.mockEnvironmentContentCurator = mock(EnvironmentContentCurator.class);

        doAnswer(iom -> {
            Map<String, String> output = new HashMap<>();
            Iterable<String> input = iom.getArgument(0);

            for (String consumerId : input) {
                this.consumerEntitlementIdMap.getOrDefault(consumerId, Set.of())
                    .forEach(entitlementId -> output.put(entitlementId, consumerId));
            }

            return output;
        }).when(this.mockEntitlementCurator).getEntitlementConsumerIdMap(anyIterable());

        doAnswer(this.buildMapAnswer(this.entitlementContentIdMap))
            .when(this.mockEntitlementCurator).getEntitlementContentIdMap(anyIterable());

        doAnswer(this.buildMapAnswer(this.environmentContentIdMap))
            .when(this.mockEnvironmentContentCurator).getEnvironmentContentIdMap(anyIterable());
    }

    private Answer<Map<String, Set<String>>> buildMapAnswer(Map<String, Set<String>> source) {
        return iom -> {
            Map<String, Set<String>> output = new HashMap<>();
            Iterable<String> input = iom.getArgument(0);

            for (String element : input) {
                if (source.containsKey(element)) {
                    output.put(element, source.get(element));
                }
            }

            return output;
        };
    }

    private EntitlementEnvironmentFilter buildEEFilter() {
        return new EntitlementEnvironmentFilter(this.mockEntitlementCurator,
            this.mockEnvironmentContentCurator);
    }

    private void mapConsumerToEntitlements(String consumerId, String... entitlementIds) {
        for (String entitlementId : entitlementIds) {
            this.consumerEntitlementIdMap.computeIfAbsent(consumerId, (key) -> new HashSet<>())
                .add(entitlementId);
        }
    }

    private void mapEntitlementToContent(String entitlementId, String... contentIds) {
        for (String contentId : contentIds) {
            this.entitlementContentIdMap.computeIfAbsent(entitlementId, (key) -> new HashSet<>())
                .add(contentId);
        }
    }

    private void mapEnvironmentToContent(String environmentId, String... contentIds) {
        for (String contentId : contentIds) {
            this.environmentContentIdMap.computeIfAbsent(environmentId, (key) -> new HashSet<>())
                .add(contentId);
        }
    }


    @Test
    public void testEEFRequiresNonNullInput() {
        EntitlementEnvironmentFilter filter = this.buildEEFilter();

        assertThrows(IllegalArgumentException.class, () -> filter.filterEntitlements(null));
    }

    @Test
    public void testEEFReturnsEmptySetOnEmptyInput() {
        EntitlementEnvironmentFilter filter = this.buildEEFilter();

        Set<String> output = this.buildEEFilter().filterEntitlements(new EnvironmentUpdates());

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    // Tests for adding environments
    @Test
    public void testEEFReportsChangeWhenAddingEnvironmentWithNewEntitlementContent() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2", "c3");
        this.mapEntitlementToContent("ent2", "c1", "c2");
        this.mapEnvironmentToContent("env1", "c1");
        this.mapEnvironmentToContent("env2", "c2");
        this.mapEnvironmentToContent("env3", "c3");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2"), List.of("env1", "env2", "env3"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.contains("ent1"));
    }

    @Test
    public void testEEFReportsChangeWhenAddingEnvironmentProvidingExistingContentAtHigherPriority() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c3");
        this.mapEnvironmentToContent("env3", "c2");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2"), List.of("env3", "env1", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.contains("ent1"));
    }

    @Test
    public void testEEFIgnoresChangeWhenAddingEnvironmentProvidingExistingContentAtLowerPriority() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c3");
        this.mapEnvironmentToContent("env3", "c2");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2"), List.of("env1", "env2", "env3"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testEEFIgnoresChangeWhenAddingEnvironmentProvidingNoEntitlementContent() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c3");
        this.mapEnvironmentToContent("env3", "c4");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2"), List.of("env3", "env1", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    // Tests for changing environments (reordering/reprioritization)
    @Test
    public void testEEFReportsChangeWhenHighestPrioEnvProvidingContentChanges() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1");
        this.mapEnvironmentToContent("env2", "c1");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2"), List.of("env2", "env1"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.contains("ent1"));
    }

    @Test
    public void testEEFIgnoresChangeWhenHighestPrioEnvProvidingNoEntitlementContentChanges() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c4");
        this.mapEnvironmentToContent("env3", "c4");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env3", "env2"))
            .put("consumer2", List.of("env2", "env3", "env1"), List.of("env3", "env2", "env1"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testEEFIgnoresChangeWhenLowPriorityEnvsProvidingEntitlementContentChanges() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c2");
        this.mapEnvironmentToContent("env3", "c2");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env3", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testEEFIgnoresChangeWhenLowPriorityEnvsProvidingNoEntitlementContentChanges() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c4");
        this.mapEnvironmentToContent("env3", "c4");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env3", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    // Tests for removing environments
    @Test
    public void testEEFReportsChangeWhenOnlyEnvironmentProvidingEntitlementContentIsRemoved() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2", "ent3");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer3", "ent1");
        this.mapEntitlementToContent("ent1", "c1");
        this.mapEntitlementToContent("ent2", "c2");
        this.mapEntitlementToContent("ent3", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c1", "c2");
        this.mapEnvironmentToContent("env3", "c3");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertEquals(1, output.size());
        assertTrue(output.contains("ent3"));
    }

    @Test
    public void testEEFReportsChangeWhenHighestPriorityEnvProvidingContentIsRemoved() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2", "ent3");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer3", "ent1");
        this.mapEntitlementToContent("ent1", "c1");
        this.mapEntitlementToContent("ent2", "c2");
        this.mapEntitlementToContent("ent3", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2", "c3");
        this.mapEnvironmentToContent("env2", "c1", "c2");
        this.mapEnvironmentToContent("env3", "c3");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env2", "env3"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertEquals(3, output.size());
        assertEquals(Set.of("ent1", "ent2", "ent3"), output);
    }

    @Test
    public void testEEFIgnoresChangeWhenLowPriorityEnvProvidingContentIsRemoved() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c3");
        this.mapEnvironmentToContent("env3", "c1", "c2", "c3");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env2"))
            .put("consumer2", List.of("env3", "env2", "env1"), List.of("env3"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testEEFIgnoresChangeWhenEnvironmentProvidingNoEntitlementContentIsRemoved() {
        this.mapConsumerToEntitlements("consumer1", "ent1", "ent2");
        this.mapConsumerToEntitlements("consumer2", "ent1", "ent2");
        this.mapEntitlementToContent("ent1", "c1", "c2");
        this.mapEntitlementToContent("ent2", "c2", "c3");
        this.mapEnvironmentToContent("env1", "c1", "c2");
        this.mapEnvironmentToContent("env2", "c3");
        this.mapEnvironmentToContent("env3", "c4");

        EnvironmentUpdates updates = new EnvironmentUpdates()
            .put("consumer1", List.of("env1", "env2", "env3"), List.of("env1", "env2"));

        Set<String> output = this.buildEEFilter().filterEntitlements(updates);

        assertNotNull(output);
        assertTrue(output.isEmpty());
    }
}
