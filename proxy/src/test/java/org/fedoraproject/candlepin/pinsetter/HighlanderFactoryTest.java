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
package org.fedoraproject.candlepin.pinsetter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fedoraproject.candlepin.pinsetter.core.HighlanderJobFactory;

import org.junit.Test;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.spi.TriggerFiredBundle;

import java.text.ParseException;

/**
 * HighlanderFactoryTest
 * @version $Rev$
 */
public class HighlanderFactoryTest {

    @Test
    public void testNewJob() throws SchedulerException, ParseException {
        HighlanderJobFactory hf = new HighlanderJobFactory();
        assertNotNull(hf);
        try {
            hf.newJob(null);
            fail("should've died with npe");
        }
        catch (NullPointerException npe) {
            assertTrue(true);
        }

        String crontab = "0 0 12 * * ?";
        JobDetail jd = new JobDetail("testjob", "group", TestJob.class);
        Trigger trigger = new CronTrigger("testjob", "group", crontab);

        TriggerFiredBundle tfb = new TriggerFiredBundle(jd, trigger, null, false, null, null, null, null);
        Job j = hf.newJob(tfb);
        assertNotNull(j);
        assertEquals(TestJob.class, j.getClass());
    }
}
