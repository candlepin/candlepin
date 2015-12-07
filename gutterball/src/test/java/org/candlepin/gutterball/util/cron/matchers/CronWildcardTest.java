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

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;



/**
 * CronWildcardTest
 */
@RunWith(JUnitParamsRunner.class)
public class CronWildcardTest {

    @Test
    public void testMatches() {
        CronWildcard matcher = new CronWildcard();

        for (int i = 0; i < 1000; ++i) {
            // It should match everything thrown at it. Even stuff that's out of its range.
            assertTrue(matcher.matches(i));
        }
    }

    @Test
    public void testHasNext() {
        CronWildcard matcher = new CronWildcard();

        for (int i = 0; i < 1000; ++i) {
            // It should always appear to have additional elements, even when we're out of range.
            assertTrue(matcher.hasNext(i));
        }
    }

    @Test
    public void testNext() {
        CronWildcard matcher = new CronWildcard();

        for (int i = 0; i < 1000; ++i) {
            // We should be just returning our input, always.
            assertEquals(i, matcher.next(i));
        }
    }

}
