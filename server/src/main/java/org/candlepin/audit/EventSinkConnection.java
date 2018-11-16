/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ActiveMQ connection that is configured for the EventSink. This connection will
 * will continuously attempt reconnects to the broker if the connection is lost. This
 * is to ensure that message sends can survive while a broker is restarted so long as
 * candlepin remains up.
 *
 * NOTE: This is legacy behavior and could likely be better supported by JTA in the
 *       future.
 */
@Singleton
public class EventSinkConnection extends ActiveMQConnection {
    private static Logger log = LoggerFactory.getLogger(EventSinkConnection.class);
    private int largeMessageSize;

    @Inject
    public EventSinkConnection(Configuration config) {
        super(config);
        this.largeMessageSize = config.getInt(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE);
    }

    @Override
    ServerLocator initLocator() throws Exception {
        ServerLocator locator = ActiveMQClient.createServerLocator(serverUrl);
        locator.setMinLargeMessageSize(this.largeMessageSize);
        locator.setReconnectAttempts(-1);
        return locator;
    }

    @Override
    ClientSessionFactory initClientSessionFactory(ServerLocator locator) throws Exception {
        return locator.createSessionFactory();
    }

    @Override
    ClientSession createClientSession() throws ActiveMQException {
        return getFactory().createTransactedSession();
    }

}
