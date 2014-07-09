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

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobExecutionException;

/**
 * StatisticHistoryTaskTest
 */

public class StatisticHistoryTaskTest extends DatabaseTestFixture {
    private StatisticHistoryTask task;

    @Before
    public void init() {
        super.init();
        this.task = new StatisticHistoryTask(statisticCurator);
    }

    @Test
    public void executeTest() throws JobExecutionException {
        this.beginTransaction();
        task.execute(null);
        this.commitTransaction();
    }

}
