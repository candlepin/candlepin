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

import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidConnection.STATUS;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidQmf.QpidStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;
import org.candlepin.model.CandlepinModeChange.Reason;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * When failedAttempts = 1, then the delay will be 15
     * When failedAttempts = 2, then the delay will be 45
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
    private int failedAttempts = 0;
    /**
     * Single threaded periodic task.
     */
    private ScheduledExecutorService execService;
    private ModeManager modeManager;
    private QpidQmf qmf;
    private QpidConnection qpidConnection;

    @Inject
    public SuspendModeTransitioner(Configuration config, ScheduledExecutorService execService) {
        this.execService = execService;
        delayGrowth = config.getInt(ConfigProperties.QPID_MODE_TANSITIONER_DELAY_GROWTH);
        initialDelay = config.getInt(ConfigProperties.QPID_MODE_TRANSITIONER_INITIAL_DELAY);
        maxDelay = config.getInt(ConfigProperties.QPID_MODE_TRANSITIONER_MAX_DELAY);
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
     *  delay = initialDelay * (delayGrowth ^ failedAttempts)
     *
     *  Maximal delay is configurable, -1 means there is no bound
     *
     *  When delayGrowth is set to 1, the delay wont grow.
     */
    private void schedule() {
        int delay = initialDelay * (int) Math.pow(delayGrowth, failedAttempts);

        if (maxDelay != -1 && delay > maxDelay) {
            log.debug("Maximum delay {} reached", maxDelay);
            delay = maxDelay;
        }

        log.debug("Next Transitioner check will run after {} seconds", delay);
        if (failedAttempts > 3) {
            log.info("SuspendModeTransitioner failed to reconnect to the Qpid Broker " +
                "{} times, backing off with next reconnect {} seconds", failedAttempts,
                delay);
        }
        execService.schedule(this, delay, TimeUnit.SECONDS);
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
     * Attempts to transition Candlepin according to current Mode and current status of
     * the Qpid Broker. Logs and swallows possible exceptions - theoretically
     * there should be none.
     *
     * Most of the time the transition won't be required and this method will be no-op.
     * There is an edge-case when transitioning from SUSPEND to NORMAL mode.
     * During that transition, there is a small time window between checking the
     * Qpid status and attempt to reconnect. If the Qpid status is reported as
     * Qpid up, the transitioner will try to reconnect to the broker. This reconnect
     * may fail. In that case the transition to NORMAL mode shouldn't go through
     */
    public synchronized void transitionAppropriately() {
        log.debug("Attempting to transition to appropriate Mode");
        try {
            QpidStatus status = qmf.getStatus();
            CandlepinModeChange modeChange = modeManager.getLastCandlepinModeChange();

            log.debug("Qpid status is {}, the current mode is {}", status, modeChange);

            if (status != QpidStatus.CONNECTED) {
                qpidConnection.setConnectionStatus(STATUS.JMS_OBJECTS_STALE);
            }

            if (modeChange.getMode() == Mode.SUSPEND) {
                switch (status) {
                    case CONNECTED:
                        log.info("Connection to qpid is restored! Reconnecting Qpid and" +
                            " Entering NORMAL mode");
                        failedAttempts = 0;
                        modeManager.enterMode(Mode.NORMAL, Reason.QPID_UP);
                        break;
                    case FLOW_STOPPED:
                    case DOWN:
                        failedAttempts++;
                        log.debug("Staying in {} mode. So far {} failed attempts",
                            status,
                            failedAttempts);
                        break;
                    default:
                        throw new RuntimeException("Unknown status: " + status);
                }
            }
            else if (modeChange.getMode() == Mode.NORMAL) {
                switch (status) {
                    case FLOW_STOPPED:
                        log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                            "the Qpid connection is flow stopped");
                        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_FLOW_STOPPED);
                        break;
                    case DOWN:
                        log.debug("Will need to transition Candlepin into SUSPEND Mode because " +
                            "the Qpid connection is down");
                        modeManager.enterMode(Mode.SUSPEND, Reason.QPID_DOWN);
                        break;
                    case CONNECTED:
                        log.debug("Connection to Qpid is ok and current mode is NORMAL. No-op!");
                        break;
                    default:
                        throw new RuntimeException("Unknown status: " + status);
                }
            }
        }
        catch (Throwable t) {
            log.error("Error while executing period Suspend Watchdog check", t);
            /**
             * Nothing more we can do here, since this is scheduled thread. We must
             * hope that this error won't infinitely recur with each scheduled execution
             */
        }
    }
}
