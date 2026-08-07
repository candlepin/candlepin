/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.util;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Provider;
import javax.inject.Singleton;



/**
 * Provider for a universal executor service which will be gracefully shutdown along with Candlepin. The
 * ExecutorService provided will run each task in a virtual daemon thread, created for each task. Long-running
 * tasks may be interrupted during Candlepin shutdown, and should be designed to handle interruption.
 *
 * This class must be bound as a singleton or bound to a specific instance for the lifecycle management to
 * be function correctly.
 */
@Singleton
public class CandlepinExecutorServiceProvider implements Provider<ExecutorService> {
    private final ExecutorService executorService;

    public CandlepinExecutorServiceProvider() {
        this.executorService = Executors.newThreadPerTaskExecutor(runnable -> {
            Thread thread = Thread.ofVirtual().unstarted(runnable);
            thread.setDaemon(true);

            return thread;
        });
    }

    @Override
    public ExecutorService get() {
        return this.executorService;
    }

    /**
     * Immediately shuts down the ExecutorService backing this provider, returning any remaining tasks that
     * were awaiting execution.
     *
     * @return
     *  a list of tasks that never commenced execution
     */
    public List<Runnable> shutdown() {
        return this.executorService.shutdownNow();
    }

}



