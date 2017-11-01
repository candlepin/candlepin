/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Pool;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EventBuilderTest {

    private EventFactory factory;
    private EventBuilder eventBuilder;
    private PrincipalProvider principalProvider;

    @Before
    public void init() throws Exception {
        principalProvider = mock(PrincipalProvider.class);
        Principal principal = mock(Principal.class);
        factory = new EventFactory(principalProvider);
        when(principalProvider.get()).thenReturn(principal);
    }

    @Test
    public void testSetEventDataForPoolSetsEventDataJson() {
        Pool pool = mock(Pool.class);
        eventBuilder = new EventBuilder(factory, Event.Target.POOL, Event.Type.CREATED);

        when(pool.getSubscriptionId()).thenReturn("test-subscription-id");

        String expectedEventData = "{\"subscriptionId\":\"test-subscription-id\"}";
        Event event = eventBuilder.setEventData(pool).buildEvent();
        assertEquals(expectedEventData, event.getEventData());
    }
}
