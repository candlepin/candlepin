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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementEnvironmentFilterTest {
    @Mock private EnvironmentContentCurator environmentContentCurator;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private Consumer consumer;

    @Test
    public void filterEntitlementWhenEnvironmentBeingAddedTest() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2", "c3"));

        when(this.entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1"));
        id.put("envB", Arrays.asList("c2"));
        id.put("envC", Arrays.asList("c1"));

        when(this.environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);

        // EnvB added with Higher priority
        // EnvA & EnvB both have different content
        Set<String> filteredEnt1 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA"))
            .setUpdatedEnvironment(Arrays.asList("envB", "envA"))
            .filterEntitlements();

        assertEquals(filteredEnt1.size(), 1);
        assertTrue(filteredEnt1.contains("Dummy-ent"));

        // EnvB added with Lower priority
        // EnvA & EnvB both have different content
        Set<String> filteredEnt2 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA"))
            .setUpdatedEnvironment(Arrays.asList("envA", "envB"))
            .filterEntitlements();
        assertEquals(filteredEnt2.size(), 1);
        assertTrue(filteredEnt2.contains("Dummy-ent"));


        // EnvC is added with lower priority
        // EnvA (high) & EnvC (low) both have same content
        Set<String> filteredEnt3 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA"))
            .setUpdatedEnvironment(Arrays.asList("envA", "envC"))
            .filterEntitlements();
        assertEquals(filteredEnt3.size(), 0);

        // EnvC is added with Higher priority
        // EnvA (low) & EnvC (high) both have same content
        Set<String> filteredEnt4 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA"))
            .setUpdatedEnvironment(Arrays.asList("envC", "envA"))
            .filterEntitlements();
        assertEquals(filteredEnt4.size(), 1);
        assertTrue(filteredEnt4.contains("Dummy-ent"));
    }

    @Test
    public void filterEntitlementWhenEnvironmentBeingRemovedTest() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2", "c3"));

        when(entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1"));
        id.put("envB", Arrays.asList("c2"));
        id.put("envC", Arrays.asList("c1"));

        when(environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);
        // EnvA of higher priority is being removed
        // envA & envB have different content
        Set<String> filteredEnt1 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB"))
            .setUpdatedEnvironment(Arrays.asList("envB"))
            .filterEntitlements();
        assertEquals(filteredEnt1.size(), 1);
        assertTrue(filteredEnt1.contains("Dummy-ent"));

        // EnvB of lower priority is being removed
        // envA & envB have different content
        Set<String> filteredEnt2 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB"))
            .setUpdatedEnvironment(Arrays.asList("envA"))
            .filterEntitlements();
        assertEquals(filteredEnt2.size(), 1);
        assertTrue(filteredEnt2.contains("Dummy-ent"));

        // EnvC of lower priority is being removed
        // EnvC provides content same as envA
        Set<String> filteredEnt3 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB", "envC"))
            .setUpdatedEnvironment(Arrays.asList("envA", "envB"))
            .filterEntitlements();
        assertEquals(filteredEnt3.size(), 0);

        // EnvC of higher priority is being removed
        // EnvC provides content same as envA
        Set<String> filteredEnt4 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envC", "envA"))
            .setUpdatedEnvironment(Arrays.asList("envA"))
            .filterEntitlements();
        assertEquals(filteredEnt4.size(), 1);
        assertTrue(filteredEnt4.contains("Dummy-ent"));

        // EnvC of Lower priority is being removed
        // EnvC provides content same as envA
        Set<String> filteredEnt5 =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envC"))
            .setUpdatedEnvironment(Arrays.asList("envA"))
            .filterEntitlements();
        assertEquals(filteredEnt5.size(), 0);
    }

    @Test
    public void testWhenLowerPriorityEnvsReorderedProvidingSameContent() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2", "c3"));

        when(entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1"));
        id.put("envB", Arrays.asList("c1"));
        id.put("envC", Arrays.asList("c1"));

        when(environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);

        // Reordering lower priority (envB & envC) environments
        // providing same contents
        Set<String> filteredEnt =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB", "envC"))
            .setUpdatedEnvironment(Arrays.asList("envA", "envC", "envB"))
            .filterEntitlements();
        assertEquals(filteredEnt.size(), 0);
    }

    @Test
    public void testWhenEnvsAreReorderedProvidingDifferentContent() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2", "c3"));

        when(entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1"));
        id.put("envB", Arrays.asList("c2"));

        when(environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);

        // Priority of envB & envA are reversed
        Set<String> filteredEnt =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB"))
            .setUpdatedEnvironment(Arrays.asList("envB", "envA"))
            .filterEntitlements();
        assertEquals(filteredEnt.size(), 1);
        assertTrue(filteredEnt.contains("Dummy-ent"));
    }

    @Test
    public void testWhenHigherPriorityEnvironmentIsDeleted() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2"));

        when(entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1", "c2"));
        id.put("envB", Arrays.asList("c1", "c2"));

        when(environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);

        Set<String> filteredEnt =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA", "envB"))
            .setUpdatedEnvironment(Arrays.asList("envB"))
            .filterEntitlements();
        assertEquals(filteredEnt.size(), 1);
        assertTrue(filteredEnt.contains("Dummy-ent"));
    }

    @Test
    public void testEnvBeingAddedHasNoContent() {
        Entitlement ent = new Entitlement();
        ent.setId("Dummy-ent");
        Map<String, Set<String>> contentUUIDsConsumerIds = new HashMap<>();
        contentUUIDsConsumerIds.put(ent.getId(), Set.of("c1", "c2"));

        when(entitlementCurator.getEntitlementContentUUIDs(Arrays.asList(consumer.getId())))
            .thenReturn(contentUUIDsConsumerIds);

        Map<String, List<String>> id = new HashMap<>();
        id.put("envA", Arrays.asList("c1", "c2"));
        id.put("envB", new ArrayList<>());

        when(environmentContentCurator.getEnvironmentContentUUIDs(any())).thenReturn(id);

        Set<String> filteredEnt =
            new EntitlementEnvironmentFilter(this.entitlementCurator, this.environmentContentCurator)
            .setConsumerToBeUpdated(Arrays.asList(consumer.getId()))
            .setPreExistingEnvironments(Arrays.asList("envA"))
            .setUpdatedEnvironment(Arrays.asList("envB", "envA"))
            .filterEntitlements();

        assertEquals(filteredEnt.size(), 0);
    }
}
