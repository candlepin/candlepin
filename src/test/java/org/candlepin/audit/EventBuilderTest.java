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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.exceptions.IseException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;


public class EventBuilderTest {
    private ConsumerTypeCurator mockConsumerTypeCurator;
    private EnvironmentCurator mockEnvironmentCurator;
    private OwnerCurator mockOwnerCurator;
    private ModelTranslator modelTranslator;

    private EventFactory factory;
    private EventBuilder eventBuilder;
    private PrincipalProvider principalProvider;

    @BeforeEach
    public void init() throws Exception {
        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockEnvironmentCurator = mock(EnvironmentCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.modelTranslator = new StandardTranslator(this.mockConsumerTypeCurator,
            this.mockEnvironmentCurator, this.mockOwnerCurator);

        principalProvider = mock(PrincipalProvider.class);
        Principal principal = mock(Principal.class);

        factory = new EventFactory(principalProvider, this.modelTranslator);
        when(principalProvider.get()).thenReturn(principal);
    }

    @Test
    public void testSetEventDataForPoolSetsEventDataJson() {
        Pool pool = mock(Pool.class);
        eventBuilder = new EventBuilder(factory, Event.Target.POOL, Event.Type.CREATED);

        String expectedSubId = "test-subscription-id";
        when(pool.getSubscriptionId()).thenReturn(expectedSubId);
        String expectedOwnerKey = TestUtil.randomString();
        doReturn(expectedOwnerKey).when(pool).getOwnerKey();

        Map<String, Object> expectedEventData = Map.of("subscriptionId", expectedSubId);
        Event event = eventBuilder.setEventData(pool).buildEvent();
        assertEquals(expectedEventData, event.getEventData());
        assertEquals(expectedOwnerKey, event.getOwnerKey());
    }

    @Test
    public void testSetEventDataWithContentAccessModeModification() {
        String expectedOwnerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setKey(expectedOwnerKey);
        owner.setId(TestUtil.randomString());
        owner.setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        eventBuilder = new EventBuilder(factory, Event.Target.OWNER_CONTENT_ACCESS_MODE, Event.Type.MODIFIED);
        Map<String, Object> expectedData =
            Map.of("contentAccessMode", ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        Event event = eventBuilder
            .setEventData(owner)
            .buildEvent();

        assertEquals(expectedOwnerKey, event.getOwnerKey());
        assertThat(event)
            .returns(expectedOwnerKey, Event::getOwnerKey)
            .extracting(Event::getEventData, as(map(String.class, Object.class)))
            .containsAllEntriesOf(expectedData);
    }

    @Test
    public void testSetEventDataForNonModifiedEventsThrowsIesException() {
        Pool pool = mock(Pool.class);
        eventBuilder = new EventBuilder(factory, Event.Target.POOL, Event.Type.CREATED);

        IseException e = assertThrows(IseException.class, () -> eventBuilder.setEventData(pool, pool));
        assertEquals("This method is only for type MODIFIED Events.", e.getMessage());
    }
}
