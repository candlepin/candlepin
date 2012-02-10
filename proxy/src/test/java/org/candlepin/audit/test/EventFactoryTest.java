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
package org.candlepin.audit.test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
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

        when(guestId.getGuestId()).thenReturn("guest-id");
        when(guestId.getId()).thenReturn(null);
        when(consumer.getOwner()).thenReturn(owner);
        when(consumer.getId()).thenReturn("consumer-id");
        when(owner.getId()).thenReturn("owner-id");

        Event event = eventFactory.guestIdCreated(consumer, guestId);
        assertNotNull(event.getEntityId());
    }

}
