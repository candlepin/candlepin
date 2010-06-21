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

import java.util.HashMap;
import java.util.Map;

import org.quartz.Job;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * HighlanderJobFactory is a custom Quartz JobFactory implementation which
 * insures that only one instance of a job class is ever instantiated.
 * @version $Rev$
 */
public class HighlanderJobFactory implements JobFactory {

    private Map<String, Job> jobImplCache = new HashMap<String, Job>();
    private Injector injector;
    
    @Inject
    public HighlanderJobFactory(Injector injector) {
        this.injector = injector;
    }
    
    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public synchronized Job newJob(TriggerFiredBundle trigger)
        throws SchedulerException {

        Class jobClass = trigger.getJobDetail().getJobClass();
        Job retval = jobImplCache.get(jobClass.getName());
        if (retval == null) {
            try {
                retval = (Job) injector.getInstance(jobClass);
                injector.injectMembers(retval);
                jobImplCache.put(jobClass.getName(), retval);
            }
            catch (Exception e) {
                throw new SchedulerException(e);
            }
        }
        return retval;
    }
}
