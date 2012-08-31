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
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;

/**
 * GuiceJobFactory is a custom Quartz JobFactory implementation which
 * delegates job creation to the Guice injector.
 * @version $Rev$
 */
public class GuiceJobFactory implements JobFactory {

    private Injector injector;
    private CandlepinSingletonScope candlepinSingletonScope;

    @Inject
    public GuiceJobFactory(Injector injector, CandlepinSingletonScope singletonScope) {
        this.injector = injector;
        this.candlepinSingletonScope = singletonScope;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler)
        throws SchedulerException {
        Class<Job> jobClass = (Class<Job>) bundle.getJobDetail().getJobClass();

        candlepinSingletonScope.enter();
        try {
            return new TransactionalPinsetterJob(injector.getInstance(jobClass),
                injector.getInstance(UnitOfWork.class));
        }
        finally {
            candlepinSingletonScope.exit();
        }
    }
}
