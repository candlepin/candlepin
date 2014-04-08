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

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.candlepin.config.Config;
import org.candlepin.guice.AMQPBusPubProvider;
import org.junit.Before;
import org.junit.Test;

import javax.jms.TopicSession;
/**
 * AMQPBusPublisherTest
 */
public class AMQPBusPublisherTest {

    private ObjectMapper mapper;
    private TopicSession session;
    private AMQPBusPublisher publisher;

    @Before
    public void init() {
        Config config = new Config();
        mapper = new ObjectMapper();

        AMQPBusPubProvider provider = new AMQPBusPubProvider(config, mapper);
        session = mock(TopicSession.class);

        publisher = provider.get();
    }

    @Test
    public void testClose() {
        publisher.close();
    }

}
