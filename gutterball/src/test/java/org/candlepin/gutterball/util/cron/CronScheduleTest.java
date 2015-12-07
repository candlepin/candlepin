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

package org.candlepin.gutterball.util.cron;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;



/**
 * CronScheduleTest
 */
@RunWith(JUnitParamsRunner.class)
public class CronScheduleTest {

    @Test
    @Parameters(method = "parametersForValidScheduleParsing")
    public void testValidScheduleParsing(String minute, String hour, String dayOfMonth,
        String month, String dayOfWeek) {

        new CronSchedule(String.format("%s %s %s %s %s", minute, hour, dayOfMonth, month, dayOfWeek));
    }

    public Object[] parametersForValidScheduleParsing() {
        List<Object[]> params = new LinkedList<Object[]>();
        List<String> defs = Arrays.asList(
            "*",
            "*/2",
            "5",
            "3-5",
            "*/2,5,3-5",
            "1,2,*,4,5"
        );

        // Uncomment this block for absolutely insane coverage that's probably never going to be
        // needed. Also, this will take 15 minutes at minimum.
        // for (String e1 : defs) {
        //     for (String e2 : defs) {
        //         for (String e3 : defs) {
        //             for (String e4 : defs) {
        //                 for (String e5 : defs) {
        //                     params.add(new Object[] { e1, e2, e3, e4, e5 });
        //                 }
        //             }
        //         }
        //     }
        // }

        for (int i = 0; i < 5; ++i) {
            for (String def : defs) {
                Object[] row = new Object[] { "1", "2", "3", "4", "5" };
                row[i] = def;

                params.add(row);
            }
        }

        return params.toArray();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "parametersForInvalidScheduleParsing")
    public void testInvalidScheduleParsing(String minute, String hour, String dayOfMonth,
        String month, String dayOfWeek) {

        new CronSchedule(String.format("%s %s %s %s %s", minute, hour, dayOfMonth, month, dayOfWeek));
    }

    public Object[] parametersForInvalidScheduleParsing() {
        List<Object[]> params = new LinkedList<Object[]>();
        List<String> defs = Arrays.asList(
            "-1",
            "*/-2",
            "*/",
            "5-3",
            "-1-2",
            "1--2",
            "1-bacon",
            "ham-10",
            "sausage",
            "1,2,three",
            "1,-2,3",
            "1,*/,",
            "5,",
            "",
            " "
        );

        for (int i = 0; i < 5; ++i) {
            for (String def : defs) {
                Object[] row = new Object[] { "1", "2", "3", "4", "5" };
                row[i] = def;

                params.add(row);
            }
        }

        return params.toArray();
    }


    @Test
    @Parameters(method = "parametersForNextOccurance")
    public void testGetNextOccurance(String schedule, String initial, String expected) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        Date initialDate = formatter.parse(initial);
        Date expectedDate = formatter.parse(expected);

        CronSchedule cron = new CronSchedule(schedule);
        Date actual = cron.getNextOccurance(initialDate);

        assertEquals(expectedDate, actual);
    }

    public Object[] parametersForNextOccurance() {
        List<Object[]> params = Arrays.asList(
            new Object[] { "0 3 * * *", "2015/09/23 00:00:00", "2015/9/23 03:00:00" },
            new Object[] { "0 3 * * *", "2015/09/23 12:00:00", "2015/09/24 03:00:00" },
            new Object[] { "15 15 * * *", "2016/01/05 00:00:00", "2016/01/05 15:15:00" },

            new Object[] { "15-30 15 * * *", "2016/01/05 15:00:00", "2016/01/05 15:15:00" },
            new Object[] { "15-30 15 * * *", "2016/01/05 15:25:00", "2016/01/05 15:25:00" },
            new Object[] { "15-30 15 * * *", "2016/01/05 15:31:00", "2016/01/06 15:15:00" },

            new Object[] { "15,30 15 * * *", "2016/01/05 15:00:00", "2016/01/05 15:15:00" },
            new Object[] { "15,30 15 * * *", "2016/01/05 15:25:00", "2016/01/05 15:30:00" },
            new Object[] { "15,30 15 * * *", "2016/01/05 15:31:00", "2016/01/06 15:15:00" },

            new Object[] { "*/10 5 * * *", "2016/01/01 00:00:00", "2016/01/01 05:00:00" },
            new Object[] { "*/10 5 * * *", "2016/01/01 05:01:00", "2016/01/01 05:10:00" },
            new Object[] { "*/10 5 * * *", "2016/01/01 05:51:00", "2016/01/02 05:00:00" },

            new Object[] { "1 2 3 4 *", "2016/01/01 00:00:00", "2016/04/03 02:01:00" },
            new Object[] { "1 2 3 4 5", "2016/01/01 00:00:00", "2020/04/03 02:01:00" },
            new Object[] { "1 2 3 3-5 5", "2016/01/01 00:00:00", "2017/03/03 02:01:00" },
            new Object[] { "1 2 3,4 7 1-5", "2016/01/01 00:00:00", "2016/07/04 02:01:00" }
        );

        return params.toArray();
    }

    @Test(expected = RuntimeException.class)
    @Parameters(method = "parametersForDistantSchedules")
    public void testImpossiblyDistantSchedules(String schedule) {
        CronSchedule cron = new CronSchedule(schedule);
        Date actual = cron.getNextOccurance();

        // We shouldn't get here.
    }

    public Object[] parametersForDistantSchedules() {
        List<Object[]> params = Arrays.<Object[]>asList(
            new Object[] { new Object[] { "* * 31 2 *" } }
        );

        return params.toArray();
    }
}
