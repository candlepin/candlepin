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
 * The CronMatcher interface defines the methods necessary for a matcher to properly generate
 * values relative to a given time and determine whether or not a value occurs within its range.
 *
 * Matchers will always have a value to return from a call to the next method, as they will wrap
 * as necessary. Callers can determine whether or not a matcher is about to wrap by checking if
 * it has more values with a call to the hasNext method.
 */
public interface CronMatcher {
    /**
     * Checks whether or not the specified value matches this matcher.
     *
     * @param value
     *  The value to check against this matcher
     *
     * @return
     *  true if the value matches this matcher; false otherwise
     */
    boolean matches(int value);

    /**
     * Checks whether or not this matcher has more values greater than or equal to the specified
     * value. The matcher will not wrap when determining if it has more values.
     *
     * @param now
     *  The value to use to determine whether or not this matcher has more values
     *
     * @return
     *  true if this matcher has more values either greater than or equal to the specified value;
     *  false otherwise
     */
    boolean hasNext(int now);

    /**
     * Retrieves the next value from this matcher range, relative to the specified value, wrapping
     * as necessary. Callers can use the hasNext() method to determine whether or not the matcher
     * will wrap when generating a value.
     *
     * @param now
     *  The value to use to determine where this matcher should begin generating values
     *
     * @return
     *  the next value from this matcher's range
     */
    int next(int now);
}
