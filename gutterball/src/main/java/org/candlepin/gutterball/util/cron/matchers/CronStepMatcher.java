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



/**
 * The CronStepMatcher represents the step or divisible specification. It will generate values in
 * steps within its range.
 */
public class CronStepMatcher implements CronMatcher {
    private int step;
    private int min;
    private int max;
    private boolean wrapped;

    public CronStepMatcher(int step, int min, int max) {
        this.step = step;
        this.min = min;
        this.max = max;
    }

    public boolean matches(int value) {
        return (value >= this.min && value <= this.max && value % this.step == 0);
    }

    public boolean hasNext(int now) {
        if (now < this.min) {
            return this.hasNext(this.min);
        }
        else {
            int mod = (now % this.step);
            int next = (mod > 0 ? (now + this.step - mod) : now);

            return next <= this.max;
        }
    }

    public int next(int now) {
        if (now < this.min) {
            return this.next(this.min);
        }
        else {
            int mod = (now % this.step);
            int next = (mod > 0 ? (now + this.step - mod) : now);

            return (next <= this.max ? next : this.next(this.min));
        }
    }
}
