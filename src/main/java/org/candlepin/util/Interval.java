/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.util;

import java.time.Instant;
import java.time.temporal.TemporalUnit;




/**
 * Utility class representing an interval on the timeline. Intervals are immutable, and any method
 * which would result in a change to the interval returns a modified copy of the original interval.
 */
public class Interval {

    private final Instant start;
    private final Instant end;


    public Interval(Instant start, Instant end) {
        if (start == null) {
            throw new IllegalArgumentException("start is null");
        }

        if (end == null) {
            throw new IllegalArgumentException("end is null");
        }

        // this may be unnecessary
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start occurs after end");
        }

        this.start = start;
        this.end = end;
    }

    /**
     * Builds an interval from the given start and endpoints. Functionally identical to the matching
     * constructor; provided for consistency with the classes in the java.time package.
     */
    public static Interval of(Instant start, Instant end) {
        return new Interval(start, end);
    }

    /**
     * Builds an interval from the given start and endpoints. Functionally identical to the matching
     * constructor; provided for consistency with the classes in the java.time package.
     */
    public static Interval between(Instant start, Instant end) {
        return new Interval(start, end);
    }

    /**
     * Builds an interval from the given start time and ending a given number of units later.
     *
     * @param start
     *  the starting time for this interval
     *
     * @param amount
     *  the amount of units to add to the starting time to calculate the end time
     *
     * @param unit
     *  the temporal unit to add to the starting time
     *
     * @throws IllegalArgumentException
     *  if the either the starting time or temporal unit is null
     *
     * @return
     *  an interval built from the given inputs
     */
    public static Interval from(Instant start, long amount, TemporalUnit unit) {
        if (start == null) {
            throw new IllegalArgumentException("start is null");
        }

        if (unit == null) {
            throw new IllegalArgumentException("unit is null");
        }

        return new Interval(start, start.plus(amount, unit));
    }


    // TODO: Add methods to move the start/end points about as necessary


    public Instant getStart() {
        return this.start;
    }

    public Instant getEnd() {
        return this.end;
    }





}
