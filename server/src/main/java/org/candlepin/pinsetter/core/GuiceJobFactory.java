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

import org.candlepin.guice.CandlepinRequestScope;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GuiceJobFactory is a custom Quartz JobFactory implementation which
 * delegates job creation to the Guice injector.
 * @version $Rev$
 */
public class GuiceJobFactory implements JobFactory {

    private static Logger log = LoggerFactory.getLogger(GuiceJobFactory.class);
    private Injector injector;
    private CandlepinRequestScope candlepinRequestScope;
    private UnitOfWork unitOfWork;

    @Inject
    public GuiceJobFactory(Injector injector, CandlepinRequestScope requestScope,
        UnitOfWork unitOfWork) {
        this.injector = injector;
        this.candlepinRequestScope = requestScope;
        this.unitOfWork = unitOfWork;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler)
        throws SchedulerException {
        Class<Job> jobClass = (Class<Job>) bundle.getJobDetail().getJobClass();

        boolean startedUow = startUnitOfWork();
        candlepinRequestScope.enter();
        try {
            return injector.getInstance(jobClass);
        }
        finally {
            candlepinRequestScope.exit();
            if (startedUow) {
                endUnitOfWork();
            }
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
