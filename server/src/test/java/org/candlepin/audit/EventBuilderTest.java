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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;



public class EventBuilderTest {
    private ConsumerTypeCurator mockConsumerTypeCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private OwnerCurator mockOwnerCurator;
    private ModelTranslator modelTranslator;

    private EventFactory factory;
    private EventBuilder eventBuilder;
    private PrincipalProvider principalProvider;


    @Rule
    public ExpectedException exceptions = ExpectedException.none();

    @Before
    public void init() throws Exception {
        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.modelTranslator = new StandardTranslator(this.mockConsumerTypeCurator,
            this.mockEnvironmentCurator, this.mockOwnerCurator);

        principalProvider = mock(PrincipalProvider.class);
        Principal principal = mock(Principal.class);

        factory = new EventFactory(principalProvider, new ObjectMapper(), this.modelTranslator);
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

    @Test
    public void testSetEventDataForNonModifiedEventsThrowsIesException() {
        Pool pool = mock(Pool.class);
        eventBuilder = new EventBuilder(factory, Event.Target.POOL, Event.Type.CREATED);

        exceptions.expect(IseException.class);
        exceptions.expectMessage("This method is only for type MODIFIED Events.");
        eventBuilder.setEventData(pool, pool);
    }
}
