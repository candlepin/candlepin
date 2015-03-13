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

package org.candlepin.gutterball.receiver;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigProperties;

import org.apache.qpid.AMQException;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;

import javax.jms.JMSException;

public class EventReceiverTests {

    private static int callCount = 0;

    @Before
    public void before() {
        callCount = 0;
    }

    @Test
    public void ensureConnectionRetryOnStartUp() {
        Configuration config = mock(Configuration.class);
        when(config.getString(any(String.class))).thenReturn("");
        when(config.getInt(ConfigProperties.AMQP_CONNECTION_RETRY_ATTEMPTS)).thenReturn(2);
        when(config.getInt(ConfigProperties.AMQP_CONNECTION_RETRY_INTERVAL)).thenReturn(1);

        try {
            new EventReceiver(config, null) {

                @Override
                protected void init(Configuration config) throws AMQException,
                        JMSException, URISyntaxException {
                    callCount++;
                    throw new JMSException("Forced failure");
                }

            };
            fail("Exception should have been thrown once max retries was hit.");
        }
        catch (Exception e) {
            assertEquals(2, callCount);
        }
    }

}
