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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * EventSource
 */
public class EventSource {
    private static  Logger log = LoggerFactory.getLogger(EventSource.class);
    static final String QUEUE_ADDRESS = "event";
    private ClientSessionFactory factory;
    private ObjectMapper mapper;
    private List<EventReceiver> eventReceivers = new LinkedList<>();


    @Inject
    public EventSource(ObjectMapper mapper) {
        this.mapper = mapper;

        try {
            factory =  createSessionFactory();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return new instance of {@link ClientSessionFactory}
     * @throws Exception
     */
    protected ClientSessionFactory createSessionFactory() throws Exception {
        return ActiveMQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName())).createSessionFactory();
    }

    protected void shutDown() {
        closeEventReceivers();
        factory.close();
    }

    void registerListener(EventListener listener) throws Exception {
        this.eventReceivers.add(new EventReceiver(listener, factory, mapper));
    }

    private void closeEventReceivers() {
        this.eventReceivers.forEach(EventReceiver::close);
    }
}
