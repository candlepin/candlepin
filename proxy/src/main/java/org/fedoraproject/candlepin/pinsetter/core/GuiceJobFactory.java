/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.pinsetter.core;

import org.quartz.Job;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.wideplay.warp.persist.WorkManager;

/**
 * GuiceJobFactory is a custom Quartz JobFactory implementation which
 * delegates job creation to the Guice injector.
 * @version $Rev$
 */
public class GuiceJobFactory implements JobFactory {
    private Injector injector;
    
    @Inject
    public GuiceJobFactory(Injector injector) {
        this.injector = injector;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Job newJob(TriggerFiredBundle trigger) throws SchedulerException {
        Class<Job> jobClass = trigger.getJobDetail().getJobClass();
        return new TransactionalPinsetterJob(injector.getInstance(jobClass),
            injector.getInstance(WorkManager.class));
    }
}
