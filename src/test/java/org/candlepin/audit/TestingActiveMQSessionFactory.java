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

import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.config.Configuration;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;

/**
 * A test class for overriding/mocking the ActiveMQ client session creation/utilization.
 * When multiple ingress/egress client sessions are expected to be generated, the passed
 * mocks must set expectations on what should be done when createSession is called.
 */
public class TestingActiveMQSessionFactory extends ActiveMQSessionFactory {

    private ClientSessionFactory egressSessionFactory;
    private ClientSessionFactory ingressSessionFactory;

    public TestingActiveMQSessionFactory(ClientSessionFactory ingressFactoryMock,
        ClientSessionFactory egressFactoryMock) {
        super(mock(Configuration.class));
        this.ingressSessionFactory = ingressFactoryMock;
        this.egressSessionFactory = egressFactoryMock;
    }

    @Override
    public ClientSession getIngressSession(boolean transacted) throws Exception {
        return ingressSessionFactory.createSession();
    }

    @Override
    public ClientSession getEgressSession(boolean transacted) throws Exception {
        return egressSessionFactory.createSession();
    }
}
