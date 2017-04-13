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
package org.candlepin.controller;

import com.google.inject.Provider;
import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidConnection.STATUS;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidQmf.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.cache.StatusCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.BrokerState;
import org.candlepin.model.CandlepinModeChange.DbState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.Query;

@RunWith(MockitoJUnitRunner.class)
public class SuspendModeTransitionerTest {
    private SuspendModeTransitioner transitioner;
    @Mock
    private ModeManager modeManager;
    @Mock
    private QpidQmf qmf;
    @Mock
    private QpidConnection qpidConnection;
    @Mock
    private ScheduledExecutorService execService;
    @Mock
    private CandlepinCache candlepinCache;
    @Mock
    private StatusCache cache;
    @Mock
    private Provider<EntityManager> entityManagerProvider;
    @Mock
    private Configuration candlepinConfig;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;
    private CandlepinModeChange brokerUpDbUpChange;
    private CandlepinModeChange brokerOffDbUpChange;
    private CandlepinModeChange brokerUpDbDownChange;
    private CandlepinModeChange brokerDownDbDownChange;
    private CandlepinModeChange brokerFlowStoppedDbDownChange;
    private CandlepinModeChange brokerFlowStoppedDbUpChange;
    private CandlepinModeChange brokerDownDbUpChange;

    @Before
    public void setUp() {
        Mockito.when(candlepinCache.getStatusCache()).thenReturn(cache);
        Mockito.when(candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)).thenReturn(true);
        CandlepinCommonTestConfig testConfig = new CandlepinCommonTestConfig();
        transitioner = new SuspendModeTransitioner(testConfig, execService,
            candlepinCache, entityManagerProvider, candlepinConfig);
        transitioner.setModeManager(modeManager);
        transitioner.setQmf(qmf);
        transitioner.setQpidConnection(qpidConnection);
        transitioner = Mockito.spy(transitioner);

        brokerUpDbUpChange = new CandlepinModeChange(new Date(), Mode.NORMAL, BrokerState.UP, DbState.UP);
        brokerOffDbUpChange = new CandlepinModeChange(new Date(), Mode.NORMAL, BrokerState.OFF, DbState.UP);
        brokerUpDbDownChange =
                new CandlepinModeChange(new Date(), Mode.SUSPEND, BrokerState.UP, DbState.DOWN);
        brokerDownDbDownChange =
                new CandlepinModeChange(new Date(), Mode.SUSPEND, BrokerState.DOWN, DbState.DOWN);
        brokerDownDbUpChange =
                new CandlepinModeChange(new Date(), Mode.SUSPEND, BrokerState.DOWN, DbState.UP);
        brokerFlowStoppedDbDownChange =
                new CandlepinModeChange(new Date(), Mode.SUSPEND, BrokerState.FLOW_STOPPED, DbState.DOWN);
        brokerFlowStoppedDbUpChange =
                new CandlepinModeChange(new Date(), Mode.SUSPEND, BrokerState.FLOW_STOPPED, DbState.UP);

        Mockito.when(entityManagerProvider.get()).thenReturn(entityManager);
        Mockito.when(entityManager.createNativeQuery("SELECT 1")).thenReturn(query);
        Mockito.when(query.getSingleResult()).thenReturn(1);
    }

    @Test
    public void normalConnected() {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        Mockito.doReturn(true).when(transitioner).isDbConnected();
        Mockito.when(modeManager.getLastCandlepinModeChange()).thenReturn(brokerUpDbUpChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
                .stateChanged(brokerUpDbUpChange, BrokerState.UP, DbState.UP);
        Mockito.verifyNoMoreInteractions(execService, modeManager, qmf);
    }

    @Test
    public void stillDisconnected() {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        Mockito.doReturn(true).when(transitioner).isDbConnected();
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerDownDbUpChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
                .stateChanged(brokerDownDbUpChange, BrokerState.DOWN, DbState.UP);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, modeManager, qmf);
    }


    @Test
    public void transitionFromDownToConnected() throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.CONNECTED);
        Mockito.doReturn(true).when(transitioner).isDbConnected();
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerDownDbUpChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
                .enterMode(Mode.NORMAL, BrokerState.UP, DbState.UP);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToDown()
        throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.DOWN);
        Mockito.doReturn(true).when(transitioner).isDbConnected();
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerUpDbUpChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
            .enterMode(Mode.SUSPEND, BrokerState.DOWN, DbState.UP);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void transitionFromConnectedToFlowStopped()
        throws Exception {
        Mockito.when(qmf.getStatus()).thenReturn(QpidStatus.FLOW_STOPPED);
        Mockito.doReturn(true).when(transitioner).isDbConnected();
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerUpDbUpChange);

        transitioner.transitionAppropriately();

        Mockito.verify(qpidConnection, Mockito.times(1))
            .setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
        Mockito.verify(qmf, Mockito.times(1)).getStatus();
        Mockito.verify(modeManager, Mockito.times(1)).getLastCandlepinModeChange();
        Mockito.verify(modeManager, Mockito.times(1))
            .enterMode(Mode.SUSPEND, BrokerState.FLOW_STOPPED, DbState.UP);
        Mockito.verifyNoMoreInteractions(execService, qpidConnection, qmf, modeManager);
    }

    @Test
    public void backOff()
        throws Exception {
        Mockito.when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.DOWN)
            .thenReturn(QpidStatus.DOWN)
            .thenReturn(QpidStatus.CONNECTED);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerDownDbUpChange)
            .thenReturn(brokerUpDbUpChange)
            .thenReturn(brokerDownDbUpChange)
            .thenReturn(brokerDownDbUpChange);

        transitioner.run();

        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
        Mockito.reset(execService);

        transitioner.run();

        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 20L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 10L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
    }


    @Test
    public void backOffMaximum()
        throws Exception {
        Mockito.when(qmf.getStatus())
            .thenReturn(QpidStatus.CONNECTED)
            .thenReturn(QpidStatus.DOWN);
        Mockito.when(modeManager.getLastCandlepinModeChange())
            .thenReturn(brokerDownDbUpChange)
            .thenReturn(brokerUpDbUpChange)
            .thenReturn(brokerDownDbUpChange);

        for (int i = 0; i < 10; i++) {
            transitioner.run();
        }

        Mockito.reset(execService);
        transitioner.run();
        Mockito.verify(execService, Mockito.times(1))
            .schedule(transitioner, 100L, TimeUnit.SECONDS);
        Mockito.verifyNoMoreInteractions(execService);
    }
}
