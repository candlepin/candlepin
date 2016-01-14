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

package org.candlepin.gutterball.util.cron.matchers;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.util.Arrays;
import java.util.List;



/**
 * CronStepMatcherTest
 */
@RunWith(JUnitParamsRunner.class)
public class CronStepMatcherTest {

    private CronMatcher matcher;

    @Before
    public void setup() {
        this.matcher = new CronStepMatcher(2, 1, 6);
    }

    @Test
    @Parameters(method = "parametersForTestMatches")
    public void testMatches(int input, boolean expected) {
        assertEquals(expected, this.matcher.matches(input));
    }

    public Object[] parametersForTestMatches() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 1, false },
            new Object[] { 2, true },
            new Object[] { 3, false },
            new Object[] { 4, true },
            new Object[] { 5, false },
            new Object[] { 6, true },
            new Object[] { 7, false },
            new Object[] { 8, false }
        );

        return params.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestHasNext")
    public void testHasNext(int input, boolean expected) {
        assertEquals(expected, this.matcher.hasNext(input));
    }

    public Object[] parametersForTestHasNext() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 1, true },
            new Object[] { 2, true },
            new Object[] { 3, true },
            new Object[] { 4, true },
            new Object[] { 5, true },
            new Object[] { 6, true },
            new Object[] { 7, false },
            new Object[] { 8, false }
        );

        return params.toArray();
    }

    @Test
    @Parameters(method = "parametersForTestNext")
    public void testNext(int step, int min, int max, int input, int expected) {
        CronMatcher matcher = new CronStepMatcher(step, min, max);
        assertEquals(expected, matcher.next(input));
    }

    public Object[] parametersForTestNext() {
        List<Object[]> params = Arrays.asList(
            new Object[] { 2, 1, 6, 0, 2 },
            new Object[] { 2, 1, 6, 1, 2 },
            new Object[] { 2, 1, 6, 2, 2 },
            new Object[] { 2, 1, 6, 3, 4 },
            new Object[] { 2, 1, 6, 4, 4 },
            new Object[] { 2, 1, 6, 5, 6 },
            new Object[] { 2, 1, 6, 6, 6 },
            new Object[] { 2, 1, 6, 7, 2 },
            new Object[] { 2, 1, 6, 8, 2 },

            new Object[] { 2, 0, 5, 0, 0 },
            new Object[] { 2, 0, 5, 1, 2 },
            new Object[] { 2, 0, 5, 2, 2 },
            new Object[] { 2, 0, 5, 3, 4 },
            new Object[] { 2, 0, 5, 4, 4 },
            new Object[] { 2, 0, 5, 5, 0 },
            new Object[] { 2, 0, 5, 6, 0 },
            new Object[] { 2, 0, 5, 7, 0 },

            new Object[] { 3, 5, 11, 0, 6 },
            new Object[] { 3, 5, 11, 1, 6 },
            new Object[] { 3, 5, 11, 2, 6 },
            new Object[] { 3, 5, 11, 3, 6 },
            new Object[] { 3, 5, 11, 4, 6 },
            new Object[] { 3, 5, 11, 5, 6 },
            new Object[] { 3, 5, 11, 6, 6 },
            new Object[] { 3, 5, 11, 7, 9 },
            new Object[] { 3, 5, 11, 8, 9 },
            new Object[] { 3, 5, 11, 9, 9 },
            new Object[] { 3, 5, 11, 10, 6 },
            new Object[] { 3, 5, 11, 11, 6 },
            new Object[] { 3, 5, 11, 12, 6 }
        );

        return params.toArray();
    }

}
