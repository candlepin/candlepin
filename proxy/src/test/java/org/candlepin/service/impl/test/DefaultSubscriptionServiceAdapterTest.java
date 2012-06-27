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
package org.candlepin.service.impl.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;

import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DefaultSubscriptionServiceAdapterTest {

    @Test
    public void activationPrefix() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("mega");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("megaman");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(null, config, null, null);

        assertTrue(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixFailure() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("mega");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("superman");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(null, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixEmpty() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("anything");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(null, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixNull() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn(null);

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("anything");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(null, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

}
