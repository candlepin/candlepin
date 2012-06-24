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
package org.candlepin.pinsetter.tasks;

import org.candlepin.model.StatisticCurator;

import com.google.inject.Inject;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * StatisticHistoryTask.
 */
public class StatisticHistoryTask implements Job {

    public static final String DEFAULT_SCHEDULE = "0 0 1 * * ?"; // run every
                                                                 // day at 1 AM

    private StatisticCurator statCurator;

    private static Logger log = Logger.getLogger(StatisticHistoryTask.class);

    /**
     * Instantiates a new statistic generation.
     *
     * @param statCurator the StatisticCurator
     */
    @Inject
    public StatisticHistoryTask(StatisticCurator statCurator) {
        this.statCurator = statCurator;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("Executing Statistic History Job.");

        try {
            statCurator.executeStatisticRun();
        }
        catch (HibernateException e) {
            log.error("Cannot store: ", e);
            throw new JobExecutionException(e);
        }
    }
}
