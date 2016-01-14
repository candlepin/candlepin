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
 * The CronStaticValueMatcher represents the single-value specification. It will always generate
 * a single value and will wrap when the value given is greater than the static value.
 */
public class CronStaticValueMatcher implements CronMatcher {
    private int value;

    public CronStaticValueMatcher(int value) {
        this.value = value;
    }

    public boolean matches(int value) {
        return this.value == value;
    }

    public boolean hasNext(int now) {
        return (now <= this.value);
    }

    public int next(int now) {
        return this.value;
    }
}
