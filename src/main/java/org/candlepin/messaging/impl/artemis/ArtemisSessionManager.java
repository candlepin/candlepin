package org.candlepin.messaging.impl.artemis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ArtemisSessionManager implements CPMSessionManager, CloseListener {
    private static Logger log = LoggerFactory.getLogger(ArtemisSessionManager.class);

    private Configuration config;
    private ServerLocator locator;

    private ClientSessionFactory sessionFactory;
    private Set<CPMSession> sessions = new HashSet<>();

    @Inject
    public ArtemisSessionManager(Configuration config) throws Exception {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void initialize() throws CPMException {
        try {
            String brokerUrl = this.config.getString(ConfigProperties.ACTIVEMQ_BROKER_URL);
            log.info("Connecting to Artemis server at {}", brokerUrl);

            this.locator = ActiveMQClient.createServerLocator(brokerUrl);
            if (locator == null) {
                throw new IllegalArgumentException("locator is null");
            }

            this.sessionFactory = this.locator.createSessionFactory();
            this.sessionFactory.getConnection().addCloseListener(this);
        }
        catch (Exception e) {
            throw new CPMException(e);
        }
    }

    @Override
    public CPMSession createSession(boolean transactional) throws CPMException {
        try {
            ClientSession clientSession = transactional ?
                this.sessionFactory.createTransactedSession() :
                this.sessionFactory.createSession();

            ArtemisSession session = new ArtemisSession(clientSession);
            sessions.add(session);

            return session;
        }
        catch (Exception e) {
            throw new CPMException(e);
        }
    }

    @Override
    public void connectionClosed() {
        this.closeAllSessions();
    }

    @Override
    public boolean closeAllSessions() {
        log.info("Closing all sessions for Artemis connection");

        boolean successful = true;
        for (CPMSession session : sessions) {
            if (session == null) {
                continue;
            }

            try {
                session.close();
            }
            catch (CPMException e) {
                log.error("Unable to close session", e);
                successful = false;
            }
        }

        return successful;
    }

}
