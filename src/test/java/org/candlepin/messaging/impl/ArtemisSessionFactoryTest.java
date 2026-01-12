/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.messaging.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import org.candlepin.messaging.impl.artemis.ArtemisSessionFactory;
import org.candlepin.test.DatabaseTestFixture;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ArtemisSessionFactoryTest extends DatabaseTestFixture {

    private ArtemisSessionFactory sessionFactory;

    @Mock
    private ServerLocator locator;

    @BeforeEach
    public void beforeEach() {
        this.sessionFactory = this.injector.getInstance(ArtemisSessionFactory.class);
    }

    @Test
    public void shouldSetConsumerWindowSizeDuringInitialization() throws Exception {
        try (MockedStatic<ActiveMQClient> mocked = mockStatic(ActiveMQClient.class)) {
            mocked.when(() -> ActiveMQClient.createServerLocator(any(String.class)))
                .thenReturn(locator);

            this.sessionFactory.initialize();
        }

        verify(locator).setConsumerWindowSize(0);
    }

}

