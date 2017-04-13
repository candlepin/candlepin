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
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.PooledDataSource;
import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidConnection.STATUS;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidQmf.QpidStatus;
import org.candlepin.cache.CandlepinCache;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.BrokerState;
import org.candlepin.model.CandlepinModeChange.DbState;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.math.BigInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logic to transition Candlepin between different modes (SUSPEND, NORMAL) based
 * on what is the current status of Qpid Broker.
 *
 * This logic can also be run periodically using startPeriodicExecutions
 *
 * Using this class, clients can attempt to transition to appropriate mode. The
 * attempt may be no-op if no transition is required.
 * @author fnguyen
 *
 */
public class SuspendModeTransitioner implements Runnable {
    private static Logger log = LoggerFactory.getLogger(SuspendModeTransitioner.class);
    /**
     * Factor by which the delay is going to be increased with each failed attempt to
     * reconnect to the Qpid Broker. For example if set to 3 and initialDelay set to
     * 5. The following will be the delay interval between the executions of this
     * transitioner:
     *
     * When failedAttempts = 0, then delay will be 5
     * When failedAttempts = 1, then the delay will be 10
     * When failedAttempts = 2, then the delay will be 15
     * etc.
     *
     */
    private int delayGrowth;
    /**
     * Delay in seconds. SuspendModeTransitioner will wait this amount
     * of seconds between periodic checks for Qpid connectivity.
     */
    private int initialDelay;
    /**
     * Maximum delay that this transitioner can wait between executions
     */
    private int maxDelay = 0;
    private BigInteger failedAttempts = BigInteger.ZERO;
    /**
     * Single threaded periodic task.
     */
    private ScheduledExecutorService execService;
    private ModeManager modeManager;
    private QpidQmf qmf;
    private QpidConnection qpidConnection;
    private CandlepinCache candlepinCache;
    private Provider<EntityManager> entityManager;
    private Configuration candlepinConfig;
    private PooledDataSource dataSource;

    @Inject
    public SuspendModeTransitioner(Configuration config, ScheduledExecutorService execService,
        CandlepinCache cache, Provider<EntityManager> entityManager, Configuration candlepinConfig) {
        this.execService = execService;
        delayGrowth = config.getInt(ConfigProperties.QPID_MODE_TANSITIONER_DELAY_GROWTH);
        initialDelay = config.getInt(ConfigProperties.QPID_MODE_TRANSITIONER_INITIAL_DELAY);
        maxDelay = config.getInt(ConfigProperties.QPID_MODE_TRANSITIONER_MAX_DELAY);
        this.candlepinCache = cache;
        this.entityManager = entityManager;
        this.candlepinConfig = candlepinConfig;
        dataSource = C3P0Registry.pooledDataSourceByName("hibernateDataSource");
    }

    /**
     * Other dependencies are injected using method injection so
     * that Guice can handle circular dependency between SuspendModeTransitioner
     * and the QpidConnection
     */
    @Inject
    public void setModeManager(ModeManager modeManager) {
        this.modeManager = modeManager;
    }

    @Inject
    public void setQmf(QpidQmf qmf) {
        this.qmf = qmf;
    }

    @Inject
    public void setQpidConnection(QpidConnection qpidConnection) {
        this.qpidConnection = qpidConnection;
    }

    /**
     * Enables to run the transitioning logic periodically.
     */
    public void startPeriodicExecutions() {
        log.info("Starting Periodic Suspend Mode Transitioner " +
            "with delay grow factor of {}s and initial delay {}s ",
            delayGrowth, initialDelay);
        schedule();
    }

    /**
     * Schedules next execution of the Suspend Mode check. The delay, in seconds,
     * grows as the failedAttempts grow. The formula is:
     *
     *  delay = initialDelay + (delayGrowth * failedAttempts)
     *
     *  Maximal delay is configurable, -1 means there is no bound
     *
     */
    private void schedule() {
        BigInteger delay =
            BigInteger.valueOf(initialDelay)
            .add(BigInteger.valueOf(delayGrowth)
            .multiply(failedAttempts));

        if (maxDelay != -1 &&
            delay.compareTo(BigInteger.valueOf(maxDelay)) > 0) {
            log.debug("Maximum delay {} reached", maxDelay);
            delay = BigInteger.valueOf(maxDelay);
        }

        log.debug("Next Transitioner check will run after {} seconds", delay);
        if (failedAttempts.compareTo(BigInteger.ZERO) > 0) {
            log.info("SuspendModeTransitioner failed to reconnect to the Qpid Broker or DB" +
                "{} times, backing off with next reconnect {} seconds", failedAttempts,
                delay);
        }
        execService.schedule(this, delay.longValue(), TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        log.debug("Executing periodic transition attempt");
        try {
            transitionAppropriately();
        }
        finally {
            /**
             * The Transitioner is periodic task. But we want to have flexibility of setting
             * different delays between executions. As the amount of failed atttempts to connect
             * rise, we want to also increase the delay. Scheduling in finally seems to be the
             * easiest way to achieve that.
             */
            schedule();
        }
    }

    /**
     * Tests the connection to the database by running a fast query.
     * To make the testing query as lightweight as possible, a DB specific SQL query is used.
     * TODO: Add Oracle support. The used query "SELECT 1" is implemented in mysql and postgres.
     *
     * @return true if the test query executed successfully, false otherwise.
     */
    public boolean isDbConnected() {
        log.debug("Testing database connection");
        boolean connectionIsValid = false;
        try {
            Integer result = (Integer) entityManager.get().createNativeQuery("SELECT 1").getSingleResult();
            log.debug("Database testing query result is \"{}\"", result);
            if (result == 1) {
                connectionIsValid = true;
            }
        }
        catch (Exception e) {
            log.warn("Error occurred while testing database connection, will switch to SUSPEND mode.", e);
        }

        return connectionIsValid;
    }

    /**
     * Attempts to transition Candlepin according to current Mode, current status of
     * the Qpid Broker, and the status of database connection.
     * Logs and swallows possible exceptions - theoretically there should be none.
     *
     * Most of the time the transition won't be required and this method will be no-op.
     * There is an edge-case when transitioning from SUSPEND to NORMAL mode.
     * During that transition, there is a small time window between checking the
     * Qpid status and attempt to reconnect. If the Qpid status is reported as
     * Qpid up, the transitioner will try to reconnect to the broker. This reconnect
     * may fail. In that case the transition to NORMAL mode shouldn't go through
     */
    public synchronized void transitionAppropriately() {
        try {
            QpidStatus qpidStatus = null;
            if (candlepinConfig.getBoolean(ConfigProperties.AMQP_INTEGRATION_ENABLED)) {
                qpidStatus = qmf.getStatus();

                if (qpidStatus != QpidStatus.CONNECTED) {
                    qpidConnection.setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
                }
            }
            boolean dbConnected = isDbConnected();
            BrokerState brokerState;
            DbState dbState;

            if (qpidStatus != null) {
                switch (qpidStatus) {
                    case CONNECTED:
                        brokerState = BrokerState.UP;
                        break;
                    case DOWN:
                        brokerState = BrokerState.DOWN;
                        break;
                    case FLOW_STOPPED:
                        brokerState = BrokerState.FLOW_STOPPED;
                        break;
                    default:
                        throw new RuntimeException("Unknown Qpid status: " + qpidStatus);
                }
            }
            else {
                //AMQP integration is turned off
                brokerState = BrokerState.OFF;
            }

            if (dbConnected) {
                dbState = DbState.UP;
            }
            else {
                dbState = DbState.DOWN;
            }

            CandlepinModeChange modeChange  = modeManager.getLastCandlepinModeChange();
            boolean qpidOk = (brokerState == BrokerState.UP || brokerState == BrokerState.OFF);
            boolean dbOk = dbState == DbState.UP;

            if (modeChange.getMode() == Mode.NORMAL) {
                if (!qpidOk || !dbOk) {
                    log.debug("Need to enter SUSPEND mode with Qpid status {} and DB status {}",
                        brokerState, dbState);
                    modeManager.enterMode(Mode.SUSPEND, brokerState, dbState);
                    cleanStatusCache();
                }
                else {
                    writeModeIfStateChanged(modeChange, brokerState, dbState);
                }
            }
            else if (modeChange.getMode() == Mode.SUSPEND) {
                if (qpidOk && dbOk) {
                    if (modeChange.getDbState() != DbState.UP) {
                        dataSource.hardReset();
                    }
                    failedAttempts = BigInteger.ZERO;
                    log.debug("DB and QPID are up, changing to NORMAL mode.");
                    modeManager.enterMode(Mode.NORMAL, brokerState, dbState);
                    cleanStatusCache();
                }
                else {
                    failedAttempts = failedAttempts.add(BigInteger.ONE);
                    log.debug("Staying in SUSPEND mode. So far {} failed attempts", failedAttempts);
                    writeModeIfStateChanged(modeChange, brokerState, dbState);
                }
            }
        }
        catch (Throwable t) {
            log.error("Error while executing period Suspend Transitioner Qpid check", t);
            /*
             * Nothing more we can do here, since this is scheduled thread. We must
             * hope that this error won't infinitely recur with each scheduled execution
             */
        }
    }

    private void writeModeIfStateChanged(CandlepinModeChange modeChange, BrokerState brokerState,
        DbState dbState) {
        if (modeManager.stateChanged(modeChange, brokerState, dbState)) {
            log.debug("DB or Qpid state changed, changing the CandlepinModeChange");
            modeManager.enterMode(modeChange.getMode(), brokerState, dbState);
            cleanStatusCache();
        }
        else {
            log.debug("DB or Qpid state did not change, Suspend mode transition check is no-op.");
        }
    }

    /**
     * Cleans Status Cache. We need to do this so that client's don't see
     * cached status response in case of a mode change.
     */
    private void cleanStatusCache() {
        candlepinCache.getStatusCache().clear();
    }
}
