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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.TestingModules;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



public class ActivationListenerTest {

    private ActivationListener listener;
    private SubscriptionServiceAdapter subscriptionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void init() {
        Configuration config = TestConfig.defaults();
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.StandardTest(config),
            new TestingModules.ServletEnvironmentModule());
        objectMapper = injector.getInstance(ObjectMapper.class);
        subscriptionService = mock(ImportSubscriptionServiceAdapter.class);
        listener = new ActivationListener(subscriptionService, objectMapper);
    }

    @Test
    public void testActivationEmailIsSentWithCorrectSubscriptionId() {
        Event event = mock(Event.class);
        when(event.getTarget()).thenReturn(Event.Target.POOL);
        when(event.getType()).thenReturn(Event.Type.CREATED);
        when(event.getEventData()).thenReturn("{\"subscriptionId\":\"sub-id-1\"}");
        listener.onEvent(event);
        verify(subscriptionService, times(1)).sendActivationEmail("sub-id-1");
    }
}
