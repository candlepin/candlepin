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
package org.candlepin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.Principal;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class EventFactoryTest {
    private ConsumerTypeCurator mockConsumerTypeCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private OwnerCurator mockOwnerCurator;
    private ModelTranslator modelTranslator;

    private PrincipalProvider principalProvider;

    private EventFactory eventFactory;

    @BeforeEach
    public void init() throws Exception {
        principalProvider = mock(PrincipalProvider.class);
        Principal principal = mock(Principal.class);
        when(principalProvider.get()).thenReturn(principal);

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.modelTranslator = new StandardTranslator(this.mockConsumerTypeCurator,
            this.mockEnvironmentCurator, this.mockOwnerCurator);

        eventFactory = new EventFactory(principalProvider, this.modelTranslator);
    }

    @Test
    public void testGuestIdCreation() {
        // this test is testing bz 786730, to ensure
        // the virt-who error does not occur
        Consumer consumer = mock(Consumer.class);
        GuestId guestId = mock(GuestId.class);

        when(guestId.getConsumer()).thenReturn(consumer);
        when(guestId.getGuestId()).thenReturn("guest-id");
        when(guestId.getId()).thenReturn("test");
        when(consumer.getOwnerId()).thenReturn("owner-id");
        when(consumer.getId()).thenReturn("consumer-id");

        Event event = eventFactory.guestIdCreated(guestId);
        assertNotNull(event.getEntityId());
    }

    @Test
    public void testComplianceCreatedSetsEventData() {
        Consumer consumer = mock(Consumer.class);
        ComplianceStatus status = mock(ComplianceStatus.class);

        when(consumer.getName()).thenReturn("consumer-name");
        when(consumer.getOwnerId()).thenReturn("owner-id");
        when(consumer.getUuid()).thenReturn("48b09f4e-f18c-4765-9c41-9aed6f122739");
        when(status.getStatus()).thenReturn("invalid");

        ComplianceReason reason1 = new ComplianceReason();
        reason1.setKey(ComplianceReason.ReasonKeys.SOCKETS);
        String expectedReason1Message = "Only supports 2 of 12 sockets.";
        reason1.setMessage(expectedReason1Message);
        String reason1MarketingName = "Awesome OS";
        reason1.setAttributes(ImmutableMap.of(ComplianceReason.Attributes.MARKETING_NAME,
            reason1MarketingName));

        ComplianceReason reason2 = new ComplianceReason();
        reason2.setKey(ComplianceReason.ReasonKeys.ARCHITECTURE);
        String expectedReason2Message = "Supports architecture ppc64 but the system is x86_64.";
        reason2.setMessage(expectedReason2Message);
        String reason2MarketingName = "Awesome Middleware";
        reason2.setAttributes(ImmutableMap.of(
            ComplianceReason.Attributes.MARKETING_NAME,
            reason2MarketingName));

        when(status.getReasons()).thenReturn(ImmutableSet.of(reason1, reason2));

        Map<String, String> expectedReason1 =
            Map.of("productName", reason1MarketingName, "message", expectedReason1Message);
        Map<String, String> expectedReason2 =
            Map.of("productName", reason2MarketingName, "message", expectedReason2Message);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("status", "invalid");
        expectedData.put("reasons", List.of(expectedReason1, expectedReason2));

        Event event = eventFactory.complianceCreated(consumer, status);

        assertThat(event.getEventData())
            .isNotNull()
            .containsAllEntriesOf(expectedData);
    }

    @Test
    public void testSyspurposeComplianceCreatedSetsEventData() {
        Consumer consumer = mock(Consumer.class);
        SystemPurposeComplianceStatus status = mock(SystemPurposeComplianceStatus.class);

        when(consumer.getName()).thenReturn("consumer-name");
        when(consumer.getOwnerId()).thenReturn("owner-id");
        when(consumer.getUuid()).thenReturn("48b09f4e-f18c-4765-9c41-9aed6f122739");
        when(status.getStatus()).thenReturn("mismatched");

        String reason1 = "unsatisfied usage: Production";
        String reason2 = "unsatisfied sla: Premium";
        String reason3 = "unsatisfied role: Red Hat Enterprise Linux Server";
        String reason4 = "unsatisfied service type: L1-L3";

        when(status.getReasons()).thenReturn(ImmutableSet.of(reason1, reason2, reason3, reason4));
        when(status.getNonCompliantRole()).thenReturn("Red Hat Enterprise Linux Server");
        when(status.getNonCompliantAddOns()).thenReturn(new HashSet<>());
        when(status.getNonCompliantSLA()).thenReturn("Premium");
        when(status.getNonCompliantUsage()).thenReturn("Production");
        when(status.getNonCompliantServiceType()).thenReturn("L1-L3");

        Map<String, Set<Entitlement>> entitlements = new HashMap<>();
        when(status.getCompliantRole()).thenReturn(entitlements);
        when(status.getCompliantAddOns()).thenReturn(entitlements);
        when(status.getCompliantSLA()).thenReturn(entitlements);
        when(status.getCompliantUsage()).thenReturn(entitlements);
        when(status.getCompliantServiceType()).thenReturn(entitlements);

        Set<String> expectedReasons = Set.of("unsatisfied usage: Production",
            "unsatisfied sla: Premium",
            "unsatisfied role: Red Hat Enterprise Linux Server",
            "unsatisfied service type: L1-L3");

        Map<String, Object> expectedEventData = new HashMap<>();
        expectedEventData.put("nonCompliantUsage", "Production");
        expectedEventData.put("nonCompliantRole", "Red Hat Enterprise Linux Server");
        expectedEventData.put("nonCompliantServiceType", "L1-L3");
        expectedEventData.put("compliantSLA", Map.of());
        expectedEventData.put("nonCompliantAddOns", Set.of());
        expectedEventData.put("compliantRole", Map.of());
        expectedEventData.put("nonCompliantSLA", "Premium");
        expectedEventData.put("compliantUsage", Map.of());
        expectedEventData.put("status", "mismatched");
        expectedEventData.put("compliantServiceType", Map.of());
        expectedEventData.put("reasons", expectedReasons);

        Event event = eventFactory.complianceCreated(consumer, status);

        assertThat(event.getEventData())
            .isNotNull()
            .containsAllEntriesOf(expectedEventData);
    }

    @Test
    public void testOwnerContentAccessModeChanged() {
        Owner owner = mock(Owner.class);

        when(owner.getName()).thenReturn("owner-name");
        when(owner.getOwnerId()).thenReturn("owner-id");
        when(owner.getContentAccessMode()).thenReturn("org_environment");

        Event event = eventFactory.ownerContentAccessModeChanged(owner);
        assertEquals(Target.OWNER_CONTENT_ACCESS_MODE, event.getTarget());
        assertEquals(Type.MODIFIED, event.getType());
        assertEquals(owner.getName(), event.getTargetName());
        assertEquals(owner.getOwnerKey(), event.getOwnerKey());
        assertEquals(Map.of("contentAccessMode", "org_environment"), event.getEventData());
    }
}
