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
package org.candlepin.pinsetter.core;

import org.candlepin.guice.CandlepinSingletonScope;
import org.candlepin.guice.SimpleScope;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

/**
 * GuiceJobFactory is a custom Quartz JobFactory implementation which
 * delegates job creation to the Guice injector.
 *
 * Courtesy https://github.com/brunojcm/guartz
 */
public class GuiceJobFactory implements JobFactory {

    private static Logger log = LoggerFactory.getLogger(GuiceJobFactory.class);
    private Injector injector;
    private CandlepinSingletonScope candlepinSingletonScope;
    private UnitOfWork unitOfWork;
    @Inject @Named("PinsetterJobScope") private SimpleScope pinsetterJobScope;

    @Inject
    public GuiceJobFactory(Injector injector, CandlepinSingletonScope singletonScope,
        UnitOfWork unitOfWork) {
        this.injector = injector;
        this.candlepinSingletonScope = singletonScope;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler)
        throws SchedulerException {
        final Class<? extends Job> jobClass = bundle.getJobDetail().getJobClass();
        Job decorator = new JobScopeDecorator(jobClass);
        return decorator;
    }

    private final class JobScopeDecorator implements Job {
        private final Class<? extends Job> decoratedJobClass;
        protected Job decorated;

        private JobScopeDecorator(Class<? extends Job> jobClass) {
            this.decoratedJobClass = jobClass;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {

            // Enter custom scopes and start a unit of work just for the injection done
            // when creating the job:
            pinsetterJobScope.enter();
            candlepinSingletonScope.enter();
            boolean startedUow = startUnitOfWork();
            try {
                decorated = injector.getInstance(decoratedJobClass);
            }
            finally {
                candlepinSingletonScope.exit();
                if (startedUow) {
                    endUnitOfWork();
                }
            }

            try {
                decorated.execute(context);
            }
            finally {
                pinsetterJobScope.exit();
            }
        }

        protected boolean startUnitOfWork() {
            if (unitOfWork != null) {
                try {
                    unitOfWork.begin();
                    return true;
                }
                catch (IllegalStateException e) {
                    log.debug("Already have an open unit of work");
                    return false;
                }
            }
            return false;
        }

        protected void endUnitOfWork() {
            if (unitOfWork != null) {
                try {
                    unitOfWork.end();
                }
                catch (IllegalStateException e) {
                    log.debug("Unit of work is already closed, doing nothing");
                    // If there is no active unit of work, there is no reason to close it
                }
            }
        }
    }
}
