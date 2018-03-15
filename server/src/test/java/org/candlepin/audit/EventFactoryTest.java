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
package org.candlepin.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.junit.Before;
import org.junit.Test;


/**
 * EventFactoryTest
 */

public class EventFactoryTest {
    private PrincipalProvider principalProvider;

    private EventFactory eventFactory;

    @Before
    public void init() throws Exception {
        principalProvider = mock(PrincipalProvider.class);
        Principal principal = mock(Principal.class);
        when(principalProvider.get()).thenReturn(principal);
        eventFactory = new EventFactory(principalProvider);
    }

    @Test
    public void testGuestIdCreation() throws Exception {
        // this test is testing bz 786730, to ensure
        // the virt-who error does not occur
        Consumer consumer = mock(Consumer.class);
        GuestId guestId = mock(GuestId.class);
        Owner owner = mock(Owner.class);

        when(guestId.getConsumer()).thenReturn(consumer);
        when(guestId.getGuestId()).thenReturn("guest-id");
        when(guestId.getId()).thenReturn("test");
        when(consumer.getOwner()).thenReturn(owner);
        when(consumer.getId()).thenReturn("consumer-id");
        when(owner.getId()).thenReturn("owner-id");

        Event event = eventFactory.guestIdCreated(guestId);
        assertNotNull(event.getEntityId());
    }

    @Test
    public void testComplianceCreatedSetsEventData() {
        Consumer consumer = mock(Consumer.class);
        Owner owner = mock(Owner.class);
        ComplianceStatus status = mock(ComplianceStatus.class);

        when(consumer.getName()).thenReturn("consumer-name");
        when(consumer.getOwner()).thenReturn(owner);
        when(consumer.getUuid()).thenReturn("48b09f4e-f18c-4765-9c41-9aed6f122739");
        when(status.getStatus()).thenReturn("invalid");

        ComplianceReason reason1 = new ComplianceReason();
        reason1.setKey(ComplianceReason.ReasonKeys.SOCKETS);
        reason1.setMessage("Only supports 2 of 12 sockets.");
        reason1.setAttributes(ImmutableMap.of(ComplianceReason.Attributes.MARKETING_NAME, "Awesome OS"));

        ComplianceReason reason2 = new ComplianceReason();
        reason2.setKey(ComplianceReason.ReasonKeys.ARCHITECTURE);
        reason2.setMessage("Supports architecture ppc64 but the system is x86_64.");
        reason2.setAttributes(ImmutableMap.of(
            ComplianceReason.Attributes.MARKETING_NAME,
            "Awesome Middleware"
        ));

        when(status.getReasons()).thenReturn(ImmutableSet.of(reason1, reason2));

        String expectedEventData = "{\"reasons\":[" +
            "{\"productName\":\"Awesome OS\"," +
            "\"message\":\"Only supports 2 of 12 sockets.\"}," +
            "{\"productName\":\"Awesome Middleware\"," +
            "\"message\":\"Supports architecture ppc64 but the system is x86_64.\"}]," +
            "\"consumer_uuid\":\"48b09f4e-f18c-4765-9c41-9aed6f122739\",\"status\":\"invalid\"}";
        Event event = eventFactory.complianceCreated(consumer, status);
        assertEquals(expectedEventData, event.getEventData());
    }
}
