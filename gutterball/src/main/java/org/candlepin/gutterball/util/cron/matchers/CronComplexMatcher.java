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

import java.util.HashSet;



/**
 * The CronComplexMatcher represents a unit with multiple specifications. It generates values
 * based on the set of matchers it contains, using non-wrapping matchers before those which
 * would wrap.
 *
 * For obvious reasons, a complex matcher may not contain itself.
 */
public class CronComplexMatcher extends HashSet<CronMatcher> implements CronMatcher {

    public boolean matches(int value) {
        for (CronMatcher matcher : this) {
            if (matcher.matches(value)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasNext(int now) {
        for (CronMatcher matcher : this) {
            if (matcher.hasNext(now)) {
                return true;
            }
        }

        return false;
    }

    public int next(int now) {
        int best = Integer.MAX_VALUE;
        boolean bwrap = true;

        for (CronMatcher matcher : this) {
            boolean vwrap = !matcher.hasNext(now);

            if (bwrap || !vwrap) {
                int value = matcher.next(now);

                if ((value < best) || (bwrap && !vwrap)) {
                    best = value;
                    bwrap = vwrap;
                }
            }
        }

        return best;
    }

}
